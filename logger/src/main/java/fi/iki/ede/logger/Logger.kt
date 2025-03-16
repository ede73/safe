package fi.iki.ede.logger

import android.util.Log

object Logger {
    fun d(tag: String, message: String) = Log.d(tag, message)
    fun d(tag: String, message: String, t: Throwable) = Log.d(tag, message, t)
    fun i(tag: String, message: String) = Log.i(tag, message)
    fun i(tag: String, message: String, t: Throwable) = Log.i(tag, message, t)
    fun w(tag: String, message: String) = Log.w(tag, message)
    fun e(tag: String, message: String) = Log.e(tag, message)
    fun e(tag: String, message: String, t: Throwable) = Log.e(tag, message, t)
}