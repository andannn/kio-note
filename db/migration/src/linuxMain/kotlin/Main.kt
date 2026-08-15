import kio.async.poller.uring.LinuxUring
import kio.async.runPollEventLoop
import kotlin.system.exitProcess

fun main(): Unit = runPollEventLoop(LinuxUring) {
    val success = doMigration()
    if (!success) exitProcess(1)
}