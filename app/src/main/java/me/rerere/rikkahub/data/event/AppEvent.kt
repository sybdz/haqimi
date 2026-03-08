package me.rerere.rikkahub.data.event

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data class Toast(
        val message: String,
        val style: ToastStyle = ToastStyle.Normal,
    ) : AppEvent()
}

enum class ToastStyle {
    Normal,
    Success,
    Error,
    Warning,
    Info,
}
