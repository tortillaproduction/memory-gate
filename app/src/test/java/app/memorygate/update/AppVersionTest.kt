package app.memorygate.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parse() {
        assertEquals(AppVersion(0, 1, 3), AppVersion.parse("0.1.3"))
        assertEquals(AppVersion(0, 1, 3), AppVersion.parse("v0.1.3"))
        assertEquals(AppVersion(0, 1, 3), AppVersion.parse("V0.1.3"))
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("v1.2"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2"))
        assertEquals(AppVersion(0, 2, 0), AppVersion.parse("v0.2.0-beta1"))
        assertEquals(AppVersion(0, 1, 3), AppVersion.parse(" v0.1.3 "))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("v0.1.x"))
        assertNull(AppVersion.parse("0.1.3.4"))
        assertNull(AppVersion.parse("99999999999.0.0"))
    }

    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(AppVersion.isNewer("0.1.10", "0.1.9"))
        assertFalse(AppVersion.isNewer("0.1.9", "0.1.10"))
        assertTrue(AppVersion.isNewer("0.10.0", "0.9.9"))
        assertTrue(AppVersion.isNewer("1.0.0", "0.99.99"))
        assertTrue(AppVersion.isNewer("v0.1.3", "0.1.2"))
        assertFalse(AppVersion.isNewer("0.1.3", "0.1.3"))
        assertFalse(AppVersion.isNewer("v0.1.3", "0.1.3"))
        assertFalse(AppVersion.isNewer("0.1.2", "0.1.3"))
        assertFalse(AppVersion.isNewer("garbage", "0.1.3"))
        assertFalse(AppVersion.isNewer("0.1.4", "garbage"))
        assertFalse(AppVersion.isNewer(null, "0.1.3"))
    }
}
