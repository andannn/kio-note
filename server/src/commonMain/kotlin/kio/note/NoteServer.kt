package kio.note

import kio.async.io.ServerSocket
import kio.http.get
import kio.http.httpServer
import kio.http.staticResource
import kio.note.domain.MockRepositoryImpl
import kio.note.domain.Repository
import kio.note.page.noteMainPage
import kio.note.util.Env
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.openPgConnection

suspend fun noteServer(serverSocket: ServerSocket) {
    val env = loadEnv()
    val pgPool = createPgPool(env)
    val repo = MockRepositoryImpl()
//    val repo = Repository(pgPool)
    with(repo) {
        httpServer(serverSocket) {
            get("/") { call -> call.noteMainPage() }
            notesRoute()
            staticResource("/attachments", "data/uploads")
            staticResource("/", "resource")
        }
    }
}

private fun loadEnv() = Env(
    postgresHost = System.getenv("POSTGRES_HOST") ?: "127.0.0.1",
    postgresPort = System.getenv("POSTGRES_PORT")?.toIntOrNull() ?: 5432,
    postgresUser = System.getenv("POSTGRES_USER") ?: "postgres",
    postgresPassword = System.getenv("POSTGRES_PASSWORD") ?: "",
    postgresDatabase = System.getenv("POSTGRES_DB") ?: "knote",
)

fun createPgPool(env: Env): PgConnectionPool {
    return PgConnectionPool(3) {
        openPgConnection(
            env.postgresHost,
            env.postgresPort,
            env.postgresUser,
            env.postgresPassword,
            env.postgresDatabase,
        )
    }
}
