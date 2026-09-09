package kio.note.util

import kio.async.AsyncRawSink
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual suspend fun fileSink(path: String): AsyncRawSink {
    val sink = SystemFileSystem.sink(Path(path))
    return object : AsyncRawSink {
        override suspend fun write(source: Buffer, byteCount: Long) {
            sink.write(source, byteCount)
        }

        override suspend fun flush() {
            sink.flush()
        }

        override suspend fun close() {
            sink.close()
        }
    }
}