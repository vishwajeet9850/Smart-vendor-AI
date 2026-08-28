package com.smartvendor.ai.voice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.smartvendor.ai.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Transcribing : VoiceState()
    data class Recognized(val text: String, val isFinal: Boolean, val source: String = "local") : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class VoiceBillingManager(private val context: Context) {

    private val TAG = "VoiceBillingManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("smartvendor_voice_prefs", Context.MODE_PRIVATE)

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val recorderHelper = VoiceRecorderHelper(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var amplitudePollJob: Job? = null

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    var currentLanguageCode: String = "mr-IN" // 'mr-IN', 'hi-IN', 'en-IN'
    var useGroqCloud: Boolean
        get() = prefs.getBoolean("use_groq_whisper", true)
        set(value) = prefs.edit().putBoolean("use_groq_whisper", value).apply()

    var customGroqApiKey: String
        get() = prefs.getString("groq_api_key", "") ?: ""
        set(value) = prefs.edit().putString("groq_api_key", value).apply()

    init {
        initNativeSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initNativeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceState.Listening
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceState.value = VoiceState.Listening
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _audioRms.value = rmsdB.coerceIn(0f, 10f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please speak clearly."
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue for native voice recognition."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                            else -> "Voice recognition paused."
                        }
                        _voiceState.value = VoiceState.Error(message)
                        _audioRms.value = 0f
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _voiceState.value = VoiceState.Recognized(text, isFinal = true, source = "android_native")
                        } else {
                            _voiceState.value = VoiceState.Idle
                        }
                        _audioRms.value = 0f
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _voiceState.value = VoiceState.Recognized(text, isFinal = false, source = "android_native")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                applyTtsLanguage()
            }
        }
    }

    private fun applyTtsLanguage() {
        if (!isTtsInitialized) return
        val loc = when (currentLanguageCode) {
            "mr-IN" -> Locale("mr", "IN")
            "hi-IN" -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
        val result = textToSpeech?.setLanguage(loc)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech?.setLanguage(Locale("hi", "IN"))
        }
    }

    fun setLanguage(langCode: String) {
        currentLanguageCode = langCode
        applyTtsLanguage()
    }

    fun startListening() {
        if (useGroqCloud) {
            // Groq Whisper Recording Mode
            val started = recorderHelper.startRecording()
            if (started) {
                _voiceState.value = VoiceState.Listening
                startAmplitudePolling()
            } else {
                // Fallback to Native SpeechRecognizer if recorder fails
                startNativeListening()
            }
        } else {
            startNativeListening()
        }
    }

    private fun startNativeListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguageCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, currentLanguageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            _voiceState.value = VoiceState.Listening
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _voiceState.value = VoiceState.Error("Cannot start microphone: ${e.message}")
        }
    }

    fun stopListening() {
        if (useGroqCloud && recorderHelper.isRecording) {
            stopAmplitudePolling()
            val audioFile = recorderHelper.stopRecording()
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                transcribeWithGroq(audioFile)
            } else {
                _voiceState.value = VoiceState.Idle
            }
        } else {
            try {
                speechRecognizer?.stopListening()
                _audioRms.value = 0f
            } catch (_: Exception) {}
        }
    }

    private fun transcribeWithGroq(audioFile: File) {
        scope.launch(Dispatchers.IO) {
            _voiceState.value = VoiceState.Transcribing
            try {
                val reqFile = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", audioFile.name, reqFile)
                val langCode = currentLanguageCode.split("-")[0]
                val langBody = langCode.toRequestBody("text/plain".toMediaTypeOrNull())
                val apiKeyHeader = customGroqApiKey.ifBlank { null }

                val response = ApiClient.apiService.transcribeAudio(body, langBody, apiKeyHeader)
                if (response.isSuccessful && response.body()?.success == true) {
                    val transcript = response.body()?.transcript ?: ""
                    _voiceState.value = VoiceState.Recognized(transcript, isFinal = true, source = "groq_whisper")
                } else {
                    val errorMsg = response.body()?.error ?: "Groq transcription failed (${response.code()})"
                    Log.w(TAG, "Groq cloud error: $errorMsg. Falling back to native recognizer prompt.")
                    _voiceState.value = VoiceState.Error(errorMsg)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Network exception calling Groq Whisper", ex)
                _voiceState.value = VoiceState.Error("Server unreachable. Please ensure backend is running.")
            } finally {
                try { audioFile.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun startAmplitudePolling() {
        amplitudePollJob?.cancel()
        amplitudePollJob = scope.launch {
            while (isActive && recorderHelper.isRecording) {
                val amp = recorderHelper.getMaxAmplitude()
                _audioRms.value = (amp / 3276.7f).coerceIn(0f, 10f)
                delay(80)
            }
            _audioRms.value = 0f
        }
    }

    private fun stopAmplitudePolling() {
        amplitudePollJob?.cancel()
        amplitudePollJob = null
        _audioRms.value = 0f
    }

    fun speakFeedback(text: String) {
        if (!isTtsInitialized || text.isBlank()) return
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VoiceBillingFeedback")
    }

    fun destroy() {
        try {
            stopAmplitudePolling()
            if (recorderHelper.isRecording) {
                recorderHelper.stopRecording()
            }
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
    }
}
