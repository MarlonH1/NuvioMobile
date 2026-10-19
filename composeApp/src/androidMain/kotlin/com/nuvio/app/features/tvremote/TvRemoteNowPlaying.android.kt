package com.nuvio.app.features.tvremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.nuvio.app.R
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_playing_on
import org.jetbrains.compose.resources.getString

/**
 * "Google Home" style controls for whatever plays on the selected TV, no matter who started
 * it: a media notification (lock screen, quick settings player) backed by a MediaSession that
 * is marked as *remote* playback, so the phone's volume keys change the TV volume.
 *
 * The notification runs in a foreground service while the TV plays; that also keeps the
 * WebSocket alive when Nuvio is in the background.
 */
internal object TvRemoteNowPlaying {
    private const val CHANNEL_ID = "nuvio_tv_remote"
    internal const val NOTIFICATION_ID = 4711
    private const val EXTRA_OPEN_REMOTE = "com.nuvio.app.extra.OPEN_TV_REMOTE"
    private const val VOLUME_STEPS = 100
    private const val REWIND_MS = 10_000L
    private const val FORWARD_MS = 30_000L

    internal const val ACTION_TOGGLE = "com.nuvio.app.tvremote.TOGGLE"
    internal const val ACTION_REWIND = "com.nuvio.app.tvremote.REWIND"
    internal const val ACTION_FORWARD = "com.nuvio.app.tvremote.FORWARD"
    internal const val ACTION_STOP = "com.nuvio.app.tvremote.STOP"

    /** Sent by the root watcher (remote/magisk) when the TV starts playing. */
    internal const val ACTION_WAKE = "com.nuvio.app.tvremote.WAKE"
    private const val WATCH_CONFIG_FILE = "tv_remote_watch.conf"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var session: MediaSession? = null
    private var volumeProvider: VolumeProvider? = null
    private var artworkUrl: String? = null
    private var artwork: Bitmap? = null
    private var foreground = false
    internal var currentNotification: Notification? = null
        private set

    private data class Snapshot(
        val state: TvSessionState,
        val tvName: String?,
    )

    fun install(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        scope.launch {
            combine(TvRemoteRepository.session, TvRemoteRepository.pairedTvs, TvRemoteRepository.enabled) { state, tvs, enabled ->
                Snapshot(state.takeIf { enabled } ?: TvSessionState(), tvs.firstOrNull { it.id == state.tvId }?.name)
            }
                .distinctUntilChanged()
                .collect(::render)
        }
        scope.launch {
            combine(TvRemoteRepository.enabled, TvRemoteRepository.pairedTvs, TvRemoteRepository.activeTvId) { enabled, tvs, activeId ->
                tvs.firstOrNull { it.id == activeId && !it.demo }?.takeIf { enabled }
            }
                .distinctUntilChanged()
                .collect { tv -> withContext(Dispatchers.IO) { writeWatchConfig(tv) } }
        }
    }

    /**
     * Target for the optional root watcher: it keeps one idle connection to the TV and starts
     * [TvRemoteNowPlayingService] with [ACTION_WAKE] when playback begins. Plain KEY=value
     * lines so a shell script can read it; removed when there is nothing to watch.
     */
    private fun writeWatchConfig(tv: PairedTv?) {
        val context = appContext ?: return
        val file = java.io.File(context.filesDir, WATCH_CONFIG_FILE)
        if (tv == null) {
            file.delete()
            return
        }
        fun clean(value: String) = value.replace(Regex("[\\r\\n=]"), " ").trim()
        val text = buildString {
            appendLine("host=${clean(tv.host)}")
            appendLine("port=${tv.port}")
            appendLine("token=${clean(tv.token)}")
            appendLine("name=${clean(tv.name)}")
            appendLine("package=${context.packageName}")
        }
        val temp = java.io.File(context.filesDir, "$WATCH_CONFIG_FILE.tmp")
        temp.writeText(text)
        temp.renameTo(file)
    }

    /** Shown while a cold-started service (root watcher) is still connecting to the TV. */
    internal fun placeholderNotification(context: Context): Notification {
        createChannel(context)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(TvRemoteRepository.activeTv()?.name ?: "TV")
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .build()
    }

    /** Called from MainActivity for launches from the notification. */
    fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REMOTE, false) == true) {
            intent.removeExtra(EXTRA_OPEN_REMOTE)
            TvRemoteNavigation.open(TvRemoteDestination.Control)
        }
    }

    internal fun handleAction(action: String?) {
        val state = TvRemoteRepository.session.value
        when (action) {
            ACTION_TOGGLE -> TvRemoteRepository.togglePause()
            ACTION_REWIND -> TvRemoteRepository.seekBy(-REWIND_MS)
            ACTION_FORWARD -> TvRemoteRepository.seekBy(FORWARD_MS)
            ACTION_STOP -> if (state.isPlaybackActive) TvRemoteRepository.stop()
        }
    }

    private suspend fun render(snapshot: Snapshot) {
        val context = appContext ?: return
        val state = snapshot.state
        val media = state.playback.media
        val show = state.isPlaybackActive && media != null && state.connection == TvConnectionStatus.Connected
        if (!show) {
            hide(context)
            return
        }
        val tvName = snapshot.tvName ?: "TV"
        val mediaSession = session ?: createSession(context).also { session = it }
        updateVolume(mediaSession, state.volume)
        updateArtwork(media.poster ?: media.background)

        val episodeLine = listOfNotNull(
            if (media.season != null && media.episode != null) "S${media.season} E${media.episode}" else null,
            media.episodeTitle?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val playingOn = getString(Res.string.tv_remote_playing_on, tvName)

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, media.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, episodeLine.ifBlank { playingOn })
                .putString(MediaMetadata.METADATA_KEY_ALBUM, playingOn)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.playback.durationMs)
                .apply { artwork?.let { putBitmap(MediaMetadata.METADATA_KEY_ART, it) } }
                .build(),
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_REWIND or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_STOP or
                        (if (state.playback.hasNext) PlaybackState.ACTION_SKIP_TO_NEXT else 0L),
                )
                .setState(
                    state.playback.status.toPlatformState(),
                    state.positionAt(TvRemoteRepository.now()),
                    if (state.playback.status == RemotePlaybackStatus.Playing) state.playback.rate else 0f,
                    android.os.SystemClock.elapsedRealtime(),
                )
                .build(),
        )
        mediaSession.isActive = true

        val notification = buildNotification(context, mediaSession.sessionToken, media.title, episodeLine, playingOn, state)
        currentNotification = notification
        show(context, notification)
    }

    private fun createSession(context: Context): MediaSession =
        MediaSession(context, "NuvioTvRemote").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        if (TvRemoteRepository.session.value.playback.status == RemotePlaybackStatus.Paused) {
                            TvRemoteRepository.togglePause()
                        }
                    }

                    override fun onPause() {
                        if (TvRemoteRepository.session.value.playback.status == RemotePlaybackStatus.Playing) {
                            TvRemoteRepository.togglePause()
                        }
                    }

                    override fun onSeekTo(pos: Long) = TvRemoteRepository.seekTo(pos.coerceAtLeast(0L))

                    override fun onRewind() = TvRemoteRepository.seekBy(-REWIND_MS)

                    override fun onFastForward() = TvRemoteRepository.seekBy(FORWARD_MS)

                    override fun onSkipToNext() = TvRemoteRepository.playNext()

                    override fun onStop() = TvRemoteRepository.stop()
                },
            )
        }

    // Remote playback: the system routes hardware volume keys to this provider instead of the
    // phone's own stream, and its volume UI shows the TV's level.
    private fun updateVolume(mediaSession: MediaSession, volume: TvVolumeState) {
        val level = (volume.level ?: VOLUME_STEPS / 2).coerceIn(0, VOLUME_STEPS)
        val provider = volumeProvider
        if (provider != null) {
            if (provider.currentVolume != level) provider.currentVolume = level
            return
        }
        val created = object : VolumeProvider(VOLUME_CONTROL_RELATIVE, VOLUME_STEPS, level) {
            override fun onAdjustVolume(direction: Int) {
                when {
                    direction > 0 -> TvRemoteRepository.volumeUp()
                    direction < 0 -> TvRemoteRepository.volumeDown()
                }
            }
        }
        volumeProvider = created
        mediaSession.setPlaybackToRemote(created)
    }

    private suspend fun updateArtwork(url: String?) {
        if (url == artworkUrl) return
        artworkUrl = url
        artwork = if (url == null) null else withContext(Dispatchers.IO) { downloadArtwork(url) }
    }

    private fun show(context: Context, notification: Notification) {
        createChannel(context)
        if (foreground) {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
            return
        }
        // Android 12+ refuses foreground services started from the background (e.g. the TV
        // started playing while Nuvio was not open). The notification still shows; it just
        // does not keep the process alive in that case.
        val started = runCatching {
            context.startForegroundService(Intent(context, TvRemoteNowPlayingService::class.java))
        }.isSuccess
        foreground = started
        if (!started) {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun hide(context: Context) {
        currentNotification = null
        session?.let {
            it.isActive = false
            it.release()
        }
        session = null
        volumeProvider = null
        artworkUrl = null
        artwork = null
        if (foreground) {
            foreground = false
            context.stopService(Intent(context, TvRemoteNowPlayingService::class.java))
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    internal fun onServiceStopped() {
        foreground = false
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TV remote", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for what plays on your TV"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun buildNotification(
        context: Context,
        token: MediaSession.Token,
        title: String,
        episodeLine: String,
        playingOn: String,
        state: TvSessionState,
    ): Notification {
        val playing = state.playback.status == RemotePlaybackStatus.Playing ||
            state.playback.status == RemotePlaybackStatus.Buffering
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(episodeLine.ifBlank { playingOn })
            .setSubText(playingOn)
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent(context))
            .setDeleteIntent(actionIntent(context, ACTION_STOP, 14))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(android.R.drawable.ic_media_rew, "Rewind 10 seconds", actionIntent(context, ACTION_REWIND, 11)).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pause" else "Play",
                    actionIntent(context, ACTION_TOGGLE, 12),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(android.R.drawable.ic_media_ff, "Forward 30 seconds", actionIntent(context, ACTION_FORWARD, 13)).build(),
            )
            .setStyle(Notification.MediaStyle().setMediaSession(token).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, TvRemoteNowPlayingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.putExtra(EXTRA_OPEN_REMOTE, true)
            ?: return null
        return PendingIntent.getActivity(context, 10, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun RemotePlaybackStatus.toPlatformState(): Int = when (this) {
        RemotePlaybackStatus.Playing -> PlaybackState.STATE_PLAYING
        RemotePlaybackStatus.Paused -> PlaybackState.STATE_PAUSED
        RemotePlaybackStatus.Buffering, RemotePlaybackStatus.Loading -> PlaybackState.STATE_BUFFERING
        RemotePlaybackStatus.Error -> PlaybackState.STATE_ERROR
        RemotePlaybackStatus.Ended, RemotePlaybackStatus.Idle -> PlaybackState.STATE_STOPPED
    }

    private fun downloadArtwork(url: String): Bitmap? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
        }
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** Foreground service holding the TV notification; also receives its button presses. */
class TvRemoteNowPlayingService : Service() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val giveUp = Runnable {
        // Woken by the watcher but nothing is playing (any more): let the process go.
        if (TvRemoteNowPlaying.currentNotification == null) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // A cold start by the root watcher skips MainActivity; load the paired TV here.
        TvRemoteStorage.initialize(applicationContext)
        TvRemoteRepository.ensureLoaded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val waking = intent?.action == TvRemoteNowPlaying.ACTION_WAKE
        val notification = TvRemoteNowPlaying.currentNotification
            ?: if (waking) TvRemoteNowPlaying.placeholderNotification(this) else null
        if (notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (waking) {
            TvRemoteRepository.reconnect()
            handler.removeCallbacks(giveUp)
            handler.postDelayed(giveUp, WAKE_TIMEOUT_MS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(TvRemoteNowPlaying.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(TvRemoteNowPlaying.NOTIFICATION_ID, notification)
        }
        intent?.action?.let(TvRemoteNowPlaying::handleAction)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        TvRemoteNowPlaying.onServiceStopped()
        super.onDestroy()
    }

    private companion object {
        const val WAKE_TIMEOUT_MS = 30_000L
    }
}
