package app.memorygate.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 誘導先に関する表示用の文言 */
object TargetFormat {

    fun dayOfWeekShort(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.JAPANESE)

    /** 「1日ごと」「3日ごと」「1週間ごと」「毎週月曜日」 */
    fun schedule(target: Target): String = when (target.scheduleType) {
        ScheduleType.EVERY_N_DAYS -> when (val days = target.intervalDays) {
            7 -> "1週間ごと"
            null -> "-"
            else -> "${days}日ごと"
        }
        ScheduleType.WEEKLY -> target.dayOfWeek?.let { "毎週${dayOfWeekShort(it)}曜日" } ?: "-"
    }

    /** 「未訪問」「最終: 今日」「最終: N日前」 */
    fun lastVisited(lastVisitedAt: Long?, today: LocalDate, zone: ZoneId): String {
        if (lastVisitedAt == null) return "未訪問"
        val days = ChronoUnit.DAYS.between(GateLogic.toLocalDate(lastVisitedAt, zone), today)
        return if (days <= 0) "最終: 今日" else "最終: ${days}日前"
    }

    /** 「10/3(土)」 */
    fun shortDate(date: LocalDate): String =
        "${date.monthValue}/${date.dayOfMonth}(${dayOfWeekShort(date.dayOfWeek.value)})"
}
