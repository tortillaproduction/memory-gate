package app.memorygate.gate

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType

/**
 * 誘導先を開いた結果。
 *
 * @property opened 開けたら true
 * @property packageName 実際に起動したアプリのパッケージ（type=APP はその packageName、type=URL は Custom Tabs または
 *   ACTION_VIEW で解決されたブラウザ）。選択ダイアログが出た場合など、分からなければ null
 */
data class OpenResult(val opened: Boolean, val packageName: String? = null)

/** 誘導先を開く（URL は Custom Tabs、アプリはランチャー Intent） */
class TargetLauncher(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    /** type=APP のとき、起動できるか（アンインストール済みなどでないか） */
    fun canLaunch(target: Target): Boolean = when (target.type) {
        TargetType.URL -> target.url != null
        TargetType.APP -> target.packageName?.let { packageManager.getLaunchIntentForPackage(it) } != null
    }

    fun open(context: Context, target: Target): OpenResult = when (target.type) {
        TargetType.URL -> target.url?.let { openUri(context, it.toUri()) } ?: OpenResult(false)
        TargetType.APP -> {
            val pkg = target.packageName
            val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            if (intent == null) {
                OpenResult(false)
            } else {
                OpenResult(tryStart(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), pkg)
            }
        }
    }

    /** URL を Custom Tabs で開く（失敗時は ACTION_VIEW）。開けたら true */
    fun openUrl(context: Context, url: String): Boolean = openUri(context, url.toUri()).opened

    private fun openUri(context: Context, uri: Uri): OpenResult {
        val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            customTabs.launchUrl(context, uri)
            OpenResult(true, resolvePackage(Intent(customTabs.intent).setData(uri)))
        } catch (_: ActivityNotFoundException) {
            // Custom Tabs に対応したブラウザがない場合は ACTION_VIEW にフォールバック
            val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            OpenResult(tryStart(context, view), resolvePackage(view))
        }
    }

    /**
     * Intent を処理するアプリのパッケージ。既定のアプリが決まっていない（選択ダイアログになる）場合は null。
     * 選択ダイアログはシステム（`android`）のアクティビティに解決される。
     */
    private fun resolvePackage(intent: Intent): String? {
        intent.`package`?.let { return it }
        val pkg = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return pkg?.takeIf { it != "android" }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
