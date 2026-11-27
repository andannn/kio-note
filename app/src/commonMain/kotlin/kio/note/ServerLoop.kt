package kio.note

import kio.async.io.AsyncConnection
import kio.async.io.AsyncRawConnection
import kio.async.io.ServerSocket
import kio.async.io.accept
import kio.async.io.asAsyncConnection
import kio.async.io.buffered
import kio.http.AsyncLoggingBackEnd
import kio.http.CoroutineLoggingBackend
import kio.http.Logger
import kio.http.LoggingBackEnd
import kio.http.Route
import kio.http.error
import kio.http.info
import kio.http.internal.http1.http1Connection
import kio.http.internal.http2.Settings
import kio.http.internal.http2.http2Connection
import kio.http.newLogger
import kio.http.trace
import kio.http.warn
import kio.tls.SslConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.EOFException
import kotlinx.io.IOException

suspend fun CoroutineScope.httpServer(
    serverSocket: ServerSocket,
    connectionWrapper: AsyncRawConnection.() -> AsyncConnection = { buffered() },
    loggingBackEnd: LoggingBackEnd = AsyncLoggingBackEnd(this),
    enableProtocolLogger: Boolean = false,
    block: suspend Route.() -> Unit
) {
    withContext(CoroutineLoggingBackend(loggingBackEnd)) {
        val logger = loggingBackEnd.newLogger("HttpServer")

        logger.trace("Configuring HTTP server")

        val route = Route()
        route.block()

        logger.info("HTTP server started")

        with(logger) {
            startHttpServer(
                route = route,
                serverSocket = serverSocket,
                enableProtocolLogger = enableProtocolLogger,
                connectionWrapper = connectionWrapper,
            )
        }

        logger.info("HTTP server stopped")
    }
}

context(logger: Logger)
private suspend fun CoroutineScope.startHttpServer(
    route: Route,
    serverSocket: ServerSocket,
    enableProtocolLogger: Boolean,
    connectionWrapper: AsyncRawConnection.() -> AsyncConnection,
) {
    while (true) {
        val raw = try {
            serverSocket.accept().asAsyncConnection()
        } catch (t: IOException) {
            logger.warn("Failed to accept connection", t)
            continue
        }

        logger.trace("Connection accepted")

        val conn = raw.connectionWrapper()
        launch {
            try {
                val sslConnection = conn as? SslConnection
                sslConnection?.let { ssl ->
                    logger.trace("TLS handshake started")
                    ssl.handShake()
                    val fields = mapOf("alpn" to ssl.getSelectedAlpn())
                    logger.trace("TLS handshake completed", fields)
                }

                val selectedAlpn = sslConnection?.getSelectedAlpn()
                when (selectedAlpn) {
                    "h2" -> route.http2Connection(
                        conn = conn,
                        enableProtocolLogger = enableProtocolLogger,
                        http2Settings = Settings().apply {
                            this[Settings.INITIAL_WINDOW_SIZE] = 4 * 1024 * 1024
                        }
                    )
                    else -> route.http1Connection(conn = conn)
                }
            } catch (_: EOFException) {
                logger.trace("connection closed by peer")
            } catch (e: IOException) {
                logger.trace("Connection I/O failed", mapOf("reason" to e.message))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Unexpected connection failure", t)
            } finally {
                conn.close()
                logger.trace("Connection closed")
            }
        }
    }
}
