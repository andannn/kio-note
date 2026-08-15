import kio.async.poller.select.Select
import kio.async.runPollEventLoop
import kotlin.system.exitProcess

fun main() = runPollEventLoop(Select) {
    val success = doMigration()
    if (!success) exitProcess(1)
}