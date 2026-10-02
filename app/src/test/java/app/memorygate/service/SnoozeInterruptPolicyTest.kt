package app.memorygate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
