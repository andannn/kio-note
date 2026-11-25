package kio.note.util

import kio.async.*
import kio.async.io.asyncRawSink
import kio.async.io.asyncRawSource
import kio.async.io.exitStatus
import kio.async.io.posixSpawn
import kotlinx.cinterop.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual suspend fun SuspendIo.compressImageSourceToWebp(
    doSendInput: suspend (AsyncSink) -> Unit,
    doTransferData: suspend (AsyncSource) -> Unit,
): Result<Unit> = coroutineScope {
    prepareFfmpegCommand().mapCatching { (pid, sink, source) ->
        val writeJob = launch {
            try {
                doSendInput(sink)
            } finally {
                sink.flush()
                sink.close()
            }
        }

        try {
            doTransferData(source)
        } catch (e: Throwable) {
            writeJob.cancel()
            throw e
        } finally {
            source.close()
        }

        writeJob.join()

        memScoped {
            val info = alloc<siginfo_t>()

            val rc = waitid(idtype_t.P_PID, pid.toUInt(), info.ptr, WEXITED)
            if (rc != 0) throw IOException("waitid failed: ${strerror(rc)?.toKString()}")
            if (info.exitStatus != 0) throw IOException("ffmpeg exited with non-zero status: ${info.exitStatus}")
        }
    }
}

private data class FfmpegCommand(
    val pid: Int,
    val sink: AsyncSink,
    val source: AsyncSource,
)

private suspend fun SuspendIo.prepareFfmpegCommand(): Result<FfmpegCommand> {
    val (stdinRead, stdinWrite) = openPipe()
    val (stdoutRead, stdoutWrite) = openPipe()

    // 核心 ffmpeg 命令及参数
    val args = listOf(
        "-v", "error",            // 只输出致命错误日志，避免污染管道
        "-nostdin",               // 避免交互式控制台挂起
        "-i", "pipe:0",           // 从标准输入读取待清洗图片流 (stdin)
        "-map_metadata", "-1",    // 清洗脱敏：彻底剥离 GPS、相机型号等全部 EXIF 隐私
        "-vcodec", "libwebp",     // 转码为现代 WebP 格式
        "-compression_level", "4",// 压缩率与 CPU 开销的黄金平衡点 (0-6)
        "-q:v", "75",             // 视觉无损质量级别 (8MB -> ~200KB)
        "-vf", "scale='min(2048,iw)':'min(2048,ih)':force_original_aspect_ratio=decrease", // 限制最大边不超 2048px，防像素爆炸
        "-f", "image2",           // 输出到 pipe 时必须显式声明封装格式为单张图片流
        "pipe:1"                  // 从标准输出写出 WebP 字节流 (stdout)
    )

    val pid = posixSpawn("ffmpeg", args) {
        dup2(stdinRead, STDIN_FILENO)
        dup2(stdoutWrite, STDOUT_FILENO)
        close(stdinWrite)
        close(stdoutRead)
    }

    close(stdinRead)
    close(stdoutWrite)

    if (pid < 0) {
        close(stdinWrite)
        close(stdoutRead)

        return Result.failure(IOException("spawn FFmpeg command failed."))
    }

    val sink = asyncRawSink(stdinWrite).buffered()
    val source = asyncRawSource(stdoutRead).buffered()

    return Result.success(FfmpegCommand(pid, sink, source))
}
@OptIn(ExperimentalForeignApi::class)
private suspend fun SuspendIo.openPipe(): Pair<Int, Int> = memScoped {
    val fds = allocArray<IntVar>(2)
    check(pipe(fds) == 0)
    return@memScoped fds[0] to fds[1]
}