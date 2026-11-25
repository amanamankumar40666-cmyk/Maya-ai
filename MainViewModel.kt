package com.maya.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maya.assistant.ai.GeminiBrainClient
import com.maya.assistant.data.MayaPreferences
import com.maya.assistant.data.MayaSettingsState
import com.maya.assistant.device.ActionResult
import com.maya.assistant.device.DeviceController
import com.maya.assistant.voice.MayaSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class AssistantUiState {
    IDLE, WAKE_STANDBY, LISTENING, THINKING, SPEAKING, AWAITING_CONFIRMATION
}

data class ChatEntry(
    val isUser: Boolean,
    val text: String,
    val actionNote: String? = null
)

data class PendingConfirmation(
    val type: String, // "call" or "message"
    val recipient: String,
    val phoneNumber: String? = null,
    val platform: String = "whatsapp",
    val messageText: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = MayaPreferences(application)
    private val gemini = GeminiBrainClient()
    private val device = DeviceController(application)

    private val _uiState = MutableStateFlow(AssistantUiState.IDLE)
    val uiState: StateFlow<AssistantUiState> = _uiState

    private val _history = MutableStateFlow<List<ChatEntry>>(emptyList())
    val history: StateFlow<List<ChatEntry>> = _history

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation

    private val _settings = MutableStateFlow(MayaSettingsState())
    val settings: StateFlow<MayaSettingsState> = _settings

    private val conversationJson = JSONArray()

    val speechManager = MayaSpeechManager(
        context = application,
        onWakePhraseDetected = {
            viewModelScope.launch {
                _uiState.value = AssistantUiState.LISTENING
                speechManager.startListening(wakeWordOnly = false)
            }
        },
        onCommandRecognized = { command ->
            processUserUtterance(command)
        },
        onSpeechFinished = {
            viewModelScope.launch {
                val cfg = _settings.value
                if (_pendingConfirmation.value != null) {
                    _uiState.value = AssistantUiState.AWAITING_CONFIRMATION
                } else if (cfg.continuousMode) {
                    _uiState.value = AssistantUiState.LISTENING
                    speechManager.startListening(wakeWordOnly = false)
                } else if (cfg.wakeWordEnabled) {
                    _uiState.value = AssistantUiState.WAKE_STANDBY
                    speechManager.startListening(wakeWordOnly = true)
                } else {
                    _uiState.value = AssistantUiState.IDLE
                }
            }
        },
        onError = { msg ->
            _history.value = _history.value + ChatEntry(isUser = false, text = msg)
            _uiState.value = AssistantUiState.IDLE
        }
    )

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collect { _settings.value = it }
        }
    }

    fun toggleMic() {
        when (_uiState.value) {
            AssistantUiState.LISTENING -> {
                speechManager.stopListening()
                _uiState.value = AssistantUiState.IDLE
            }
            AssistantUiState.SPEAKING -> {
                speechManager.stopSpeaking()
                _uiState.value = AssistantUiState.LISTENING
                speechManager.startListening(wakeWordOnly = false)
            }
            else -> {
                _uiState.value = AssistantUiState.LISTENING
                speechManager.startListening(wakeWordOnly = false)
            }
        }
    }

    fun startWakeStandby() {
        _uiState.value = AssistantUiState.WAKE_STANDBY
        speechManager.startListening(wakeWordOnly = true)
    }

    fun processUserUtterance(text: String) {
        if (text.isBlank()) return
        _history.value = _history.value + ChatEntry(isUser = true, text = text)
        _uiState.value = AssistantUiState.THINKING

        viewModelScope.launch {
            val cfg = preferences.settingsFlow.first()
            conversationJson.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })

            val first = gemini.sendTurn(conversationJson, cfg)
            first.onFailure { err ->
                val errorReply = "Sorry ${cfg.preferredName}, I couldn't reach Gemini right now: ${err.localizedMessage}"
                _history.value = _history.value + ChatEntry(isUser = false, text = errorReply)
                _uiState.value = AssistantUiState.SPEAKING
                speechManager.speak(errorReply)
            }.onSuccess { turn ->
                if (turn.toolCalls.isEmpty()) {
                    val reply = turn.replyText ?: "How can I help?"
                    appendModelText(reply)
                    speakReply(reply)
                } else {
                    turn.rawModelContent?.let { conversationJson.put(it) }
                    val toolResponseParts = JSONArray()
                    val actionNotes = mutableListOf<String>()

                    for (call in turn.toolCalls) {
                        val exec = executeAndroidTool(call.name, call.args)
                        actionNotes.add(exec.message)
                        toolResponseParts.put(JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", call.name)
                                put("response", JSONObject().apply {
                                    put("success", exec.success)
                                    put("message", exec.message)
                                })
                            })
                        })
                    }

                    conversationJson.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", toolResponseParts)
                    })

                    val second = gemini.sendTurn(conversationJson, cfg)
                    val finalReply = second.getOrNull()?.replyText ?: actionNotes.joinToString(". ")
                    appendModelText(finalReply, actionNotes.joinToString(" · "))
                    speakReply(finalReply)
                }
            }
        }
    }

    private suspend fun executeAndroidTool(name: String, args: JSONObject): ActionResult {
        return when (name) {
            "open_app" -> device.openApp(args.optString("app_name"))
            "toggle_flashlight" -> device.toggleFlashlight(args.optBoolean("enabled", true))
            "adjust_volume" -> device.adjustVolume(
                args.optString("stream", "media"),
                args.optString("direction").ifEmpty { null },
                if (args.has("level_percent")) args.optInt("level_percent") else null
            )
            "set_alarm" -> device.setAlarm(
                args.optInt("hour", 7),
                args.optInt("minute", 0),
                args.optString("label", "MAYA Alarm")
            )
            "read_notifications" -> device.readNotifications()
            "control_music" -> device.controlMusic(args.optString("action", "play"))
            "save_preference" -> {
                val key = args.optString("key")
                val value = args.optString("value")
                if (key == "preferredName") preferences.setPreferredName(value)
                else preferences.saveMemory(key, value)
                ActionResult(true, "Saved preference $key.")
            }
            "prepare_call" -> {
                val contact = args.optString("contact_name")
                val number = device.resolvePhoneNumber(contact)
                _pendingConfirmation.value = PendingConfirmation(
                    type = "call",
                    recipient = contact,
                    phoneNumber = number
                )
                ActionResult(true, "Prepared call to $contact. Waiting for user confirmation.")
            }
            "prepare_message" -> {
                val recipient = args.optString("recipient")
                val platform = args.optString("platform", "whatsapp")
                val msg = args.optString("message_text")
                _pendingConfirmation.value = PendingConfirmation(
                    type = "message",
                    recipient = recipient,
                    platform = platform,
                    messageText = msg
                )
                ActionResult(true, "Drafted $platform message to $recipient. Waiting for user confirmation.")
            }
            else -> ActionResult(false, "Unsupported command: $name")
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        val result = if (pending.type == "call") {
            val num = pending.phoneNumber
            if (num != null) device.executeConfirmedCall(num)
            else ActionResult(false, "Could not find a phone number for ${pending.recipient}.")
        } else {
            device.executeConfirmedMessage(pending.recipient, pending.platform, pending.messageText)
        }
        appendModelText(result.message, if (result.success) "Confirmed & Executed" else "Failed")
        speakReply(result.message)
    }

    fun cancelPendingAction() {
        _pendingConfirmation.value = null
        val msg = "Okay, cancelled."
        appendModelText(msg)
        speakReply(msg)
    }

    private fun appendModelText(text: String, note: String? = null) {
        conversationJson.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text", text)))
        })
        _history.value = _history.value + ChatEntry(isUser = false, text = text, actionNote = note)
    }

    private fun speakReply(text: String) {
        _uiState.value = AssistantUiState.SPEAKING
        speechManager.speak(text)
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.release()
    }
}
