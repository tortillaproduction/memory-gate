package app.memorygate.domain

/** 誘導先の種別 */
enum class TargetType { URL, APP }

/** スケジュールの種別 */
enum class ScheduleType { EVERY_N_DAYS, WEEKLY }

/**
 * 誘導先（やるべきこと）。
 *
 * @property intervalDays [ScheduleType.EVERY_N_DAYS] のとき 1 / 3 / 7 のいずれか
 * @property dayOfWeek [ScheduleType.WEEKLY] のとき 1(月)〜7(日)。`java.time.DayOfWeek` の値
 * @property lastVisitedAt 最後にゲートから開いた日時 (epoch millis)。null = 未訪問
 * @property snoozeEnabled スヌーズの ON/OFF。OFF にしても他のスヌーズ設定は保持する
 * @property snoozeIntervalMinutes スヌーズの間隔（1 / 5 / 30 / 60 分）。null は既定値（30 分）
 * @property snoozeStartMinutes スヌーズする時間帯の開始（0:00 からの分数）。null は既定値（9:00）
 * @property snoozeEndMinutes スヌーズする時間帯の終了（0:00 からの分数）。null は既定値（22:00）
 * @property snoozeImagePath スヌーズ用ゲートに表示する画像（アプリ内部ストレージのファイルパス）
 * @property lastSnoozeShownAt スヌーズ用ゲートを最後に表示した日時 (epoch millis)
 */
data class Target(
    val id: Long = 0,
    val title: String,
    val type: TargetType,
    val url: String? = null,
    val packageName: String? = null,
    val scheduleType: ScheduleType,
    val intervalDays: Int? = null,
    val dayOfWeek: Int? = null,
    val lastVisitedAt: Long? = null,
    val createdAt: Long,
    val snoozeEnabled: Boolean = false,
    val snoozeIntervalMinutes: Int? = null,
    val snoozeStartMinutes: Int? = null,
    val snoozeEndMinutes: Int? = null,
    val snoozeImagePath: String? = null,
    val lastSnoozeShownAt: Long? = null,
) {
    /** 未設定（null）なら既定値を使う */
    val effectiveSnoozeIntervalMinutes: Int get() = snoozeIntervalMinutes ?: DEFAULT_SNOOZE_INTERVAL_MINUTES
    val effectiveSnoozeStartMinutes: Int get() = snoozeStartMinutes ?: DEFAULT_SNOOZE_START_MINUTES
    val effectiveSnoozeEndMinutes: Int get() = snoozeEndMinutes ?: DEFAULT_SNOOZE_END_MINUTES

    companion object {
        /** UI で選択できる「N日ごと」の値 */
        val ALLOWED_INTERVAL_DAYS = listOf(1, 3, 7)

        /** UI で選択できるスヌーズの間隔（分） */
        val ALLOWED_SNOOZE_INTERVAL_MINUTES = listOf(1, 5, 30, 60)
        const val DEFAULT_SNOOZE_INTERVAL_MINUTES = 30
        const val DEFAULT_SNOOZE_START_MINUTES = 9 * 60
        const val DEFAULT_SNOOZE_END_MINUTES = 22 * 60
    }
}
