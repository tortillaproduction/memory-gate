package app.memorygate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SnoozeInterruptPolicyTest {
    private val own = "app.memorygate"
    private val guarded = setOf("com.sns")
    private val ime = setOf("com.google.android.inputmethod.latin")

    private fun classify(pkg: String) = SnoozeInterruptPolicy.classify(pkg, own, guarded, ime)

    @Test
    fun classify() {
        assertEquals(ForegroundKind.GUARDED, classify("com.sns"))
        assertEquals(ForegroundKind.OTHER, classify("com.android.chrome"))
        assertEquals(ForegroundKind.OTHER, classify("com.google.android.apps.nexuslauncher"))
        // 自アプリ・IME・SystemUI ではタイマーを止めない
        assertEquals(ForegroundKind.TRANSIENT, classify(own))
        assertEquals(ForegroundKind.TRANSIENT, classify("com.google.android.inputmethod.latin"))
        assertEquals(ForegroundKind.TRANSIENT, classify("com.android.systemui"))
    }

    @Test
    fun timerDelay() {
        val now = Instant.parse("2026-10-02T03:00:00Z")
        assertNull(SnoozeInterruptPolicy.timerDelayMillis(null, now))
        assertEquals(5 * 60_000L, SnoozeInterruptPolicy.timerDelayMillis(now.plusSeconds(300), now))
        // すでに可能・過去の時刻なら最短の待ち時間
        assertEquals(SnoozeInterruptPolicy.MIN_DELAY_MILLIS, SnoozeInterruptPolicy.timerDelayMillis(now, now))
        assertEquals(SnoozeInterruptPolicy.MIN_DELAY_MILLIS, SnoozeInterruptPolicy.timerDelayMillis(now.minusSeconds(60), now))
    }

    // ---- 切り替えの判定（ForegroundTracker） ----

    private val twoGuarded = setOf("com.sns", "com.video")

    private fun ForegroundTracker.on(pkg: String) = onForeground(pkg, twoGuarded, ime)

    @Test
    fun switchFromOtherAppOrHome() {
        val tracker = ForegroundTracker(own)
        // サービス開始直後の最初の監視対象アプリも切り替えとみなす
        assertEquals(ForegroundChange(ForegroundKind.GUARDED, switched = true), tracker.on("com.sns"))
        assertEquals(ForegroundChange(ForegroundKind.OTHER, switched = false), tracker.on("com.android.launcher"))
        assertTrue(tracker.on("com.sns").switched)
    }

    @Test
    fun switchBetweenGuardedApps() {
        val tracker = ForegroundTracker(own)
        tracker.on("com.sns")
        assertTrue(tracker.on("com.video").switched)
        assertTrue(tracker.on("com.sns").switched)
    }

    @Test
    fun returningThroughImeSystemUiOrOwnAppIsNotSwitch() {
        val tracker = ForegroundTracker(own)
        tracker.on("com.sns")
        for (transient in listOf("com.google.android.inputmethod.latin", "com.android.systemui", own)) {
            assertEquals(ForegroundChange(ForegroundKind.TRANSIENT, switched = false), tracker.on(transient))
            assertFalse(tracker.on("com.sns").switched)
        }
        // 経由するものが続いても、直前の前面（それらを除いた最後のパッケージ）で判定する
        tracker.on("com.android.systemui")
        tracker.on(own)
        assertFalse(tracker.on("com.sns").switched)
        assertEquals("com.sns", tracker.lastSignificantPackage)
    }

    @Test
    fun returningThroughTransientToAnotherGuardedAppIsSwitch() {
        val tracker = ForegroundTracker(own)
        tracker.on("com.sns")
        tracker.on("com.android.systemui")
        assertTrue(tracker.on("com.video").switched)
    }
}
