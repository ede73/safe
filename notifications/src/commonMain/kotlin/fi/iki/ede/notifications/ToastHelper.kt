package fi.iki.ede.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ToastMessage(
    val message: String,
    val duration: ToastDuration = ToastDuration.SHORT,
    val id: Long = Random.nextLong()
)

enum class ToastDuration {
    SHORT,
    LONG
}

private val _currentToast = MutableStateFlow<ToastMessage?>(null)
val currentToastFlow: StateFlow<ToastMessage?> = _currentToast.asStateFlow()

private val toastScope = CoroutineScope(Dispatchers.Default)
private var dismissJob: Job? = null

expect object ToastHelper {
    fun showToast(message: String, duration: ToastDuration = ToastDuration.SHORT)
}

fun showToast(message: String, duration: ToastDuration = ToastDuration.SHORT) {
    val toast = ToastMessage(message, duration)
    _currentToast.value = toast
    dismissJob?.cancel()
    val delayMillis = if (duration == ToastDuration.LONG) 3500L else 2000L
    dismissJob = toastScope.launch {
        delay(delayMillis)
        if (_currentToast.value?.id == toast.id) {
            _currentToast.value = null
        }
    }
    ToastHelper.showToast(message, duration)
}
