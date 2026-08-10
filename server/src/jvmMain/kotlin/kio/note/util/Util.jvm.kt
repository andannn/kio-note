package kio.note.util

actual fun getEnv(key: String): String? {
    return System.getenv(key)
}