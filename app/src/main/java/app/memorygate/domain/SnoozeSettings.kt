package app.memorygate.domain

/**
 * スヌーズの設定（アプリ全体で 1 つ。SPEC 3.3・4.7）。v0.1.7 から誘導先ごとではなく DataStore に保存する。
 *
 * @property enabled スヌーズの ON/OFF。OFF にしても他の設定は保持する
 * @property intervalMinutes 間隔（1 / 5 / 30 / 60 分）
 * @property startMinutes 時間帯の開始（0:00 からの分数）
 * @property endMinutes 時間帯の終了（0:00 からの分数）
 */
data class SnoozeSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val startMinutes: Int = DEFAULT_START_MINUTES,
    val endMinutes: Int = DEFAULT_END_MINUTES,
) {
    companion object {
        /** UI で選択できる間隔（分） */
        val ALLOWED_INTERVAL_MINUTES = listOf(1, 5, 30, 60)
        const val DEFAULT_INTERVAL_MINUTES = 30
        const val DEFAULT_START_MINUTES = 9 * 60
        const val DEFAULT_END_MINUTES = 22 * 60
        private const val MINUTES_PER_DAY = 24 * 60

        /** 保存値を読むときの補正: 間隔は選択肢にない値なら既定値、時刻は 0:00〜23:59 の範囲外なら既定値 */
        fun sanitized(enabled: Boolean, intervalMinutes: Int?, startMinutes: Int?, endMinutes: Int?) = SnoozeSettings(
            enabled = enabled,
            intervalMinutes = intervalMinutes?.takeIf { it in ALLOWED_INTERVAL_MINUTES } ?: DEFAULT_INTERVAL_MINUTES,
            startMinutes = startMinutes?.takeIf { it in 0 until MINUTES_PER_DAY } ?: DEFAULT_START_MINUTES,
            endMinutes = endMinutes?.takeIf { it in 0 until MINUTES_PER_DAY } ?: DEFAULT_END_MINUTES,
        )
    }
}

/**
 * v0.1.6 までの誘導先ごとのスヌーズ設定を、アプリ全体の設定へ引き継ぐ（v0.1.7、SPEC 3.3）。
 */
object SnoozeSettingsMigration {

    /**
     * - スヌーズ ON の誘導先がある: 全体を ON にし、そのうち SPEC 4.2 の並び順で先頭の誘導先の間隔・時間帯（NULL なら既定値）を使う
     * - スヌーズ ON の誘導先がない: 既定値（OFF）
     */
    fun fromTargets(targets: List<Target>): SnoozeSettings {
        val first = targets.filter { it.snoozeEnabled }.minWithOrNull(GateLogic.GATE_ORDER) ?: return SnoozeSettings()
        return SnoozeSettings.sanitized(
            enabled = true,
            intervalMinutes = first.snoozeIntervalMinutes,
            startMinutes = first.snoozeStartMinutes,
            endMinutes = first.snoozeEndMinutes,
        )
    }
}
