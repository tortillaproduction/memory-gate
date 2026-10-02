package app.memorygate.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * DB のマイグレーション。既存のデータを保持したまま列を追加する。
 * スキーマは app/schemas に書き出しており、MigrationTest で検証する。
 */
object Migrations {

    /** v1 → v2: 誘導先にスヌーズの設定と最終表示日時を追加する（v0.1.4） */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            SQL_1_2.forEach { connection.execSQL(it) }
        }
    }

    internal val SQL_1_2 = listOf(
        "ALTER TABLE targets ADD COLUMN snoozeEnabled INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE targets ADD COLUMN snoozeIntervalMinutes INTEGER",
        "ALTER TABLE targets ADD COLUMN snoozeStartMinutes INTEGER",
        "ALTER TABLE targets ADD COLUMN snoozeEndMinutes INTEGER",
        "ALTER TABLE targets ADD COLUMN snoozeImagePath TEXT",
        "ALTER TABLE targets ADD COLUMN lastSnoozeShownAt INTEGER",
    )

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
