package com.example.aigamerfriend.data

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioManager {
    companion object {
        private const val TAG = "AudioManager"
        private const val RECORD_SAMPLE_RATE = 16000
        private const val PLAYBACK_SAMPLE_RATE = 24000
        private const val RECORD_CHUNK_SIZE = 2048
        private const val PLAYBACK_BUFFER_MULTIPLIER = 4
    }

    var onAudioDataAvailable: ((ByteArray) -> Unit)? = null
    var isMuted: Boolean = false
    var onAudioLevelUpdate: ((Float) -> Unit)? = null
    var gainFactor: Float = 5.0f

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordJob: Job? = null
    private var scope: CoroutineScope? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        this.scope = scope
        startRecording(scope)
        startPlayback()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(scope: CoroutineScope) {
        val minBuffer = AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, RECORD_CHUNK_SIZE),
        )

        audioRecord?.let { record ->
            val sessionId = record.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    aec = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
                    Log.d(TAG, "AEC enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable AEC", e)
                }
            }
            if (AutomaticGainControl.isAvailable()) {
                try {
                    agc = AutomaticGainControl.create(sessionId)?.also { it.enabled = true }
                    Log.d(TAG, "AGC enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable AGC", e)
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
                    Log.d(TAG, "NoiseSuppressor enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable NoiseSuppressor", e)
                }
            }
        }

        audioRecord?.startRecording()

        recordJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(RECORD_CHUNK_SIZE)
            while (isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (bytesRead > 0) {
                    val data = amplifyAudio(buffer.copyOf(bytesRead))
                    // Calculate RMS audio level from PCM 16-bit samples
                    onAudioLevelUpdate?.let { callback ->
                        val level = calculateAudioLevel(data)
                        callback(level)
                    }
                    if (!isMuted) {
                        onAudioDataAvailable?.invoke(data)
                    }
                }
            }
        }
    }

    private fun startPlayback() {
        val minBuffer = AudioTrack.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(PLAYBACK_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * PLAYBACK_BUFFER_MULTIPLIER)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    private fun amplifyAudio(data: ByteArray): ByteArray {
        if (gainFactor == 1.0f) return data
        val result = ByteArray(data.size)
        for (i in 0 until data.size - 1 step 2) {
            val sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            val amplified = (sample * gainFactor).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            result[i] = (amplified.toInt() and 0xFF).toByte()
            result[i + 1] = (amplified.toInt() shr 8).toByte()
        }
        return result
    }

    private fun calculateAudioLevel(pcmData: ByteArray): Float {
        val sampleCount = pcmData.size / 2
        if (sampleCount == 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        val rms = kotlin.math.sqrt(sumSquares / sampleCount)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    fun playAudio(data: ByteArray) {
        try {
            audioTrack?.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            Log.w(TAG, "Error playing audio", e)
        }
    }

    fun shutdown() {
        recordJob?.cancel()
        recordJob = null

        aec?.release()
        aec = null
        agc?.release()
        agc = null
        noiseSuppressor?.release()
        noiseSuppressor = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord?.release()
        audioRecord = null

        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
        audioTrack?.release()
        audioTrack = null

        scope = null
    }
}
