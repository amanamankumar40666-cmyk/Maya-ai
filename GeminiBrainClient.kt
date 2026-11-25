package com.maya.assistant.ai

import com.maya.assistant.BuildConfig
import com.maya.assistant.data.MayaSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolCallRequest(
    val name: String,
    val args: JSONObject
)

data class GeminiTurnResult(
    val replyText: String?,
    val toolCalls: List<ToolCallRequest>,
    val rawModelContent: JSONObject?
)

class GeminiBrainClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val modelName = "gemini-2.5-flash"

    suspend fun sendTurn(
        contents: JSONArray,
        settings: MayaSettingsState
    ): Result<GeminiTurnResult> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = BuildConfig.GEMINI_API_KEY
            require(apiKey.isNotBlank()) {
                "GEMINI_API_KEY missing in local.properties"
            }

            val bodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", buildSystemPrompt(settings))))
                })
                put("contents", contents)
                put("tools", JSONArray().put(JSONObject().put("functionDeclarations", toolDeclarations())))
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Gemini API HTTP ${response.code}: $raw")
                }

                val json = JSONObject(raw)
                val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                val modelContent = candidate?.optJSONObject("content")
                val parts = modelContent?.optJSONArray("parts") ?: JSONArray()

                val textBuilder = StringBuilder()
                val calls = mutableListOf<ToolCallRequest>()

                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                    if (part.has("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        calls.add(
                            ToolCallRequest(
                                name = fc.getString("name"),
                                args = fc.optJSONObject("args") ?: JSONObject()
                            )
                        )
                    }
                }

                GeminiTurnResult(
                    replyText = textBuilder.toString().trim().ifEmpty { null },
                    toolCalls = calls,
                    rawModelContent = modelContent
                )
            }
        }
    }

    private fun buildSystemPrompt(settings: MayaSettingsState): String {
        val memoryBlock = if (settings.memories.isEmpty()) {
            "None saved yet."
        } else {
            settings.memories.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        }
        return """
            You are MAYA, a futuristic personal Android voice assistant inspired by JARVIS with a warm, intelligent female persona.
            Address the user as "${settings.preferredName}".
            Understand Hindi, English, and Hinglish naturally. Reply in the same language style the user speaks.
            Keep voice responses concise (1-3 sentences) unless answering a detailed question.
            NEVER claim an action succeeded if a tool returns success=false or permission_denied.
            For phone calls (prepare_call) and sending messages (prepare_message), ALWAYS ask the user for confirmation before executing.
            Language preference: ${settings.languageMode}
            User-approved memories:
            $memoryBlock
        """.trimIndent()
    }

    private fun toolDeclarations(): JSONArray = JSONArray().apply {
        put(fn("open_app", "Open an app on Android (YouTube, WhatsApp, Instagram, Free Fire MAX, Settings, Camera, Spotify).",
            mapOf("app_name" to prop("STRING", "Name of the app")), listOf("app_name")))
        put(fn("adjust_volume", "Adjust media, ring, or alarm volume.",
            mapOf(
                "stream" to prop("STRING", "media, ring, or alarm"),
                "direction" to prop("STRING", "up, down, mute, or max"),
                "level_percent" to prop("INTEGER", "Optional percentage 0-100")
            ), listOf("stream")))
        put(fn("toggle_flashlight", "Turn phone flashlight on or off.",
            mapOf("enabled" to prop("BOOLEAN", "True for on, false for off")), listOf("enabled")))
        put(fn("set_alarm", "Set an alarm on the device clock.",
            mapOf(
                "hour" to prop("INTEGER", "24-hour value 0-23"),
                "minute" to prop("INTEGER", "Minute 0-59"),
                "label" to prop("STRING", "Alarm label")
            ), listOf("hour", "minute")))
        put(fn("read_notifications", "Read active notifications if notification permission is granted.",
            emptyMap(), emptyList()))
        put(fn("prepare_call", "Prepare a phone call and ask user for confirmation.",
            mapOf("contact_name" to prop("STRING", "Contact name or phone number")), listOf("contact_name")))
        put(fn("prepare_message", "Prepare an SMS or WhatsApp message and ask user for confirmation.",
            mapOf(
                "recipient" to prop("STRING", "Contact name or phone number"),
                "platform" to prop("STRING", "whatsapp or sms"),
                "message_text" to prop("STRING", "Message text")
            ), listOf("recipient", "platform", "message_text")))
        put(fn("control_music", "Control active music playback.",
            mapOf("action" to prop("STRING", "play, pause, next, or previous")), listOf("action")))
        put(fn("save_preference", "Remember a user-approved preference or fact.",
            mapOf(
                "key" to prop("STRING", "Preference key"),
                "value" to prop("STRING", "Preference value")
            ), listOf("key", "value")))
    }

    private fun fn(name: String, desc: String, props: Map<String, JSONObject>, req: List<String>) = JSONObject().apply {
        put("name", name)
        put("description", desc)
        put("parameters", JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                props.forEach { (k, v) -> put(k, v) }
            })
            if (req.isNotEmpty()) put("required", JSONArray(req))
        })
    }

    private fun prop(type: String, desc: String) = JSONObject().apply {
        put("type", type)
        put("description", desc)
    }
}
