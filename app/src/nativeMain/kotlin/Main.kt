@file:OptIn(ExperimentalForeignApi::class)

import kio.async.bind
import kio.async.close
import kio.async.io.serverSocket
import kio.async.listen
import kio.async.poller
import kio.async.poller.uring.LinuxUring
import kio.async.runPollEventLoop
import kio.async.socket
import kio.note.noteApp
import kotlinx.cinterop.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.IOException
import platform.linux.inet_addr
import platform.posix.*

const val HOST_IP = "0.0.0.0"
const val PORT = 8080

fun main() = runPollEventLoop(LinuxUring()) {
    signal(SIGPIPE, SIG_IGN)

    val io = currentCoroutineContext().poller.io
    val host = HOST_IP
    val port = PORT

    val serverFd = io.socket(AF_INET, SOCK_STREAM or SOCK_NONBLOCK, 0)
    if (serverFd < 0) {
        io.close(serverFd)
        return@runPollEventLoop
    }

    val ret = memScoped {
        val yes = alloc<IntVar> { value = 1 }

        setsockopt(serverFd, SOL_SOCKET, SO_REUSEADDR, yes.ptr, sizeOf<IntVar>().convert()).let {
            if (it < 0) return@memScoped it
        }

        val ip = inet_addr(host)
        if (ip == INADDR_NONE) {
            throw IOException("invalid IPv4 address: $host")
        }

        val serverAddr = alloc<sockaddr_in> {
            sin_family = AF_INET.convert()
            sin_port = htons(port.convert())
            sin_addr.s_addr = ip
        }

        io.bind(serverFd, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()).let {
            if (it < 0) return@memScoped it
        }
        io.listen(serverFd, 128).let {
            if (it < 0) return@memScoped it
        }

        println("Listening to $host:$port")

        val serverSocket = io.serverSocket(serverFd)

        noteApp(serverSocket)

        0
    }

    if (ret < 0) {
        println("some error happened: ${strerror(-serverFd)?.toKString()}")
    }

    io.close(serverFd)
}
