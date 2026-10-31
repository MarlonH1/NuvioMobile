package com.nuvio.app.features.player

import com.nuvio.app.features.tvremote.protocol.RemotePlayRequest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** The open player, as the desktop app's remote host (phone remote) sees and controls it. */
internal class PlayerRemoteHandle(
    val runtime: PlayerScreenRuntime,
    /** Closes the player like its back button (saves progress, releases the native player). */
    val close: () -> Unit,
)

/**
 * Lets the desktop remote host reach the player: the open player registers itself here, and play
 * requests from the phone go to the app's navigation through [playRequests]. Main thread only.
 */
internal object PlayerRemoteRegistry {
    private val _active = MutableStateFlow<PlayerRemoteHandle?>(null)
    val active: StateFlow<PlayerRemoteHandle?> = _active.asStateFlow()

    private val _playRequests = MutableSharedFlow<RemotePlayRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val playRequests: SharedFlow<RemotePlayRequest> = _playRequests.asSharedFlow()

    fun register(handle: PlayerRemoteHandle) {
        _active.value = handle
    }

    fun unregister(handle: PlayerRemoteHandle) {
        if (_active.value === handle) _active.value = null
    }

    fun requestPlay(request: RemotePlayRequest) {
        _playRequests.tryEmit(request)
    }
}
