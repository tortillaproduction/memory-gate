package app.memorygate.update

/**
 * major.minor.patch 形式のバージョン。文字列ではなく数値で比較する（0.1.10 > 0.1.9）。
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[-+].*)?$""")

        /**
         * `0.1.3`・`v0.1.3` などを解析する。minor / patch が省略されていれば 0 とみなし、
         * `-beta` などの後ろの付加情報は無視する。解析できなければ null。
         */
        fun parse(text: String?): AppVersion? {
            val match = PATTERN.matchEntire(text?.trim() ?: return null) ?: return null
            val (major, minor, patch) = match.destructured
            return try {
                AppVersion(major.toInt(), minor.ifEmpty { "0" }.toInt(), patch.ifEmpty { "0" }.toInt())
            } catch (_: NumberFormatException) {
                null
            }
        }

        /** `candidate` が `current` より新しいか。どちらかが解析できなければ false */
        fun isNewer(candidate: String?, current: String?): Boolean {
            val c = parse(candidate) ?: return false
            val base = parse(current) ?: return false
            return c > base
        }
    }
}
