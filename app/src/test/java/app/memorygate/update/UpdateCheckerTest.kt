package app.memorygate.update

import app.memorygate.data.StoredRelease
import app.memorygate.gate.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {

    private val now = Instant.parse("2026-10-01T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Tokyo"))
    private val hour = 60 * 60 * 1000L

    private val releaseJson = """{"tag_name":"v0.1.4","html_url":"https://github.com/tortillaproduction/memory-gate/releases/tag/v0.1.4"}"""

    /** 呼ばれた回数とヘッダーを記録するフェイク */
    private class FakeHttpClient(private val respond: () -> HttpResponse) : HttpClient {
        var calls = 0
        var lastUrl: String? = null
        var lastHeaders: Map<String, String> = emptyMap()
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            calls++
            lastUrl = url
            lastHeaders = headers
            return respond()
        }
    }

    private fun checker(http: HttpClient, settings: FakeSettingsRepository, current: String = "0.1.3", logs: MutableList<String>? = null) =
        UpdateChecker(
            httpClient = http,
            settings = settings,
            clock = clock,
            currentVersion = current,
            ioDispatcher = UnconfinedTestDispatcher(),
            log = { message, _ -> logs?.add(message) },
        )

    @Test
    fun savesNewerReleaseAndCheckTime() = runTest {
        val settings = FakeSettingsRepository()
        val http = FakeHttpClient { HttpResponse(200, releaseJson) }
        checker(http, settings).checkIfDue()

        assertEquals(1, http.calls)
        assertEquals(UpdateChecker.LATEST_RELEASE_URL, http.lastUrl)
        assertEquals("application/vnd.github+json", http.lastHeaders["Accept"])
        assertEquals(StoredRelease("0.1.4", "https://github.com/tortillaproduction/memory-gate/releases/tag/v0.1.4"), settings.release.value)
        assertEquals(now.toEpochMilli(), settings.lastChecked.value)
    }

    @Test
    fun doesNotSaveSameOrOlderRelease() = runTest {
        val settings = FakeSettingsRepository()
        checker(FakeHttpClient { HttpResponse(200, releaseJson) }, settings, current = "0.1.4").checkIfDue()
        assertNull(settings.release.value)
        assertEquals(now.toEpochMilli(), settings.lastChecked.value)
    }

    @Test
    fun skipsWithin1Hour() = runTest {
        val settings = FakeSettingsRepository()
        settings.lastChecked.value = now.toEpochMilli() - hour + 1
        val http = FakeHttpClient { HttpResponse(200, releaseJson) }
        checker(http, settings).checkIfDue()
        assertEquals(0, http.calls)
        assertEquals(now.toEpochMilli() - hour + 1, settings.lastChecked.value)
    }

    @Test
    fun checksAfter1Hour() = runTest {
        val settings = FakeSettingsRepository()
        settings.lastChecked.value = now.toEpochMilli() - hour
        val http = FakeHttpClient { HttpResponse(200, releaseJson) }
        checker(http, settings).checkIfDue()
        assertEquals(1, http.calls)
    }

    @Test
    fun networkErrorIsLoggedAndCheckTimeUpdated() = runTest {
        val settings = FakeSettingsRepository()
        val logs = mutableListOf<String>()
        checker(FakeHttpClient { throw SocketTimeoutException("timeout") }, settings, logs = logs).checkIfDue()
        checker(FakeHttpClient { throw IOException("offline") }, FakeSettingsRepository(), logs = logs).checkIfDue()
        assertNull(settings.release.value)
        assertEquals(now.toEpochMilli(), settings.lastChecked.value)
        assertEquals(2, logs.size)
    }

    @Test
    fun rateLimitAndParseErrorAreIgnored() = runTest {
        for (response in listOf(
            HttpResponse(403, """{"message":"API rate limit exceeded"}"""),
            HttpResponse(429, ""),
            HttpResponse(404, """{"message":"Not Found"}"""),
            HttpResponse(200, "not json"),
        )) {
            val settings = FakeSettingsRepository()
            val logs = mutableListOf<String>()
            checker(FakeHttpClient { response }, settings, logs = logs).checkIfDue()
            assertNull(settings.release.value)
            assertEquals(now.toEpochMilli(), settings.lastChecked.value)
            assertEquals(1, logs.size)
        }
    }

    @Test
    fun checkNowQueriesEvenWithin1Hour() = runTest {
        val settings = FakeSettingsRepository()
        settings.lastChecked.value = now.toEpochMilli() - 1
        val http = FakeHttpClient { HttpResponse(200, releaseJson) }
        val result = checker(http, settings).checkNow()

        assertEquals(1, http.calls)
        assertEquals(
            ManualCheckResult.UpdateAvailable("0.1.4", "https://github.com/tortillaproduction/memory-gate/releases/tag/v0.1.4"),
            result,
        )
        assertEquals(StoredRelease("0.1.4", "https://github.com/tortillaproduction/memory-gate/releases/tag/v0.1.4"), settings.release.value)
        assertEquals(now.toEpochMilli(), settings.lastChecked.value)
    }

    @Test
    fun checkNowReportsUpToDate() = runTest {
        for (current in listOf("0.1.4", "0.1.10")) {
            val settings = FakeSettingsRepository()
            val result = checker(FakeHttpClient { HttpResponse(200, releaseJson) }, settings, current = current).checkNow()
            assertEquals(ManualCheckResult.UpToDate, result)
            assertNull(settings.release.value)
            assertEquals(now.toEpochMilli(), settings.lastChecked.value)
        }
    }

    @Test
    fun checkNowReportsFailure() = runTest {
        for (http in listOf(
            FakeHttpClient { throw IOException("offline") },
            FakeHttpClient { HttpResponse(403, """{"message":"API rate limit exceeded"}""") },
            FakeHttpClient { HttpResponse(200, "not json") },
        )) {
            val settings = FakeSettingsRepository()
            val logs = mutableListOf<String>()
            assertEquals(ManualCheckResult.Failed, checker(http, settings, logs = logs).checkNow())
            assertNull(settings.release.value)
            assertEquals(now.toEpochMilli(), settings.lastChecked.value)
            assertEquals(1, logs.size)
        }
    }

    @Test
    fun isDue() {
        val t = now.toEpochMilli()
        assertTrue(UpdateChecker.isDue(null, t))
        assertFalse(UpdateChecker.isDue(t, t))
        assertFalse(UpdateChecker.isDue(t - hour + 1, t))
        assertTrue(UpdateChecker.isDue(t - hour, t))
        // 時計の巻き戻りで前回の確認日時が未来になっている場合も確認する
        assertTrue(UpdateChecker.isDue(t + hour, t))
    }

    @Test
    fun shouldShowBanner() {
        // 新しいバージョンがあり、閉じていない
        assertTrue(UpdateChecker.shouldShowBanner("0.1.4", "0.1.3", null))
        // 保存されていない・現在と同じ・古い（アップデート済み）
        assertFalse(UpdateChecker.shouldShowBanner(null, "0.1.3", null))
        assertFalse(UpdateChecker.shouldShowBanner("0.1.3", "0.1.3", null))
        assertFalse(UpdateChecker.shouldShowBanner("0.1.4", "0.1.10", null))
        // そのバージョンを閉じた
        assertFalse(UpdateChecker.shouldShowBanner("0.1.4", "0.1.3", "0.1.4"))
        // 閉じたあとに、さらに新しいバージョンが出た
        assertTrue(UpdateChecker.shouldShowBanner("0.1.10", "0.1.3", "0.1.9"))
    }
}
