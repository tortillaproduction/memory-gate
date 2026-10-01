package app.memorygate.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {

    @Test
    fun parsesTagNameAndHtmlUrl() {
        val json = """
            {
              "url": "https://api.github.com/repos/tortillaproduction/memory-gate/releases/1",
              "html_url": "https://github.com/tortillaproduction/memory-gate/releases/tag/v0.1.10",
              "tag_name": "v0.1.10",
              "name": "Memory Gate v0.1.10",
              "draft": false,
              "prerelease": false,
              "assets": []
            }
        """.trimIndent()
        assertEquals(
            LatestRelease("0.1.10", "https://github.com/tortillaproduction/memory-gate/releases/tag/v0.1.10"),
            ReleaseParser.parse(json),
        )
    }

    @Test
    fun tagWithoutVPrefix() {
        val json = """{"tag_name":"0.2.0","html_url":"https://github.com/x/y/releases/tag/0.2.0"}"""
        assertEquals("0.2.0", ReleaseParser.parse(json)?.version)
    }

    @Test
    fun returnsNullForInvalidResponses() {
        // JSON でない
        assertNull(ReleaseParser.parse("<html>rate limited</html>"))
        assertNull(ReleaseParser.parse(""))
        // エラーレスポンス（レート制限・リリースなし）
        assertNull(ReleaseParser.parse("""{"message":"API rate limit exceeded","documentation_url":"https://docs.github.com"}"""))
        assertNull(ReleaseParser.parse("""{"message":"Not Found"}"""))
        // tag_name がバージョンでない
        assertNull(ReleaseParser.parse("""{"tag_name":"latest","html_url":"https://github.com/x/y/releases/tag/latest"}"""))
        // html_url がない・http(s) でない
        assertNull(ReleaseParser.parse("""{"tag_name":"v0.1.3"}"""))
        assertNull(ReleaseParser.parse("""{"tag_name":"v0.1.3","html_url":"javascript:alert(1)"}"""))
        // 配列
        assertNull(ReleaseParser.parse("""[{"tag_name":"v0.1.3"}]"""))
    }
}
