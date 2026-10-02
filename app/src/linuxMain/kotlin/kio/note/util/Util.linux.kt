package kio.note.util

import kio.async.AsyncRawSink
import kio.async.io.asyncRawSink
import kio.async.open
import kio.async.poller
import kio.async.unlink
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.IOException
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun fileSink(path: String): AsyncRawSink {
    val io = currentCoroutineContext().poller.io
    val fd = io.open(path, O_WRONLY or O_CREAT or O_TRUNC or O_CLOEXEC, 0x1A4.toUInt())

    if (fd < 0) {
        throw IOException("open failed for $path: " + (strerror(fd)?.toKString() ?: "errno=$fd"))
    }

    return io.asyncRawSink(fd)
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun removeFile(path: String) {
    val io = currentCoroutineContext().poller.io
    val result = io.unlink(path, 0)

    if (result < 0) {
        val errorCode = -result
        val message = strerror(errorCode)?.toKString()
            ?: "errno=$errorCode"

        throw IOException("unlink failed for $path: $message")
    }
}