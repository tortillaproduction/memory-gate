package app.memorygate.apps

import android.content.pm.ApplicationInfo

/**
 * 監視対象アプリとして選択できないアプリの判定（SPEC 6 章）。除外判定はここに集約する。
 */
object ExcludedApps {

    /** 常に除外する固定リスト */
    val FIXED: Set<String> = setOf(
        "jp.naver.line.android",
        "com.android.settings",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.samsung.android.dialer",
        "com.samsung.android.messaging",
    )

    /** メールアプリの固定リスト（`mailto:` で見つからない場合に備える） */
    val EMAIL_FIXED: Set<String> = setOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "jp.co.yahoo.android.ymail",
        "com.samsung.android.email.provider",
        "com.android.email",
    )

    /** OS 基盤アプリ。システムアプリとして扱う（「システムアプリも表示」で表示される） */
    val PLATFORM_FIXED: Set<String> = setOf(
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.googlequicksearchbox",
    )

    /**
     * プリインストールのまま更新されていないシステムアプリか。
     * `FLAG_SYSTEM` があり、かつ `FLAG_UPDATED_SYSTEM_APP` がないもの（時計・電卓・カメラ・メーカー独自ツールなど）。
     * ストア経由で更新されたプリインストールアプリ（YouTube など）は対象外。
     */
    fun isPreinstalledSystemApp(flags: Int?): Boolean {
        if (flags == null) return false
        val system = flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updated = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return system && !updated
    }

    /**
     * 監視対象アプリの選択で除外するパッケージ。
     *
     * @param candidates 一覧に出す候補（ランチャーに表示されるアプリ）。システムアプリかどうかはこの中から判定する
     */
    fun forGuardSelection(source: PackageInfoSource, candidates: Collection<String>): GuardExclusion {
        val always = buildSet {
            add(source.ownPackage)
            source.defaultDialerPackage()?.let(::add)
            source.defaultSmsPackage()?.let(::add)
            addAll(source.homePackages())
            addAll(source.imePackages())
            addAll(FIXED)
            addAll(source.emailPackages())
            addAll(EMAIL_FIXED)
        }
        val system = buildSet {
            addAll(PLATFORM_FIXED)
            candidates.filterTo(this) { isPreinstalledSystemApp(source.applicationFlags(it)) }
        } - always
        return GuardExclusion(alwaysExcluded = always, systemApps = system)
    }

    /** 誘導先（type=APP）の選択では自アプリだけを除外する */
    fun forTargetSelection(ownPackage: String): Set<String> = setOf(ownPackage)
}

/**
 * 監視対象アプリの選択での除外結果。
 *
 * @property alwaysExcluded 常に表示しないアプリ（自アプリ・電話・SMS・ホーム・IME・固定リスト・メールアプリ）
 * @property systemApps 「システムアプリも表示」がオンのときだけ表示するアプリ
 */
data class GuardExclusion(
    val alwaysExcluded: Set<String>,
    val systemApps: Set<String>,
) {
    fun excluded(showSystemApps: Boolean): Set<String> =
        if (showSystemApps) alwaysExcluded else alwaysExcluded + systemApps

    /**
     * 一覧に表示するか。すでに監視対象に登録済みのアプリは、選択を外せるように除外対象でも表示する。
     */
    fun isVisible(packageName: String, showSystemApps: Boolean, guarded: Set<String>): Boolean =
        packageName in guarded || packageName !in excluded(showSystemApps)
}
