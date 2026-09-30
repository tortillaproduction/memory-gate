package app.memorygate.service

import android.content.Context
import android.os.PowerManager

/** オンボーディング・ホームで確認する端末の状態 */
data class SystemStatus(
    val accessibilityEnabled: Boolean,
    val ignoringBatteryOptimizations: Boolean,
) {
    companion object {
        fun read(context: Context): SystemStatus = SystemStatus(
            accessibilityEnabled = AccessibilityStatus.isGateServiceEnabled(context),
            ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
        )
    }
}
