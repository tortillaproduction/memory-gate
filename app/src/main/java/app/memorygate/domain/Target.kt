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
 * @property snoozeImagePath スヌーズ用ゲートに表示する画像（アプリ内部ストレージのファイルパス）
 * @property snoozeEnabled v0.1.6 までの誘導先ごとのスヌーズの ON/OFF。v0.1.7 からは使わない（全体の設定への引き継ぎでだけ読む）
 * @property snoozeIntervalMinutes 同上（間隔）
 * @property snoozeStartMinutes 同上（時間帯の開始）
 * @property snoozeEndMinutes 同上（時間帯の終了）
 * @property lastSnoozeShownAt 同上（最後に表示した日時）
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
    companion object {
        /** UI で選択できる「N日ごと」の値 */
        val ALLOWED_INTERVAL_DAYS = listOf(1, 3, 7)
    }
}
