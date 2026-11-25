package com.maya.assistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MayaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reacts only when user explicitly triggers an approved UI automation command
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun clickButtonByText(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(label)
        val clickable = nodes.firstOrNull { it.isClickable } ?: return false
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun navigateHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun navigateBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    companion object {
        @Volatile
        var instance: MayaAccessibilityService? = null
            private set
    }
}
