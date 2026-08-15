import kio.note.db.migrations
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate
import kio.postgres.conn.openPgConnection

suspend fun doMigration(): Boolean {
    val conn = openPgConnection(
        getEnv("POSTGRES_HOST") ?: error("POSTGRES_HOST not provide"),
        getEnv("POSTGRES_PORT")?.toIntOrNull() ?: error("POSTGRES_PORT not provide"),
        getEnv("POSTGRES_USER") ?: error("POSTGRES_USER not provide"),
        getEnv("POSTGRES_PASSWORD") ?: error("POSTGRES_PASSWORD not provide"),
        getEnv("POSTGRES_DB") ?: error("POSTGRES_DB not provide"),
    )

    val result = conn.migrate(migrations)

    conn.close()

    if (result is MigrationResult.Error) {
        println("Migration Failed, reason: $result")
    }

    return result is MigrationResult.Success
}
