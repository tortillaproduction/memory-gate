package app.memorygate.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedAppsTest {

    @Test
    fun `監視対象の除外には自アプリ・既定アプリ・ホーム・IME・固定リストが含まれる`() {
        val excluded = ExcludedApps.forGuardSelection(
            ownPackage = "app.memorygate",
            defaultDialer = "com.example.dialer",
            defaultSms = "com.example.sms",
            homePackages = listOf("com.example.launcher"),
            imePackages = listOf("com.example.ime"),
        )
        listOf(
            "app.memorygate",
            "com.example.dialer",
            "com.example.sms",
            "com.example.launcher",
            "com.example.ime",
            "jp.naver.line.android",
            "com.android.settings",
            "com.samsung.android.messaging",
        ).forEach { assertTrue(it, it in excluded) }
        assertTrue("com.twitter.android" !in excluded)
    }

    @Test
    fun `既定アプリが取得できなくても固定リストは除外される`() {
        val excluded = ExcludedApps.forGuardSelection("app.memorygate", null, null, emptyList(), emptyList())
        assertEquals(ExcludedApps.FIXED + "app.memorygate", excluded)
    }

    @Test
    fun `誘導先の選択では自アプリだけを除外する`() {
        assertEquals(setOf("app.memorygate"), ExcludedApps.forTargetSelection("app.memorygate"))
    }
}
