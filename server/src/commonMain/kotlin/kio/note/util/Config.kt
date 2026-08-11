package kio.note.util

object Config {
    const val APP_CSS = "/static/css/app.css"
    const val APP_JS = "/static/app.js"
    const val HTMX_MIN_JS = "/static/htmx.min.js"
    const val UPLOAD_DIR = "./data/uploads/"
}

class Env(
    val postgresHost: String,
    val postgresPort: Int,
    val postgresUser: String,
    val postgresPassword: String,
    val postgresDatabase: String,
    val tlsCert: String,
    val tlsKey: String,
)