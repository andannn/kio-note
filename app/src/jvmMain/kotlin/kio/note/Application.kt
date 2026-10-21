package kio.note

import kio.async.io.serverSocket
import kio.async.poller
import kio.async.poller.select.Select
import kio.async.runPollEventLoop
import kotlinx.coroutines.currentCoroutineContext
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.ServerSocketChannel

const val HOST_IP = "127.0.0.1"
const val PORT = 7878

fun main(): Unit = runPollEventLoop(Select) {
    val io = currentCoroutineContext().poller.io
    val backlog = 128
    val channel = ServerSocketChannel.open()

    try {
        channel.configureBlocking(false)

        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true)

        println("INFO: Listening to $HOST_IP:$PORT")

        channel.bind(
            InetSocketAddress(HOST_IP, PORT),
            backlog,
        )

        val serverSocket = io.serverSocket(channel)

        noteApp(serverSocket)
    } finally {
        channel.close()
    }
}
