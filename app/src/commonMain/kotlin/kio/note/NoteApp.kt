package kio.note

import kio.async.io.ServerSocket
import kio.async.io.getEnv
import kio.http.*
import kio.note.domain.MockRepositoryImpl
import kio.note.domain.Repository
import kio.note.page.noteMainPage
import kio.note.util.Env
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.openPgConnection
import kio.tls.pem
import kio.tls.withServerTls
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

private val DATA_SOURCE = DataSourceType.PRODUCTION

suspend fun noteApp(serverSocket: ServerSocket) {
    val env = loadEnv()

    setupServer(dataSource = DATA_SOURCE, serverSocket, env) { repo ->
        context(repo) {
            inject(
                CallId { Uuid.random().toString() },
                LoggerInterceptor()
            ) {
                notesLogin()

                inject(AuthSession()) {
                    get("/") { call -> call.noteMainPage() }
                    notesRoute()
                }

                staticResource("/attachments", "data/uploads")
                staticResource("/", "resource")
            }
        }
    }
}

private enum class DataSourceType {
    MOCK,
    LOCAL_DB,
    PRODUCTION
}

private suspend fun setupServer(
    dataSource: DataSourceType,
    serverSocket: ServerSocket, env: Env,
    block: suspend Route.(repo: Repository) -> Unit
) {
    when (dataSource) {
        DataSourceType.MOCK -> {
            httpServer(
                serverSocket = serverSocket,
            ) {
                val logger = currentLoggingBackend().newLogger("Repository")
                val repo = MockRepositoryImpl(logger)
                block(repo)
            }
        }

        DataSourceType.LOCAL_DB -> {
            httpServer(serverSocket = serverSocket) {
                val logger = currentLoggingBackend().newLogger("Repository")
                val repo = Repository(createPgPool(env), logger)
                block(repo)
            }
        }

        DataSourceType.PRODUCTION -> {
            val pgPool = createPgPool(env)
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

private fun LoggerInterceptor(): CallInterceptor = CallInterceptor { context, proceed ->
    val newLogger = currentLoggingBackend().newLogger("Call", mapOf("CallId" to currentCallId()))
    withContext(CoroutineLogger(newLogger)) {
        proceed(context)
    }
}
