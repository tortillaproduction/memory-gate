package app.memorygate.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Room のマイグレーションを JVM 上の SQLite（sqlite-jdbc）で検証する。
 * 書き出したスキーマ（app/schemas）の v1 で DB を作り、実際の Migration を流して、
 * v2 のスキーマと一致すること・既存データが残っていることを確認する。
 */
class MigrationTest {

    private lateinit var jdbc: Connection

    @Before
    fun setUp() {
        jdbc = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun tearDown() {
        jdbc.close()
    }

    @Test
    fun migrate1To2_keepsDataAndMatchesSchema() {
        createFromSchema(1)
        jdbc.createStatement().use {
            it.executeUpdate(
                "INSERT INTO targets (id, title, type, url, packageName, scheduleType, intervalDays, dayOfWeek, lastVisitedAt, createdAt) " +
                    "VALUES (1, '確定申告', 'URL', 'https://example.com', NULL, 'EVERY_N_DAYS', 3, NULL, 1700000000000, 1690000000000)",
            )
            it.executeUpdate(
                "INSERT INTO targets (id, title, type, url, packageName, scheduleType, intervalDays, dayOfWeek, lastVisitedAt, createdAt) " +
                    "VALUES (2, 'Duolingo', 'APP', NULL, 'com.duolingo', 'WEEKLY', NULL, 1, NULL, 1690000001000)",
            )
            it.executeUpdate("INSERT INTO guarded_apps (packageName) VALUES ('com.twitter.android')")
        }

        runBlocking { Migrations.MIGRATION_1_2.migrate(JdbcSQLiteConnection(jdbc)) }

        // 列の構成が v2 のスキーマから作ったテーブルと一致する
        assertEquals(columnsOfFreshSchema(2, "targets"), columns("targets"))
        assertEquals(columnsOfFreshSchema(2, "guarded_apps"), columns("guarded_apps"))

        // 既存のデータが残り、追加した列は既定値になっている
        jdbc.createStatement().use { st ->
            st.executeQuery("SELECT * FROM targets ORDER BY id").use { rs ->
                assertTrue(rs.next())
                assertEquals("確定申告", rs.getString("title"))
                assertEquals("https://example.com", rs.getString("url"))
                assertEquals(3, rs.getInt("intervalDays"))
                assertEquals(1700000000000L, rs.getLong("lastVisitedAt"))
                assertEquals(1690000000000L, rs.getLong("createdAt"))
                assertEquals(0, rs.getInt("snoozeEnabled"))
                for (column in listOf("snoozeIntervalMinutes", "snoozeStartMinutes", "snoozeEndMinutes", "snoozeImagePath", "lastSnoozeShownAt")) {
                    rs.getObject(column)
                    assertTrue(column, rs.wasNull())
                }
                assertTrue(rs.next())
                assertEquals("Duolingo", rs.getString("title"))
                assertEquals("com.duolingo", rs.getString("packageName"))
                assertEquals(1, rs.getInt("dayOfWeek"))
                assertFalse(rs.next())
            }
            st.executeQuery("SELECT packageName FROM guarded_apps").use { rs ->
                assertTrue(rs.next())
                assertEquals("com.twitter.android", rs.getString(1))
            }
        }
    }

    @Test
    fun allMigrationsCoverEveryVersion() {
        val latest = schema(latestSchemaVersion()).getJSONObject("database").getInt("version")
        var version = 1
        for (migration in Migrations.ALL.sortedBy { it.startVersion }) {
            assertEquals(version, migration.startVersion)
            version = migration.endVersion
        }
        assertEquals(latest, version)
    }

    // ---- helpers ----

    private data class Column(val name: String, val type: String, val notNull: Boolean, val default: String?, val pk: Int)

    private val schemaDir = File("schemas/app.memorygate.data.db.AppDatabase")

    private fun latestSchemaVersion(): Int = schemaDir.listFiles()!!.maxOf { it.nameWithoutExtension.toInt() }

    private fun schema(version: Int) = JSONObject(File(schemaDir, "$version.json").readText())

    private fun createFromSchema(version: Int, connection: Connection = jdbc) {
        val entities = schema(version).getJSONObject("database").getJSONArray("entities")
        connection.createStatement().use { st ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                st.executeUpdate(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
        }
    }

    private fun columns(table: String, connection: Connection = jdbc): List<Column> =
        connection.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(Column(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value"), rs.getInt("pk")))
                    }
                }.sortedBy { it.name }
            }
        }

    private fun columnsOfFreshSchema(version: Int, table: String): List<Column> =
        DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
            createFromSchema(version, fresh)
            columns(table, fresh)
        }

    /** Migration が使う execSQL（prepare → step）だけを JDBC で実装したアダプター */
    private class JdbcSQLiteConnection(private val connection: Connection) : SQLiteConnection {
        override fun prepare(sql: String): SQLiteStatement = ExecOnlyStatement(connection, sql)
        override fun close() = Unit
    }

    private class ExecOnlyStatement(private val connection: Connection, private val sql: String) : SQLiteStatement {
        override fun step(): Boolean {
            connection.createStatement().use { it.execute(sql) }
            return false
        }
        override fun close() = Unit
        override fun reset() = Unit
        override fun clearBindings() = Unit
        override fun bindBlob(index: Int, value: ByteArray) = unsupported()
        override fun bindDouble(index: Int, value: Double) = unsupported()
        override fun bindLong(index: Int, value: Long) = unsupported()
        override fun bindText(index: Int, value: String) = unsupported()
        override fun bindNull(index: Int) = unsupported()
        override fun getBlob(index: Int): ByteArray = unsupported()
        override fun getDouble(index: Int): Double = unsupported()
        override fun getLong(index: Int): Long = unsupported()
        override fun getText(index: Int): String = unsupported()
        override fun isNull(index: Int): Boolean = unsupported()
        override fun getColumnCount(): Int = unsupported()
        override fun getColumnName(index: Int): String = unsupported()
        override fun getColumnType(index: Int): Int = unsupported()
        private fun unsupported(): Nothing = throw UnsupportedOperationException()
    }
}
