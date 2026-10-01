package app.memorygate.gate

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType

/** 誘導先を開く（URL は Custom Tabs、アプリはランチャー Intent） */
class TargetLauncher(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    /** type=APP のとき、起動できるか（アンインストール済みなどでないか） */
    fun canLaunch(target: Target): Boolean = when (target.type) {
        TargetType.URL -> target.url != null
        TargetType.APP -> target.packageName?.let { packageManager.getLaunchIntentForPackage(it) } != null
    }

    /** 開けたら true */
    fun open(context: Context, target: Target): Boolean = when (target.type) {
        TargetType.URL -> target.url?.let { openUrl(context, it.toUri()) } ?: false
        TargetType.APP -> {
            val intent = target.packageName?.let { packageManager.getLaunchIntentForPackage(it) }
            if (intent == null) {
                false
            } else {
                tryStart(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** URL を Custom Tabs で開く（失敗時は ACTION_VIEW）。開けたら true */
    fun openUrl(context: Context, url: String): Boolean = openUrl(context, url.toUri())

    private fun openUrl(context: Context, uri: Uri): Boolean {
        val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            customTabs.launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            // Custom Tabs に対応したブラウザがない場合は ACTION_VIEW にフォールバック
            tryStart(context, Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
