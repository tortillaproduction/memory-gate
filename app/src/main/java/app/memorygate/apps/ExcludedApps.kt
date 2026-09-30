package app.memorygate.apps

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

    /**
     * 端末の状態から求めた、監視対象として選択できないパッケージの集合。
     *
     * @param ownPackage 自アプリ
     * @param defaultDialer 既定の電話アプリ
     * @param defaultSms 既定の SMS アプリ
     * @param homePackages ホームアプリ（`CATEGORY_HOME` を解決したもの）
     * @param imePackages 有効な IME
     */
    fun forGuardSelection(
        ownPackage: String,
        defaultDialer: String?,
        defaultSms: String?,
        homePackages: Collection<String>,
        imePackages: Collection<String>,
    ): Set<String> = buildSet {
        add(ownPackage)
        defaultDialer?.let(::add)
        defaultSms?.let(::add)
        addAll(homePackages)
        addAll(imePackages)
        addAll(FIXED)
    }

    /** 誘導先（type=APP）の選択では自アプリだけを除外する */
    fun forTargetSelection(ownPackage: String): Set<String> = setOf(ownPackage)
}
