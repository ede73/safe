package fi.iki.ede.logger

actual object Logger {
    actual fun d(tag: String, message: String) = println("D/$tag: $message")
    actual fun d(tag: String, message: String, t: Throwable) = println("D/$tag: $message\n${t.stackTraceToString()}")
    actual fun i(tag: String, message: String) = println("I/$tag: $message")
    actual fun i(tag: String, message: String, t: Throwable) = println("I/$tag: $message\n${t.stackTraceToString()}")
    actual fun w(tag: String, message: String) = println("W/$tag: $message")
    actual fun e(tag: String, message: String) = println("E/$tag: $message")
    actual fun e(tag: String, message: String, t: Throwable) = println("E/C:/Users/ede/src/safe/logger/src/desktopMain/kotlin/fi/iki/ede/logger/Logger.kt$tag: $message\n${t.stackTraceToString()}")
}
