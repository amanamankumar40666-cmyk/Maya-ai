package com.maya.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "maya_settings")

data class MayaSettingsState(
    val preferredName: String = "Friend",
    val languageMode: String = "auto",
    val wakeWordEnabled: Boolean = true,
    val continuousMode: Boolean = true,
    val memories: Map<String, String> = emptyMap()
)

class MayaPreferences(private val context: Context) {

    private object Keys {
        val PREFERRED_NAME = stringPreferencesKey("preferred_name")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        val CONTINUOUS = booleanPreferencesKey("continuous_mode")
        val MEMORIES_JSON = stringPreferencesKey("memories_json")
    }

    val settingsFlow: Flow<MayaSettingsState> = context.dataStore.data.map { prefs ->
        val rawMem = prefs[Keys.MEMORIES_JSON] ?: "{}"
        val memMap = mutableMapOf<String, String>()
        runCatching {
            val obj = JSONObject(rawMem)
            obj.keys().forEach { k -> memMap[k] = obj.getString(k) }
        }
        MayaSettingsState(
            preferredName = prefs[Keys.PREFERRED_NAME] ?: "Friend",
            languageMode = prefs[Keys.LANGUAGE_MODE] ?: "auto",
            wakeWordEnabled = prefs[Keys.WAKE_WORD] ?: true,
            continuousMode = prefs[Keys.CONTINUOUS] ?: true,
            memories = memMap
        )
    }

    suspend fun setPreferredName(name: String) {
        context.dataStore.edit { it[Keys.PREFERRED_NAME] = name.trim().ifEmpty { "Friend" } }
    }

    suspend fun setLanguageMode(mode: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_MODE] = mode }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_WORD] = enabled }
    }

    suspend fun setContinuousMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONTINUOUS] = enabled }
    }

    suspend fun saveMemory(key: String, value: String) {
        context.dataStore.edit { prefs ->
            val obj = runCatching { JSONObject(prefs[Keys.MEMORIES_JSON] ?: "{}") }
                .getOrDefault(JSONObject())
            obj.put(key, value)
            prefs[Keys.MEMORIES_JSON] = obj.toString()
        }
    }

    suspend fun removeMemory(key: String) {
        context.dataStore.edit { prefs ->
            val obj = runCatching { JSONObject(prefs[Keys.MEMORIES_JSON] ?: "{}") }
                .getOrDefault(JSONObject())
            obj.remove(key)
            prefs[Keys.MEMORIES_JSON] = obj.toString()
        }
    }
}
