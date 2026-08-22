package kio.note.util

import kio.async.AsyncRawSource
import kio.async.buffered
import kio.async.io.openFileSink
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.kotlincrypto.hash.sha2.SHA256

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

fun hashPassword(password: String): String {
    return sha256(password.encodeToByteArray()).toHexString()
}

internal fun sha256(data: ByteArray): ByteArray {
    val sha256 = SHA256()
    sha256.update(data)
    return sha256.digest()
}
