package kio.note.db

import kio.async.io.getEnv
import kio.async.poller.select.Select
import kio.postgres.conn.PgConnection
import kio.postgres.conn.openPgConnection
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kio.async.runPollEventLoop
import kio.postgres.conn.query
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate
import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.types.PgTimestampTz
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MigrationTest {

    @Test
    fun migrateFromVer1To2() = withTestPgDatabase {
        assertIs<MigrationResult.Success>(migrate(migrations, targetVersion = 1))
        exec(
            """
            insert into notes(title)
            values ('title A')
            returning id, title, create_at, update_at
        """.trimIndent()
        )

        assertIs<MigrationResult.Success>(migrate(migrations, targetVersion = 2))
        @Serializable
        data class NoteUserEntity(
            @SerialName("id")
            val id: PgInt8,
            @SerialName("username")
            val username: PgText,
            @SerialName("password_hash")
            val passwordHash: PgText,
            @SerialName("create_at")
            val createAt: PgTimestampTz,
        )
        query<NoteUserEntity>("select * from users").toList().also {
            assertEquals(1, it.size)
            assertEquals("preset", it.first().username)
        }

        @Serializable
        data class NewNotesEntity(
            @SerialName("id")
            val id: PgInt8,
            @SerialName("title")
            val title: PgText,
            @SerialName("create_at")
            val createAt: PgTimestampTz,
            @SerialName("update_at")
            val updateAt: PgTimestampTz,
            @SerialName("user_id")
            val userId: PgInt8,
        )
        query<NewNotesEntity>("""
            select * from notes
        """.trimIndent()).toList().also {
            assertEquals(1, it.size)
            assertEquals(1, it.first().userId)
        }
    }

    fun withTestPgDatabase(
        block: suspend PgConnection.() -> Unit
    ) = runPollEventLoop(Select) {
            val host = getEnv("POSTGRES_HOST") ?: "127.0.0.1"
            val port = getEnv("POSTGRES_PORT")?.toInt() ?: 5432
            val user = getEnv("POSTGRES_USER")  ?: error("no value found: POSTGRES_USER")
            val password = getEnv("POSTGRES_PASSWORD")  ?: error("no value found: POSTGRES_PASSWORD")
            val database = getEnv("POSTGRES_DB") ?: error("no value found: POSTGRES_DB")

            withTimeout(1.seconds) {
                val conn = openPgConnection(
                    host = host,
                    port = port,
                    user = user,
                    password = password,
                    database = database,
                )

                try {
                    conn.block()
                } finally {
                    conn.dropAllTables()
                }

                conn.close()
            }
        }
}