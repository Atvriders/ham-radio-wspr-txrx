package com.atvriders.wsprtxrx.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Plays rendered WSPR PCM through an [AudioTrack] in streaming mode, reporting progress
 * (0f..1f). Cancel the calling coroutine to stop playback early.
 *
 * If a [Context] is supplied, transient-exclusive audio focus is requested for the
 * ~110.6 s transmission so other apps' audio doesn't mix into and corrupt the
 * acoustically-coupled signal. Focus is abandoned in a `finally`; a denied request is
 * logged but does not abort playback.
 */
class WsprPlayer(private val context: Context? = null) {

    /**
     * Streams [pcm] at [sampleRate], invoking [onProgress] as it plays. Suspends until
     * the transmission completes or the coroutine is cancelled.
     */
    suspend fun play(
        pcm: ShortArray,
        sampleRate: Int = WsprAudio.SAMPLE_RATE,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.Default) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val focusRequest = audioManager?.let { requestFocus(it) }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf,
            AudioTrack.MODE_STREAM,
        )
        try {
            track.play()
            val chunk = 2048
            var offset = 0
            while (offset < pcm.size) {
                coroutineContext.ensureActive()
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
     * abandon later, or null if focus was denied — in which case we proceed anyway.
     */
    private fun requestFocus(am: AudioManager): AudioFocusRequest? {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .build()
        val result = runCatching { am.requestAudioFocus(request) }.getOrNull()
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            request
        } else {
            Log.w("WsprPlayer", "Audio focus not granted ($result); transmitting anyway")
            null
        }
    }
}
