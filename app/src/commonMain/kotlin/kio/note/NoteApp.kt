package kio.note

import io.ktor.http.HttpHeaders
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

private val DATA_SOURCE = DataSourceType.MOCK

suspend fun CoroutineScope.noteApp(serverSocket: ServerSocket) {
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

                    staticResource(
                        remotePath = "/attachments",
                        basePackage = "data/uploads",
                        configHeaders = {
                            append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                        }
                    )
                }

                staticResource(
                    remotePath = "/",
                    basePackage = "resource",
                    configHeaders = {
                        append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                    }
                )
            }
        }
    }
}

private enum class DataSourceType {
    MOCK,
    LOCAL_DB,
    PRODUCTION
}

private suspend fun CoroutineScope.setupServer(
    dataSource: DataSourceType,
    serverSocket: ServerSocket, env: Env,
    block: suspend Route.(repo: Repository) -> Unit
) {
    when (dataSource) {
        DataSourceType.MOCK -> {
            httpServer(
                serverSocket = serverSocket,
            ) {
                val repo = MockRepositoryImpl()
                block(repo)
            }
        }

        DataSourceType.LOCAL_DB -> {
            httpServer(serverSocket = serverSocket) {
                val repo = Repository(createPgPool(env))
                block(repo)
            }
        }

        DataSourceType.PRODUCTION -> {
            httpServer(
                serverSocket = serverSocket,
                loggingBackEnd = AsyncLoggingBackEnd(this, enableTrace = true),
                connectionWrapper = {
                    withServerTls(
                        env.tlsCert.pem,
                        env.tlsKey.pem,
                        supportAlpnProtocols = listOf("h2", "http/1.1")
                    )
                },
            ) {
                val repo = Repository(createPgPool(env))
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
