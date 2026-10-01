package app.memorygate.update

import app.memorygate.domain.TargetValidation
import org.json.JSONException
import org.json.JSONObject

/** GitHub の最新リリース */
data class LatestRelease(
    /** 先頭の `v` を除いたバージョン番号（例: `0.1.3`） */
    val version: String,
    /** リリースページの URL */
    val htmlUrl: String,
)

/** `GET /repos/{owner}/{repo}/releases/latest` のレスポンスの解析 */
object ReleaseParser {

    /** 解析できない場合（JSON が不正・`tag_name` がバージョンでない・`html_url` が http(s) でない）は null */
    fun parse(json: String): LatestRelease? {
        val obj = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val tagName = obj.optString("tag_name").trim()
        val htmlUrl = obj.optString("html_url").trim()
        val version = AppVersion.parse(tagName) ?: return null
        if (!TargetValidation.isValidUrl(htmlUrl)) return null
        return LatestRelease(version = version.toString(), htmlUrl = htmlUrl)
    }
}
