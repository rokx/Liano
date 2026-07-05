package com.rokx.liano

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Lightweight polyphonic sound source for the on-screen test keyboard. */
class TestPianoSound {
    private val tracks = mutableMapOf<Int, AudioTrack>()

    fun noteOn(noteNumber: Int) {
        val track = runCatching { tracks.getOrPut(noteNumber) { createTrack(noteNumber) } }
            .getOrNull()
            ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        }
    }

    fun noteOff(noteNumber: Int) {
        tracks[noteNumber]?.let { track ->
            runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
        }
    }

    fun release() {
        tracks.values.forEach { track ->
            runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
            track.release()
        }
        tracks.clear()
    }

    private fun createTrack(noteNumber: Int): AudioTrack {
        val samples = createPianoSamples(noteNumber)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        return AudioTrack(
            attributes,
            format,
            samples.size * Short.SIZE_BYTES,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also { track ->
                track.write(samples, 0, samples.size)
                track.setVolume(KEY_VOLUME)
            }
    }

    private fun createPianoSamples(noteNumber: Int): ShortArray {
        val frequency = A4_FREQUENCY * 2.0.pow((noteNumber - A4_NOTE) / 12.0)
        return ShortArray((SAMPLE_RATE * TONE_DURATION_SECONDS).toInt()) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val attack = (time / ATTACK_SECONDS).coerceAtMost(1.0)
            val decay = exp(-DECAY_RATE * time)
            val phase = 2.0 * PI * frequency * time
            val waveform = sin(phase) + 0.38 * sin(phase * 2.0) + 0.16 * sin(phase * 3.0)
            (waveform * attack * decay * Short.MAX_VALUE * SAMPLE_GAIN)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private companion object {
        private const val SAMPLE_RATE = 22_050
        private const val TONE_DURATION_SECONDS = 1.8
        private const val ATTACK_SECONDS = 0.008
        private const val DECAY_RATE = 2.2
        private const val SAMPLE_GAIN = 0.58
        private const val KEY_VOLUME = 0.55f
        private const val A4_NOTE = 69
        private const val A4_FREQUENCY = 440.0
    }
}
