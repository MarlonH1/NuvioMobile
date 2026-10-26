package com.nuvio.app.features.tvremote

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.protocol.RemoteMedia
import com.nuvio.app.features.tvremote.protocol.RemotePlayRequest
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import com.nuvio.app.features.tvremote.protocol.RemoteStream
import com.nuvio.app.features.tvremote.ui.TvNowPlayingBar
import com.nuvio.app.features.tvremote.ui.TvRemoteControlScreen
import com.nuvio.app.features.tvremote.ui.TvRemoteDevicesScreen
import com.nuvio.app.features.tvremote.ui.parseTvAddress
import com.nuvio.app.features.tvremote.ui.tvRemoteSettingsSection
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the whole remote flow against the built-in demo TV. Set
 * TV_REMOTE_SCREENSHOT_DIR to also write PNGs of each screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TvRemoteDemoFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val screenshotDir = System.getenv("TV_REMOTE_SCREENSHOT_DIR")?.let(::File)

    private enum class Screen { Settings, Devices, Control, Bar }

    @Test
    fun parsesTvAddresses() {
        assertEquals("192.168.1.50" to TV_REMOTE_DEFAULT_PORT, parseTvAddress("192.168.1.50"))
        assertEquals("192.168.1.50" to 47801, parseTvAddress(" ws://192.168.1.50:47801/ws "))
        assertEquals("fe80::1" to 47800, parseTvAddress("[fe80::1]:47800"))
        assertNull(parseTvAddress("192.168.1.50:99999"))
        assertNull(parseTvAddress("bad host"))
    }

    @Test
    fun demoTvPairsPlaysAndFollowsCommands() {
        TvRemoteStorage.initialize(ApplicationProvider.getApplicationContext())
        TvRemoteRepository.ensureLoaded()
        TvRemoteRepository.setEnabled(true)

        // Ticker + progress animations never settle; drive the frame clock by hand.
        compose.mainClock.autoAdvance = false
        var screen by mutableStateOf(Screen.Settings)
        compose.setContent {
            NuvioTheme(darkTheme = true, appTheme = AppTheme.WHITE) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.nuvio.colors.background)) {
                    when (screen) {
                        Screen.Settings -> NuvioScreen { tvRemoteSettingsSection(isTablet = false) }
                        Screen.Devices -> TvRemoteDevicesScreen(title = "Fernseher", isTablet = false, onBack = {})
                        Screen.Control -> TvRemoteControlScreen(onBack = {})
                        Screen.Bar -> BarPreview()
                    }
                }
            }
        }
        snapshot("01-settings-enabled")

        // Pairing: wrong code first, then the demo code.
        TvRemoteRepository.addDemoTv()
        waitFor("awaiting code") { TvRemoteRepository.pairing.value is TvPairingState.AwaitingCode }
        TvRemoteRepository.confirmPairingCode("000000")
        waitFor("wrong code") { (TvRemoteRepository.pairing.value as? TvPairingState.AwaitingCode)?.wrongCode == true }
        TvRemoteRepository.confirmPairingCode(DEMO_TV_CODE)
        waitFor("paired") { TvRemoteRepository.pairing.value is TvPairingState.Paired }
        TvRemoteRepository.cancelPairing()
        assertEquals(DEMO_TV_ID, TvRemoteRepository.activeTvId.value)
        waitFor("connected") { TvRemoteRepository.session.value.connection == TvConnectionStatus.Connected }
        screen = Screen.Devices
        snapshot("03-devices-paired")

        screen = Screen.Control
        snapshot("04-remote-idle")

        TvRemoteRepository.play(
            RemotePlayRequest(
                media = RemoteMedia(
                    type = "series",
                    metaId = "tt0903747",
                    metaType = "series",
                    videoId = "tt0903747:3:7",
                    season = 3,
                    episode = 7,
                    title = "Breaking Bad",
                    episodeTitle = "One Minute",
                ),
                stream = RemoteStream(url = "https://example.com/video.mkv", name = "1080p"),
                startPositionMs = 754_000,
            ),
        )
        waitFor("playing") { TvRemoteRepository.session.value.playback.status == RemotePlaybackStatus.Playing }
        snapshot("05-remote-playing")

        TvRemoteRepository.togglePause()
        waitFor("paused") { TvRemoteRepository.session.value.playback.status == RemotePlaybackStatus.Paused }
        TvRemoteRepository.seekTo(1_800_000)
        waitFor("seeked") {
            val playback = TvRemoteRepository.session.value.playback
            playback.positionMs == 1_800_000L && playback.status == RemotePlaybackStatus.Paused
        }
        // The optimistic seek satisfies the check before the TV reports buffering -> paused.
        Thread.sleep(800)
        TvRemoteRepository.selectSubtitleTrack("s9")
        waitFor("subtitle") {
            TvRemoteRepository.session.value.playback.subtitleTracks.firstOrNull { it.selected }?.id == "s9"
        }
        TvRemoteRepository.toggleMute()
        waitFor("muted") { TvRemoteRepository.session.value.volume.muted }
        snapshot("06-remote-paused-muted")

        screen = Screen.Bar
        snapshot("07-now-playing-bar")

        screen = Screen.Settings
        snapshot("08-settings-playing")

        TvRemoteRepository.playNext()
        waitFor("next episode") { TvRemoteRepository.session.value.playback.media?.episode == 8 }
        TvRemoteRepository.stop()
        waitFor("stopped") { !TvRemoteRepository.session.value.isPlaybackActive }
        assertTrue(TvRemoteLog.entries.value.isNotEmpty())

        TvRemoteRepository.removeTv(DEMO_TV_ID)
        assertNull(TvRemoteRepository.activeTvId.value)
        TvRemoteRepository.setEnabled(false)
    }

    @Composable
    private fun BarPreview() {
        Box(Modifier.fillMaxSize()) {
            TvNowPlayingBar(visible = true, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun snapshot(name: String) {
        val dir = screenshotDir ?: return
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(1_000)
        ShadowLooper.idleMainLooper()
        dir.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
