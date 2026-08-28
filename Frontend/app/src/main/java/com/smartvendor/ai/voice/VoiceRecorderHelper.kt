package com.smartvendor.ai.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorderHelper(private val context: Context) {

    private val TAG = "VoiceRecorderHelper"
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    var isRecording: Boolean = false
        private set

    fun startRecording(): Boolean {
        try {
            outputFile = File(context.cacheDir, "voice_order_${System.currentTimeMillis()}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            isRecording = false
            mediaRecorder?.release()
            mediaRecorder = null
            return false
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            null
        }
    }

    fun getMaxAmplitude(): Int {
        return if (isRecording) {
            try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (_: Exception) {
                0
            }
        } else 0
    }
}
