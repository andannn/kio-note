package kio.note.database

import kio.postgres.conn.PgConnection
import kio.postgres.conn.exec

suspend fun PgConnection.doSelectQuery() = exec("Select 1")
