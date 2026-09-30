package app.memorygate.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {
    /** [GateAccessibilityService] がユーザー補助の設定で有効になっているか */
    fun isGateServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, GateAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
