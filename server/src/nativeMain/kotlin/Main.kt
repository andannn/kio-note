@file:OptIn(ExperimentalForeignApi::class)

import kio.async.io.tcpBind
import kio.async.poller.uring.LinuxUring
import kio.async.runPollEventLoop
import kio.note.noteServer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.signal

const val HOST_IP = "127.0.0.1"
const val PORT = 7878

fun main() = runPollEventLoop(LinuxUring) {
    signal(SIGPIPE, SIG_IGN)

    val serverSocket = tcpBind(HOST_IP, PORT)
    println("INFO: server (${serverSocket}) is listening to , $HOST_IP, $PORT")

    noteServer(serverSocket)
}