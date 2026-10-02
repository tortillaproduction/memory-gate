package app.memorygate.service

import java.time.Instant

/** 前面に来たアプリの分類（SPEC 5 章のスヌーズの割り込み） */
enum class ForegroundKind {
    /** 監視対象アプリ: 判定し、必要ならタイマーを設定する */
    GUARDED,

    /** 自アプリ（ゲート画面など）・IME・SystemUI（通知シェード）: タイマーを止めない */
    TRANSIENT,

    /** それ以外のアプリ: タイマーを止める */
    OTHER,
}

/** スヌーズの割り込みに関する判断（ユニットテスト用に AccessibilityService から分離） */
object SnoozeInterruptPolicy {

    /** 通知シェードなどのシステム UI */
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    /** タイマーの最短の待ち時間（同じ時刻で何度も判定しないように） */
    const val MIN_DELAY_MILLIS = 1_000L

    fun classify(
        packageName: String,
        ownPackage: String,
        guardedPackages: Set<String>,
        imePackages: Set<String>,
    ): ForegroundKind = when {
        packageName == ownPackage || packageName == SYSTEM_UI_PACKAGE || packageName in imePackages -> ForegroundKind.TRANSIENT
        packageName in guardedPackages -> ForegroundKind.GUARDED
        else -> ForegroundKind.OTHER
    }

    /** 次にスヌーズが可能になる時刻までの待ち時間。null ならタイマーを設定しない */
    fun timerDelayMillis(nextReadyAt: Instant?, now: Instant): Long? {
        nextReadyAt ?: return null
        return maxOf(MIN_DELAY_MILLIS, nextReadyAt.toEpochMilli() - now.toEpochMilli())
    }
}
