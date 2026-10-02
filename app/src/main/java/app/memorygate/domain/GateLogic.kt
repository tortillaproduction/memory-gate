package app.memorygate.domain

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * ゲート表示に関するコアロジック（SPEC 4 章）。
 * 日付はすべて端末ローカルタイムゾーンの [LocalDate] で判定する。
 */
object GateLogic {

    /** ゲートに表示する誘導先の最大件数 */
    const val MAX_GATE_TARGETS = 5

    fun today(clock: Clock): LocalDate = LocalDate.now(clock)

    fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** 4.1 期限切れ判定 */
    fun isDue(target: Target, today: LocalDate, zone: ZoneId): Boolean {
        val lastVisitedAt = target.lastVisitedAt ?: return true
        val lastDate = toLocalDate(lastVisitedAt, zone)
        if (lastDate.isAfter(today)) return false
        return when (target.scheduleType) {
            ScheduleType.EVERY_N_DAYS -> {
                val interval = target.intervalDays ?: return false
                !today.isBefore(lastDate.plusDays(interval.toLong()))
            }
            ScheduleType.WEEKLY -> {
                val dayOfWeek = target.dayOfWeek ?: return false
                val anchor = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(dayOfWeek)))
                lastDate.isBefore(anchor)
            }
        }
    }

    /**
     * 次に期限切れになる日。未訪問・すでに期限切れのときは null（＝今すぐ）。
     * ホーム画面の「次の期限日」表示用。
     */
    fun nextDueDate(target: Target, today: LocalDate, zone: ZoneId): LocalDate? {
        if (isDue(target, today, zone)) return null
        val lastDate = toLocalDate(target.lastVisitedAt ?: return null, zone)
        return when (target.scheduleType) {
            ScheduleType.EVERY_N_DAYS ->
                lastDate.plusDays((target.intervalDays ?: return null).toLong())
            ScheduleType.WEEKLY -> {
                val dayOfWeek = DayOfWeek.of(target.dayOfWeek ?: return null)
                // lastDate が未来日時の場合も含め、today と lastDate の遅い方の翌日以降で直近の曜日
                val base = if (lastDate.isAfter(today)) lastDate else today
                base.with(TemporalAdjusters.next(dayOfWeek))
            }
        }
    }

    /** 4.2 の並び順: lastVisitedAt の昇順（null = 未訪問が最優先）→ 同順位は createdAt の昇順 */
    val GATE_ORDER: Comparator<Target> =
        compareBy<Target, Long?>(nullsFirst()) { it.lastVisitedAt }.thenBy { it.createdAt }

    /** 4.2 ゲートに表示する誘導先 */
    fun selectGateTargets(targets: List<Target>, today: LocalDate, zone: ZoneId): List<Target> =
        targets
            .filter { isDue(it, today, zone) }
            .sortedWith(GATE_ORDER)
            .take(MAX_GATE_TARGETS)

    /** 4.3 ゲートを表示するか */
    fun shouldShowGate(
        packageName: String,
        today: LocalDate,
        zone: ZoneId,
        guardedPackages: Set<String>,
        gatePassedDate: LocalDate?,
        targets: List<Target>,
    ): Boolean =
        packageName in guardedPackages &&
            gatePassedDate != today &&
            selectGateTargets(targets, today, zone).isNotEmpty()
}
