package com.nuvio.app.features.tvremote

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/** Kermit logging plus an in-memory ring buffer shown in the TV remote debug log. */
internal object TvRemoteLog {
    private const val MAX_ENTRIES = 300
    private val logger = Logger.withTag("TvRemote")
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun i(message: String) {
        logger.i { message }
        append("I", message)
    }

    fun w(message: String, error: Throwable? = null) {
        logger.w(error) { message }
        append("W", if (error != null) "$message: ${error.message}" else message)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(level: String, message: String) {
        val now = Clock.System.now().toString()
        val time = now.substringAfter('T').take(12)
        _entries.update { (it + "$time $level $message").takeLast(MAX_ENTRIES) }
    }
}
