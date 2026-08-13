package kio.note

import kio.async.io.ServerSocket
import kio.http.Route
import kio.http.currentLoggingBackend
import kio.http.get
import kio.http.httpServer
import kio.http.newLogger
import kio.http.staticResource
import kio.note.database.initDb
import kio.note.domain.MockRepositoryImpl
import kio.note.domain.Repository
import kio.note.page.noteMainPage
import kio.note.util.Env
import kio.note.util.getEnv
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.openPgConnection
import kio.postgres.conn.useConnection
import kio.tls.pem
import kio.tls.withServerTls

suspend fun noteServer(serverSocket: ServerSocket) {
    val env = loadEnv()

    setupServer(isMock = false, serverSocket, env) { repo ->
        with(repo) {
            get("/") { call -> call.noteMainPage() }
            notesRoute()
            staticResource("/attachments", "data/uploads")
            staticResource("/", "resource")
        }
    }
}

private suspend fun setupServer(
    isMock: Boolean, serverSocket: ServerSocket, env: Env,
    block: suspend Route.(repo: Repository) -> Unit
) {
    if (isMock) {
        httpServer(
            serverSocket = serverSocket,
        ) {
            val logger = currentLoggingBackend().newLogger("Repository")
            val repo = MockRepositoryImpl(logger)
            block(repo)
        }
    } else {
        val pgPool = createPgPool(env)
        pgPool.useConnection { it.initDb() }

        httpServer(
            serverSocket = serverSocket,
            connectionWrapper = {
                withServerTls(
                    env.tlsCert.pem,
                    env.tlsKey.pem,
                    supportAlpnProtocols = listOf("h2", "http/1.1")
                )
            },
        ) {
            val logger = currentLoggingBackend().newLogger("Repository")
            val repo = Repository(pgPool, logger)
            block(repo)
        }
    }
}

private fun loadEnv() = Env(
    postgresHost = getEnv("POSTGRES_HOST") ?: "127.0.0.1",
    postgresPort = getEnv("POSTGRES_PORT")?.toIntOrNull() ?: 5432,
    postgresUser = getEnv("POSTGRES_USER") ?: "postgres",
    postgresPassword = getEnv("POSTGRES_PASSWORD") ?: "",
    postgresDatabase = getEnv("POSTGRES_DB") ?: "knote",
    tlsCert = getEnv("TLS_CERT") ?: "",
    tlsKey = getEnv("TLS_KEY") ?: "",
)

private fun createPgPool(env: Env): PgConnectionPool {
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
