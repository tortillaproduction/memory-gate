package app.memorygate.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri

/**
 * 除外判定に使う端末のパッケージ情報。ユニットテストで差し替えられるようにインターフェースにしている。
 */
interface PackageInfoSource {
    /** 自アプリのパッケージ名 */
    val ownPackage: String

    /** 既定の電話アプリ（`TelecomManager.defaultDialerPackage`） */
    fun defaultDialerPackage(): String?

    /** 既定の SMS アプリ（`Telephony.Sms.getDefaultSmsPackage`） */
    fun defaultSmsPackage(): String?

    /** ホームアプリ（`CATEGORY_HOME` を解決したもの） */
    fun homePackages(): Collection<String>

    /** 有効な IME（`InputMethodManager.enabledInputMethodList`） */
    fun imePackages(): Collection<String>

    /** メールアプリ（`mailto:` の `ACTION_SENDTO` に応答するもの） */
    fun emailPackages(): Collection<String>

    /** `ApplicationInfo.flags`。取得できない場合は null */
    fun applicationFlags(packageName: String): Int?
}

class AndroidPackageInfoSource(context: Context) : PackageInfoSource {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    override val ownPackage: String = appContext.packageName

    override fun defaultDialerPackage(): String? = runCatching {
        appContext.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull()

    override fun defaultSmsPackage(): String? =
        runCatching { Telephony.Sms.getDefaultSmsPackage(appContext) }.getOrNull()

    override fun homePackages(): Collection<String> =
        queryPackages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    override fun imePackages(): Collection<String> = runCatching {
        appContext.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            .orEmpty()
    }.getOrDefault(emptyList())

    override fun emailPackages(): Collection<String> =
        queryPackages(Intent(Intent.ACTION_SENDTO, "mailto:".toUri()))

    override fun applicationFlags(packageName: String): Int? =
        runCatching { packageManager.getApplicationInfo(packageName, 0).flags }.getOrNull()

    private fun queryPackages(intent: Intent): List<String> = runCatching {
        packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.distinct()
    }.getOrDefault(emptyList())
}
