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
) {
    companion object {
        /** UI で選択できる「N日ごと」の値 */
        val ALLOWED_INTERVAL_DAYS = listOf(1, 3, 7)
    }
}
