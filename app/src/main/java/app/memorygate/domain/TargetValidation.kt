package app.memorygate.domain

import java.net.URI
import java.net.URISyntaxException

object TargetValidation {

    /** `http://` / `https://` のみ許可し、ホスト名があること */
    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: URISyntaxException) {
            false
        }
    }
}
