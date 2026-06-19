package com.atvriders.wsprtxrx.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Plays rendered WSPR PCM through an [AudioTrack] in streaming mode, reporting progress
 * (0f..1f). Cancel the calling coroutine to stop playback early.
 */
class WsprPlayer {

    /**
     * Streams [pcm] at [sampleRate], invoking [onProgress] as it plays. Suspends until
     * the transmission completes or the coroutine is cancelled.
     */
    suspend fun play(
        pcm: ShortArray,
        sampleRate: Int = WsprAudio.SAMPLE_RATE,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.Default) {
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
        }
    }
}
