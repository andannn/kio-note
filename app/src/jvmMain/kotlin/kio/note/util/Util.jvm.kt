package kio.note.util

import kio.async.AsyncRawSink
import kio.async.AsyncRawSource
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

internal actual suspend fun removeFile(path: String) {
    SystemFileSystem.delete(path = Path(path), mustExist = true)
}

internal actual suspend fun fileSource(path: String): AsyncRawSource {
    val source = SystemFileSystem.source(Path(path))
    return object : AsyncRawSource {
        override suspend fun close() {
            source.close()
        }

        override suspend fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            return source.readAtMostTo(sink, byteCount)
        }
    }
}