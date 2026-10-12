package kio.note.util

object Config {
    const val APP_CSS = "/static/app/app.css"
    const val APP_JS = "/static/app/app.js"
    const val HIGHLIGHT_CSS = "/static/highlight/github-dark.min.css"
    const val HIGHLIGHT_JS = "/static/highlight/highlight.min.js"
    const val HTMX_MIN_JS = "/static/htmx/htmx.min.js"
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