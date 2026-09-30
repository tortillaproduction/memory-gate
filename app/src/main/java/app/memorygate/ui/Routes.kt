package app.memorygate.ui

/** Navigation Compose のルート */
object Routes {
    const val HOME = "home"
    const val GUARDED_APPS = "guarded_apps"
    const val TARGET_EDIT = "target/{targetId}"

    /** id = 0 は新規作成 */
    fun targetEdit(id: Long) = "target/$id"
}
