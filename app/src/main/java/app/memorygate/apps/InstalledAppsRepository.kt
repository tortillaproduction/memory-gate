package app.memorygate.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** インストール済みアプリ（ランチャーに表示されるもの）の取得 */
class InstalledAppsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    /** ランチャーに表示されるアプリをアプリ名順に返す（`excluded` に含まれるものは除く） */
    suspend fun loadLauncherApps(excluded: Set<String>): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.JAPANESE)
        packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName to it }
            .filter { (pkg, _) -> pkg !in excluded }
            .distinctBy { (pkg, _) -> pkg }
            .map { (pkg, info) -> InstalledApp(pkg, info.loadLabel(packageManager).toString()) }
            .sortedWith(compareBy(collator) { it.label })
            .toList()
    }

    /** 監視対象アプリとして選択できないパッケージ */
    suspend fun guardExcludedPackages(): Set<String> = withContext(Dispatchers.IO) {
        ExcludedApps.forGuardSelection(
            ownPackage = appContext.packageName,
            defaultDialer = defaultDialerPackage(),
            defaultSms = runCatching { Telephony.Sms.getDefaultSmsPackage(appContext) }.getOrNull(),
            homePackages = homePackages(),
            imePackages = imePackages(),
        )
    }

    fun targetExcludedPackages(): Set<String> = ExcludedApps.forTargetSelection(appContext.packageName)

    fun loadIcon(packageName: String): Drawable? =
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun loadLabel(packageName: String): String? = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    private fun defaultDialerPackage(): String? = runCatching {
        appContext.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull()

    private fun homePackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
    }

    private fun imePackages(): List<String> = runCatching {
        appContext.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            .orEmpty()
    }.getOrDefault(emptyList())
}
