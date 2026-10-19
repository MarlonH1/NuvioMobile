package com.nuvio.app.features.tvremote

import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.media.RoutingSessionInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Publishes paired TVs as system media routes (Android 11+) and, while a TV plays, a routing
 * session for it. SystemUI links the media notification to that session through the
 * VolumeProvider's control id, so the output chip says "Bedroom" instead of "This phone",
 * like a Cast device in Google Home. The TV also shows up in the system output switcher.
 */
@RequiresApi(Build.VERSION_CODES.R)
class TvRemoteRouteProvider : MediaRoute2ProviderService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var publishedSessionId: String? = null

    private data class Snapshot(
        val tvs: List<PairedTv>,
        val activeTvId: String?,
        val session: TvSessionState,
    )

    override fun onCreate() {
        super.onCreate()
        TvRemoteStorage.initialize(applicationContext)
        TvRemoteRepository.ensureLoaded()
        scope.launch {
            combine(
                TvRemoteRepository.enabled,
                TvRemoteRepository.pairedTvs,
                TvRemoteRepository.activeTvId,
                TvRemoteRepository.session,
            ) { enabled, tvs, activeId, session ->
                if (enabled) {
                    Snapshot(tvs.filterNot { it.demo }, activeId, session)
                } else {
                    Snapshot(emptyList(), null, TvSessionState())
                }
            }
                .distinctUntilChanged()
                .collect(::publish)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun publish(snapshot: Snapshot) {
        val playingTvId = snapshot.session.tvId?.takeIf {
            snapshot.session.isPlaybackActive && snapshot.session.connection == TvConnectionStatus.Connected
        }
        val volume = snapshot.session.volume.level ?: VOLUME_MAX / 2
        notifyRoutes(
            snapshot.tvs.map { tv ->
                MediaRoute2Info.Builder(tv.id, tv.name)
                    .addFeature(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK)
                    .addFeature(MediaRoute2Info.FEATURE_REMOTE_VIDEO_PLAYBACK)
                    .setDescription(tv.model ?: "Nuvio")
                    .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
                    .setVolumeMax(VOLUME_MAX)
                    .setVolume(if (tv.id == playingTvId) volume else 0)
                    .setConnectionState(
                        if (tv.id == playingTvId) {
                            MediaRoute2Info.CONNECTION_STATE_CONNECTED
                        } else {
                            MediaRoute2Info.CONNECTION_STATE_DISCONNECTED
                        },
                    )
                    .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setType(MediaRoute2Info.TYPE_REMOTE_TV) }
                    .build()
            },
        )

        val tv = snapshot.tvs.firstOrNull { it.id == playingTvId }
        val sessionId = tv?.let { sessionIdFor(it.id) }
        val previous = publishedSessionId
        if (previous != null && previous != sessionId) {
            notifySessionReleased(previous)
            publishedSessionId = null
        }
        if (tv == null || sessionId == null) return
        val session = RoutingSessionInfo.Builder(sessionId, packageName)
            .setName(tv.name)
            .addSelectedRoute(tv.id)
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(VOLUME_MAX)
            .setVolume(volume)
            .build()
        if (previous == sessionId) {
            notifySessionUpdated(session)
        } else {
            // Not created by a request: playback was started from the app or on the TV itself.
            notifySessionCreated(REQUEST_ID_NONE, session)
            publishedSessionId = sessionId
        }
    }

    // The TV only knows "one step up/down", so absolute volume changes become steps.
    private fun setVolume(target: Int) {
        val current = TvRemoteRepository.session.value.volume.level ?: return
        val steps = (target - current).coerceIn(-MAX_VOLUME_STEPS, MAX_VOLUME_STEPS)
        repeat(abs(steps)) { if (steps > 0) TvRemoteRepository.volumeUp() else TvRemoteRepository.volumeDown() }
    }

    override fun onSetRouteVolume(requestId: Long, routeId: String, volume: Int) = setVolume(volume)

    override fun onSetSessionVolume(requestId: Long, sessionId: String, volume: Int) = setVolume(volume)

    // Moving phone playback to the TV goes through Nuvio's own stream picker; the system output
    // switcher cannot hand over a stream, so new sessions are declined.
    override fun onCreateSession(requestId: Long, packageName: String, routeId: String, sessionHints: Bundle?) {
        notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
    }

    override fun onReleaseSession(requestId: Long, sessionId: String) {
        if (sessionId == publishedSessionId) {
            val status = TvRemoteRepository.session.value.playback.status
            if (status != RemotePlaybackStatus.Idle) TvRemoteRepository.stop()
        }
    }

    override fun onSelectRoute(requestId: Long, sessionId: String, routeId: String) {
        notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
    }

    override fun onDeselectRoute(requestId: Long, sessionId: String, routeId: String) {
        notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
    }

    override fun onTransferToRoute(requestId: Long, sessionId: String, routeId: String) {
        notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
    }

    override fun onDiscoveryPreferenceChanged(preference: RouteDiscoveryPreference) = Unit

    companion object {
        internal const val VOLUME_MAX = 100
        private const val MAX_VOLUME_STEPS = 10

        /** Session id the media notification's VolumeProvider must carry as control id. */
        internal fun sessionIdFor(tvId: String) = "nuvio-tv-$tvId"

        private var router: MediaRouter2? = null
        private val callback = object : MediaRouter2.RouteCallback() {}

        /**
         * The system only binds route providers while some app asks for routes. Nuvio asks
         * for its own (passively, no scan) while TV playback is shown, so the session exists.
         */
        internal fun setActive(context: Context, active: Boolean) {
            if (active && router == null) {
                val mediaRouter = MediaRouter2.getInstance(context)
                val executor = Executor { it.run() }
                mediaRouter.registerRouteCallback(
                    executor,
                    callback,
                    RouteDiscoveryPreference.Builder(listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK), false).build(),
                )
                router = mediaRouter
            } else if (!active) {
                router?.unregisterRouteCallback(callback)
                router = null
            }
        }
    }
}
