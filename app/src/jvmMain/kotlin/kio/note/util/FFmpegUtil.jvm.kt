package kio.note.util

import kio.async.AsyncSink
import kio.async.AsyncSource
import kio.async.SuspendIo
import kio.async.io.buffered
import kio.async.io.openInMemoryPipe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Mock implementation
actual suspend fun SuspendIo.compressImageSourceToWebp(
    doSendInput: suspend (AsyncSink) -> Unit,
    doTransferData: suspend (AsyncSource) -> Unit
): Result<Unit> = coroutineScope {
    val pipeConn =openInMemoryPipe().buffered()
    launch { doSendInput(pipeConn.sink) }
    doTransferData(pipeConn.source)
    Result.success(Unit)
}