package com.maya.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class MayaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val voiceChannel = NotificationChannel(
            CHANNEL_VOICE_SERVICE,
            "MAYA Voice Listener",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Hey Maya wake-phrase listening active when enabled."
        }
        manager.createNotificationChannel(voiceChannel)
    }

    companion object {
        const val CHANNEL_VOICE_SERVICE = "maya_voice_service"
    }
}
