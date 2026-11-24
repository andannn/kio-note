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
): Result<Unit> = runCatching {
    coroutineScope {
        val pipeConn = openInMemoryPipe().buffered()
        val writeJob = launch {
            try {
                doSendInput(pipeConn.sink)
            } finally {
                pipeConn.sink.flush()
                pipeConn.sink.close()
            }
        }

        try {
            doTransferData(pipeConn.source)
        } finally {
            pipeConn.source.close()
        }

        writeJob.join()
    }
}