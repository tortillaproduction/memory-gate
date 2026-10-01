package app.memorygate.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

/** 更新確認の通信部分。ユニットテストで差し替える */
fun interface HttpClient {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/** `HttpURLConnection` による実装。呼び出し側で IO ディスパッチャ上から呼ぶこと */
class UrlConnectionHttpClient(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000,
) : HttpClient {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
