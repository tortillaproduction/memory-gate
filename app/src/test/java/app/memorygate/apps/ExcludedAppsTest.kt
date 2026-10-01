package app.memorygate.apps

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedAppsTest {

    /** テスト用に差し替えるパッケージ情報 */
    private class FakePackageInfoSource(
        override val ownPackage: String = "app.memorygate",
        private val dialer: String? = "com.example.dialer",
        private val sms: String? = "com.example.sms",
        private val home: List<String> = listOf("com.example.launcher"),
        private val ime: List<String> = listOf("com.example.ime"),
        private val email: List<String> = listOf("com.example.mail"),
        private val flags: Map<String, Int> = emptyMap(),
    ) : PackageInfoSource {
        override fun defaultDialerPackage() = dialer
        override fun defaultSmsPackage() = sms
        override fun homePackages() = home
        override fun imePackages() = ime
        override fun emailPackages() = email
        override fun applicationFlags(packageName: String) = flags[packageName]
    }

    private val system = ApplicationInfo.FLAG_SYSTEM
    private val updatedSystem = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP

    @Test
    fun `自アプリ・既定アプリ・ホーム・IME・固定リストは常に除外される`() {
        val result = ExcludedApps.forGuardSelection(FakePackageInfoSource(), emptyList())
        listOf(
            "app.memorygate",
            "com.example.dialer",
            "com.example.sms",
            "com.example.launcher",
            "com.example.ime",
            "jp.naver.line.android",
            "com.android.settings",
            "com.samsung.android.messaging",
        ).forEach { pkg ->
            assertTrue(pkg, pkg in result.excluded(showSystemApps = false))
            assertTrue(pkg, pkg in result.excluded(showSystemApps = true))
        }
        assertFalse("com.twitter.android" in result.excluded(showSystemApps = false))
    }

    @Test
    fun `mailto に応答するアプリと固定リストのメールアプリは常に除外される`() {
        val result = ExcludedApps.forGuardSelection(FakePackageInfoSource(), emptyList())
        val expected = listOf(
            "com.example.mail",
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "jp.co.yahoo.android.ymail",
            "com.samsung.android.email.provider",
            "com.android.email",
        )
        expected.forEach { pkg ->
            assertTrue(pkg, pkg in result.alwaysExcluded)
            assertTrue(pkg, pkg in result.excluded(showSystemApps = true))
        }
    }

    @Test
    fun `更新されていないシステムアプリはスイッチがオフのときだけ除外される`() {
        val source = FakePackageInfoSource(
            flags = mapOf(
                "com.example.clock" to system,
                "com.google.android.youtube" to updatedSystem,
                "com.twitter.android" to 0,
            ),
        )
        val candidates = listOf("com.example.clock", "com.google.android.youtube", "com.twitter.android", "com.unknown")
        val result = ExcludedApps.forGuardSelection(source, candidates)

        assertTrue("com.example.clock" in result.excluded(showSystemApps = false))
        assertFalse("com.example.clock" in result.excluded(showSystemApps = true))
        // ストアで更新されたプリインストールアプリ・通常のアプリ・情報が取れないアプリは除外しない
        listOf("com.google.android.youtube", "com.twitter.android", "com.unknown").forEach { pkg ->
            assertFalse(pkg, pkg in result.excluded(showSystemApps = false))
        }
    }

    @Test
    fun `OS 基盤アプリはシステムアプリとして扱う`() {
        val result = ExcludedApps.forGuardSelection(FakePackageInfoSource(), emptyList())
        listOf("com.android.vending", "com.google.android.gms", "com.google.android.googlequicksearchbox").forEach { pkg ->
            assertTrue(pkg, pkg in result.excluded(showSystemApps = false))
            assertFalse(pkg, pkg in result.excluded(showSystemApps = true))
        }
    }

    @Test
    fun `システムアプリのメールアプリはスイッチをオンにしても除外される`() {
        val source = FakePackageInfoSource(email = listOf("com.example.preinstalled.mail"), flags = mapOf("com.example.preinstalled.mail" to system))
        val result = ExcludedApps.forGuardSelection(source, listOf("com.example.preinstalled.mail"))
        assertTrue("com.example.preinstalled.mail" in result.excluded(showSystemApps = true))
        assertFalse("com.example.preinstalled.mail" in result.systemApps)
    }

    @Test
    fun `既定アプリが取得できなくても固定リストは除外される`() {
        val source = FakePackageInfoSource(dialer = null, sms = null, home = emptyList(), ime = emptyList(), email = emptyList())
        val result = ExcludedApps.forGuardSelection(source, emptyList())
        assertEquals(ExcludedApps.FIXED + ExcludedApps.EMAIL_FIXED + "app.memorygate", result.alwaysExcluded)
    }

    @Test
    fun `システムアプリの判定`() {
        assertTrue(ExcludedApps.isPreinstalledSystemApp(system))
        assertFalse(ExcludedApps.isPreinstalledSystemApp(updatedSystem))
        assertFalse(ExcludedApps.isPreinstalledSystemApp(0))
        assertFalse(ExcludedApps.isPreinstalledSystemApp(null))
    }

    @Test
    fun `監視対象に登録済みのアプリは除外対象でも表示する`() {
        val result = ExcludedApps.forGuardSelection(
            FakePackageInfoSource(flags = mapOf("com.example.clock" to system)),
            listOf("com.example.clock"),
        )
        assertFalse(result.isVisible("com.google.android.gm", showSystemApps = true, guarded = emptySet()))
        assertTrue(result.isVisible("com.google.android.gm", showSystemApps = false, guarded = setOf("com.google.android.gm")))
        assertFalse(result.isVisible("com.example.clock", showSystemApps = false, guarded = emptySet()))
        assertTrue(result.isVisible("com.example.clock", showSystemApps = true, guarded = emptySet()))
        assertTrue(result.isVisible("com.twitter.android", showSystemApps = false, guarded = emptySet()))
    }

    @Test
    fun `誘導先の選択では自アプリだけを除外する`() {
        assertEquals(setOf("app.memorygate"), ExcludedApps.forTargetSelection("app.memorygate"))
    }
}
