package app.memorygate.update

import app.memorygate.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration

/**
 * GitHub Releases で新しいバージョンが公開されているかを確認する。
 * ホーム画面の表示時にだけ呼ぶ（ゲート・オンボーディング・AccessibilityService からは呼ばない）。
 */
class UpdateChecker(
    private val httpClient: HttpClient,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val currentVersion: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {

    /** 前回の確認から 1 時間以上たっていれば問い合わせる。失敗しても例外は投げない */
    suspend fun checkIfDue() {
        val now = clock.millis()
        if (!isDue(settings.updateLastCheckedAt.first(), now)) return
        query(now)
    }

    /**
     * 「アップデートを確認」ボタン: 前回の確認日時にかかわらず、すぐに問い合わせる。
     * 結果の保存は [checkIfDue] と同じ。失敗しても例外は投げず、[ManualCheckResult.Failed] を返す。
     */
    suspend fun checkNow(): ManualCheckResult = query(clock.millis())

    private suspend fun query(now: Long): ManualCheckResult {
        try {
            val release = fetchLatestRelease() ?: return ManualCheckResult.Failed
            if (!AppVersion.isNewer(release.version, currentVersion)) return ManualCheckResult.UpToDate
            settings.setLatestRelease(release.version, release.htmlUrl)
            return ManualCheckResult.UpdateAvailable(release.version, release.htmlUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("更新確認に失敗しました", e)
            return ManualCheckResult.Failed
        } finally {
            // 成功・失敗にかかわらず前回の確認日時を更新する
            withContext(NonCancellable) { settings.setUpdateLastCheckedAt(now) }
        }
    }

    private suspend fun fetchLatestRelease(): LatestRelease? = withContext(ioDispatcher) {
        val response = httpClient.get(LATEST_RELEASE_URL, mapOf("Accept" to "application/vnd.github+json"))
        if (response.code != 200) {
            // 403 / 429 はレート制限、404 はリリースなし
            log("更新確認: HTTP ${response.code}", null)
            return@withContext null
        }
        ReleaseParser.parse(response.body).also {
            if (it == null) log("更新確認: レスポンスを解析できませんでした", null)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/tortillaproduction/memory-gate/releases/latest"
        val CHECK_INTERVAL: Duration = Duration.ofHours(1)

        /**
         * 問い合わせるべきか。未確認、または前回から 1 時間以上たっていれば true。
         * 前回の確認日時が未来（時計の巻き戻り）の場合も、確認が止まらないように true にする。
         */
        fun isDue(lastCheckedAt: Long?, now: Long): Boolean {
            if (lastCheckedAt == null || lastCheckedAt > now) return true
            return now - lastCheckedAt >= CHECK_INTERVAL.toMillis()
        }

        /**
         * バナーを表示するか。保存された最新バージョンが現在より新しく、
         * かつ閉じたバージョンより新しい（または閉じていない）とき。
         */
        fun shouldShowBanner(latestVersion: String?, currentVersion: String, dismissedVersion: String?): Boolean {
            if (!AppVersion.isNewer(latestVersion, currentVersion)) return false
            return dismissedVersion == null || AppVersion.isNewer(latestVersion, dismissedVersion)
        }
    }
}

/** 確認の結果（「アップデートを確認」ボタンで表示する） */
sealed interface ManualCheckResult {
    /** 現在のバージョンが最新 */
    data object UpToDate : ManualCheckResult

    /** 新しいバージョンが公開されている */
    data class UpdateAvailable(val version: String, val htmlUrl: String) : ManualCheckResult

    /** 通信エラー・解析エラー・レート制限など */
    data object Failed : ManualCheckResult
}
