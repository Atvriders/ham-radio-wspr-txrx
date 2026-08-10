package com.atvriders.wsprtxrx.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Thrown when the transmission must not proceed (or must not continue) because the app
 * does not exclusively own the audio output. Both cases are hard aborts rather than
 * best-effort continuations: WSPR is a 110.6 s timing-exact frame, so anything mixed
 * into or ducked out of it produces an undecodable emission on shared spectrum.
 */
class AudioFocusUnavailableException(message: String) : IllegalStateException(message)

/** Plays rendered WSPR PCM. Abstracted so the ViewModel is unit-testable off-device. */
interface TxAudioSink {
    /**
     * Streams [pcm], invoking [onProgress] (0f..1f) as it plays. Suspends until the
     * transmission completes or the coroutine is cancelled.
     */
    suspend fun play(pcm: ShortArray, onProgress: (Float) -> Unit = {})

    companion object {
        /** No-op sink; the ViewModel's default, so tests need no Android audio stack. */
        val NONE: TxAudioSink = object : TxAudioSink {
            override suspend fun play(pcm: ShortArray, onProgress: (Float) -> Unit) {
                onProgress(1f)
            }
        }
    }
}

/**
 * Plays rendered WSPR PCM through an [AudioTrack] in streaming mode, reporting progress
 * (0f..1f). Cancel the calling coroutine to stop playback early.
 *
 * Transient-exclusive audio focus is requested for the ~110.6 s transmission so other
 * apps' audio cannot mix into and corrupt the acoustically-coupled signal. Unlike the
 * previous best-effort behaviour, a denied request now **aborts**: from Android 15,
 * `requestAudioFocus` returns `AUDIOFOCUS_REQUEST_FAILED` unless the app is top or
 * running a foreground service, and `GAIN_TRANSIENT_EXCLUSIVE` is refused outright
 * during a call — precisely when nothing must be emitted. A focus-change listener aborts
 * an in-flight transmission for the same reason.
 */
class WsprPlayer(
    private val context: Context? = null,
    private val sampleRate: Int = WsprAudio.SAMPLE_RATE,
) : TxAudioSink {

    override suspend fun play(pcm: ShortArray, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Default) {
            val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            // Set from a binder thread by the focus listener, polled by the write loop.
            val focusLost = AtomicBoolean(false)
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                // Any negative change (loss, transient loss for a call, or a duck
                // request) corrupts the frame. There is no meaningful resume for WSPR.
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                ) {
                    focusLost.set(true)
                }
            }

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val focusRequest = audioManager?.let { requestFocus(it, attrs, listener) }
            if (audioManager != null && focusRequest == null) {
                throw AudioFocusUnavailableException(
                    "Audio focus denied — another app owns the audio output. Transmit aborted.",
                )
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                val chunk = 2048
                var offset = 0
                while (offset < pcm.size) {
                    coroutineContext.ensureActive()
                    if (focusLost.get()) {
                        throw AudioFocusUnavailableException(
                            "Audio focus lost mid-transmission — the WSPR frame is corrupt.",
                        )
                    }
                    val len = minOf(chunk, pcm.size - offset)
                    val written = track.write(pcm, offset, len)
                    if (written <= 0) break
                    offset += written
                    onProgress(offset.toFloat() / pcm.size)
                }
                onProgress(1f)
            } finally {
                runCatching { track.stop() }
                track.release()
                if (audioManager != null && focusRequest != null) {
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
                }
            }
        }

    /**
     * Requests transient-exclusive audio focus (minSdk 26 path). Returns the request to
     * abandon later, or null if focus was denied — in which case the caller aborts.
     */
    private fun requestFocus(
        am: AudioManager,
        attrs: AudioAttributes,
        listener: AudioManager.OnAudioFocusChangeListener,
    ): AudioFocusRequest? {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(listener)
            .build()
        val result = runCatching { am.requestAudioFocus(request) }.getOrNull()
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request else null
    }
}
