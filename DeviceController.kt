package com.maya.assistant.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.maya.assistant.service.MayaNotificationListener

data class ActionResult(
    val success: Boolean,
    val message: String,
    val needsPermission: String? = null
)

class DeviceController(private val context: Context) {

    private val packageAliases = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "free fire max" to "com.dts.freefiremax",
        "free fire" to "com.dts.freefiremax",
        "spotify" to "com.spotify.music"
    )

    fun openApp(appName: String): ActionResult {
        val key = appName.trim().lowercase()
        if (key.contains("settings")) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ActionResult(true, "Opened Android Settings.")
        }

        val targetPackage = packageAliases.entries
            .firstOrNull { key.contains(it.key) }?.value

        if (targetPackage != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult(true, "Launched $appName.")
            }
            return ActionResult(false, "$appName ($targetPackage) is not installed on this device.")
        }

        return ActionResult(false, "Could not find an installed package matching $appName.")
    }

    fun toggleFlashlight(enabled: Boolean): ActionResult {
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPerm) {
            return ActionResult(false, "Camera permission is required for flashlight.", Manifest.permission.CAMERA)
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val backFlashId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ActionResult(false, "No flashlight hardware found on this device.")

        return runCatching {
            cameraManager.setTorchMode(backFlashId, enabled)
            ActionResult(true, "Flashlight turned ${if (enabled) "on" else "off"}.")
        }.getOrElse {
            ActionResult(false, "Failed to change flashlight state: ${it.localizedMessage}")
        }
    }

    fun adjustVolume(streamName: String, direction: String?, levelPercent: Int?): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (streamName.lowercase()) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }

        val maxVol = audioManager.getStreamMaxVolume(stream)
        val current = audioManager.getStreamVolume(stream)

        val target = when {
            levelPercent != null -> ((levelPercent.coerceIn(0, 100) / 100f) * maxVol).toInt()
            direction == "mute" -> 0
            direction == "max" -> maxVol
            direction == "down" -> (current - (maxVol / 5).coerceAtLeast(1)).coerceAtLeast(0)
            else -> (current + (maxVol / 5).coerceAtLeast(1)).coerceAtMost(maxVol)
        }

        audioManager.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
        val pct = ((target.toFloat() / maxVol.coerceAtLeast(1)) * 100).toInt()
        return ActionResult(true, "$streamName volume set to $pct%.")
    }

    fun setAlarm(hour: Int, minute: Int, label: String): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            ActionResult(true, "Alarm prepared for ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}.")
        }.getOrElse {
            ActionResult(false, "Could not launch Clock app to set alarm.")
        }
    }

    fun controlMusic(action: String): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (action.lowercase()) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY
        }
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return ActionResult(true, "Sent media command: $action.")
    }

    fun readNotifications(): ActionResult {
        val items = MayaNotificationListener.activeNotificationsSnapshot
        if (!MayaNotificationListener.isConnected) {
            return ActionResult(false, "Notification access permission is not enabled in Android Settings.")
        }
        if (items.isEmpty()) {
            return ActionResult(true, "You have no unread notifications right now.")
        }
        val summary = items.take(5).joinToString(". ") { "${it.appName} from ${it.title}: ${it.text}" }
        return ActionResult(true, summary)
    }

    fun resolvePhoneNumber(contactOrNumber: String): String? {
        if (contactOrNumber.any { it.isDigit() } && contactOrNumber.count { it.isDigit() } >= 6) {
            return contactOrNumber.filter { it.isDigit() || it == '+' }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$contactOrNumber%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(1)
            }
        }
        return null
    }

    fun executeConfirmedCall(phoneNumber: String): ActionResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult(false, "CALL_PHONE permission is not granted.", Manifest.permission.CALL_PHONE)
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult(true, "Dialing $phoneNumber.")
    }

    fun executeConfirmedMessage(recipient: String, platform: String, text: String): ActionResult {
        if (platform.lowercase() == "whatsapp") {
            val number = resolvePhoneNumber(recipient)?.replace("+", "")?.replace(" ", "")
            val uri = if (number != null) {
                Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(text)}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(text)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching {
                context.startActivity(intent)
                ActionResult(true, "Opened WhatsApp with your confirmed message draft.")
            }.getOrElse {
                ActionResult(false, "WhatsApp is not installed on this phone.")
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult(false, "SEND_SMS permission is not granted.", Manifest.permission.SEND_SMS)
        }
        val number = resolvePhoneNumber(recipient)
            ?: return ActionResult(false, "Could not find a phone number for $recipient.")
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(number, null, text, null, null)
        return ActionResult(true, "SMS sent to $recipient.")
    }
}
