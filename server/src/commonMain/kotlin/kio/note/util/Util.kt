package kio.note.util

import kio.async.AsyncRawSource
import kio.async.buffered
import kio.async.io.openFileSink
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

expect fun getEnv(key: String): String?

suspend fun AsyncRawSource.saveFileToPath(path: String) {
    val filePath = Path(path)

    filePath.parent?.let { parent ->
        SystemFileSystem.createDirectories(parent)
    }

    val sink = openFileSink(path).buffered()
    sink.transferFrom(this)
    sink.flush()
    sink.close()
}