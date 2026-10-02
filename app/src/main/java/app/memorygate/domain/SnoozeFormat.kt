package app.memorygate.domain

/** スヌーズ設定の表示用の文言 */
object SnoozeFormat {

    /** 「1分おき」「5分おき」「30分おき」「1時間おき」 */
    fun interval(minutes: Int): String = when {
        minutes % 60 == 0 -> "${minutes / 60}時間おき"
        else -> "${minutes}分おき"
    }

    /** 0:00 からの分数を「9:00」の形式にする */
    fun time(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

    /** 時間帯の補足（日付をまたぐ・24 時間） */
    fun windowNote(start: Int, end: Int): String = when {
        start == end -> "開始と終了が同じときは 24 時間ずっと知らせます"
        start > end -> "日付をまたぎます（${time(start)}〜翌${time(end)}）"
        else -> "${time(start)}〜${time(end)}の間に知らせます"
    }
}
