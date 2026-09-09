package kio.note.database

import kio.async.PollerFactory
import kio.async.poller.select.Select

class JvmDatabaseTest: DatabaseTest() {
    override val pollerFactory: PollerFactory = Select
}