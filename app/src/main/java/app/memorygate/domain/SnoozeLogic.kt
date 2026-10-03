package app.memorygate.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** どのゲートを表示するか */
sealed interface GateDecision {
    data object None : GateDecision

    /** 通常のゲート（SPEC 4.3） */
    data object Normal : GateDecision

    /** スヌーズ用ゲート */
    data class Snooze(val target: Target) : GateDecision
}

/**
 * スヌーズの判定（SPEC 4.7）。設定はアプリ全体で 1 つ（[SnoozeSettings]）。日時は端末のローカルタイムゾーンで判定する。
 */
object SnoozeLogic {

    private const val MINUTES_PER_DAY = 24 * 60
    private const val MILLIS_PER_MINUTE = 60_000L

    private fun minuteOfDay(now: Instant, zone: ZoneId): Int {
        val t = now.atZone(zone)
        return t.hour * 60 + t.minute
    }

    /**
     * 現在時刻がスヌーズの時間帯に入っているか。開始は含み、終了は含まない。
     * - 開始 < 終了: 通常の範囲
     * - 開始 > 終了: 日付をまたぐ（例: 22:00〜2:00）
     * - 開始 == 終了: 24 時間ずっと
     */
    fun isInSnoozeWindow(settings: SnoozeSettings, now: Instant, zone: ZoneId): Boolean =
        isInWindow(minuteOfDay(now, zone), settings.startMinutes, settings.endMinutes)

    internal fun isInWindow(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    /** 前回の表示（アプリ全体の `lastSnoozeShownAt`）から間隔が経過したか。未表示なら true */
    fun isIntervalElapsed(settings: SnoozeSettings, lastShownAt: Long?, now: Instant): Boolean {
        lastShownAt ?: return true
        return now.toEpochMilli() - lastShownAt >= settings.intervalMinutes * MILLIS_PER_MINUTE
    }

    /** スヌーズ用ゲートを出せる状態か: スヌーズ ON・時間帯の中・前回の表示から間隔が経過 */
    fun isSnoozeReady(settings: SnoozeSettings, lastShownAt: Long?, now: Instant, zone: ZoneId): Boolean =
        settings.enabled && isInSnoozeWindow(settings, now, zone) && isIntervalElapsed(settings, lastShownAt, now)

    /**
     * スヌーズ用ゲートに表示する誘導先。すべての誘導先の中から SPEC 4.2 と同じ並び順で先頭の 1 件（期限切れかどうかは問わない）。
     * 誘導先がなければ null
     */
    fun selectSnoozeTarget(targets: List<Target>): Target? = targets.minWithOrNull(GateLogic.GATE_ORDER)

    /**
     * 表示するゲートを決める。通常のゲートの条件（SPEC 4.3）を満たすときは通常のゲートを優先し、
     * 満たさないときにスヌーズを判定する。監視対象アプリでなければ何も出さない。
     */
    fun decideGate(
        packageName: String,
        now: Instant,
        zone: ZoneId,
        guardedPackages: Set<String>,
        gatePassedDate: LocalDate?,
        targets: List<Target>,
        settings: SnoozeSettings,
        lastShownAt: Long?,
    ): GateDecision {
        if (packageName !in guardedPackages) return GateDecision.None
        val today = now.atZone(zone).toLocalDate()
        if (GateLogic.shouldShowGate(packageName, today, zone, guardedPackages, gatePassedDate, targets)) {
            return GateDecision.Normal
        }
        if (!isSnoozeReady(settings, lastShownAt, now, zone)) return GateDecision.None
        return selectSnoozeTarget(targets)?.let { GateDecision.Snooze(it) } ?: GateDecision.None
    }

    /**
     * 次にスヌーズが可能になる時刻（前回の表示 + 間隔と、時間帯の開始のうち遅いもの）。すでに可能なら `now`。
     * スヌーズ OFF、または誘導先が 1 件もなければ null。監視対象アプリが前面にある間のタイマーに使う。
     */
    fun nextSnoozeReadyAt(
        settings: SnoozeSettings,
        lastShownAt: Long?,
        targets: List<Target>,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        if (!settings.enabled || targets.isEmpty()) return null
        var t = now
        // 前回の表示から間隔をあける
        lastShownAt?.let { last -> t = maxOf(t, Instant.ofEpochMilli(last + settings.intervalMinutes * MILLIS_PER_MINUTE)) }
        // 時間帯の外なら、次に時間帯が始まる時刻まで待つ（間隔は一度経過すれば経過したままなので、後ろにずらしても成り立つ）
        return nextWindowStart(settings, t, zone)
    }

    private fun nextWindowStart(settings: SnoozeSettings, t: Instant, zone: ZoneId): Instant {
        val start = settings.startMinutes
        val end = settings.endMinutes
        val minute = minuteOfDay(t, zone)
        if (isInWindow(minute, start, end)) return t
        val local: ZonedDateTime = t.atZone(zone)
        val day = if (minute < start) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return day.atStartOfDay(zone).plusMinutes(start.toLong() % MINUTES_PER_DAY).toInstant()
    }
}

/** スヌーズ用ゲートの背景（SPEC 7.5） */
object SnoozeBackground {
    private const val DAY_START_MINUTES = 6 * 60
    private const val DAY_END_MINUTES = 18 * 60

    /** 6:00〜18:00（18:00 は含まない）は昼の空、それ以外は夜の星空 */
    fun isDaytime(now: Instant, zone: ZoneId): Boolean {
        val t = now.atZone(zone)
        val minute = t.hour * 60 + t.minute
        return minute in DAY_START_MINUTES until DAY_END_MINUTES
    }
}
