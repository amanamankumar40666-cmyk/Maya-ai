package com.maya.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class MayaSpeechManager(
    private val context: Context,
    private val onWakePhraseDetected: () -> Unit,
    private val onCommandRecognized: (String) -> Unit,
    private val onSpeechFinished: () -> Unit,
    private val onError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeModeActive = false
    private var activeCommandMode = false

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale("en", "IN")
            selectFemaleVoice(Locale("en", "IN"))
            tts?.setSpeechRate(1.02f)
            tts?.setPitch(1.06f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onSpeechFinished()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeechFinished()
                }
            })
        }
    }

    private fun selectFemaleVoice(locale: Locale) {
        val voices = tts?.voices ?: return
        val preferred = voices.firstOrNull { v ->
            v.locale.language == locale.language &&
                (v.name.contains("female", ignoreCase = true) ||
                    v.name.contains("en-in-x-ene", ignoreCase = true) ||
                    v.name.contains("hi-in-x-hia", ignoreCase = true) ||
                    v.name.contains("sfg", ignoreCase = true))
        }
        if (preferred != null) {
            tts?.voice = preferred
        }
    }

    fun speak(text: String) {
        if (!ttsReady) {
            onSpeechFinished()
            return
        }
        val containsHindiScript = text.any { it in '\u0900'..'\u097F' }
        val targetLocale = if (containsHindiScript) Locale("hi", "IN") else Locale("en", "IN")
        tts?.language = targetLocale
        selectFemaleVoice(targetLocale)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maya_utt_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun startListening(wakeWordOnly: Boolean, languageTag: String = "en-IN") {
        wakeModeActive = wakeWordOnly
        activeCommandMode = !wakeWordOnly

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service is unavailable on this device.")
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        wakeModeActive = false
        activeCommandMode = false
        recognizer?.stopListening()
        _rmsLevel.value = 0f
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _rmsLevel.value = 0f
        }
        override fun onError(error: Int) {
            _rmsLevel.value = 0f
            if (wakeModeActive && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                startListening(wakeWordOnly = true)
            } else if (activeCommandMode) {
                onError("Could not hear clearly (code $error). Tap mic to try again.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!wakeModeActive) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val joined = matches.joinToString(" ").lowercase()
            if (joined.contains("hey maya") || joined.contains("हे माया") || joined.contains("hi maya")) {
                wakeModeActive = false
                onWakePhraseDetected()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = matches.firstOrNull()?.trim().orEmpty()
            if (text.isEmpty()) {
                if (wakeModeActive) startListening(wakeWordOnly = true)
                return
            }

            if (wakeModeActive) {
                val lower = text.lowercase()
                if (lower.contains("hey maya") || lower.contains("hi maya") || lower.contains("हे माया")) {
                    val stripped = lower
                        .replace("hey maya", "")
                        .replace("hi maya", "")
                        .replace("हे माया", "")
                        .trim()
                    if (stripped.length > 2) {
                        onCommandRecognized(stripped)
                    } else {
                        onWakePhraseDetected()
                    }
                } else {
                    startListening(wakeWordOnly = true)
                }
            } else {
                onCommandRecognized(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
