package com.nuvio.app.features.tvremote

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.protocol.RemoteMedia
import com.nuvio.app.features.tvremote.protocol.RemotePlayRequest
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import com.nuvio.app.features.tvremote.protocol.RemoteStream
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitle
import com.nuvio.app.features.tvremote.ui.TvCastButton
import com.nuvio.app.features.tvremote.ui.TvNowPlayingBar
import com.nuvio.app.features.tvremote.ui.TvRemoteControlScreen
import com.nuvio.app.features.tvremote.ui.TvRemoteDevicesScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.tvremote.ui.PairingDialogButtons
import com.nuvio.app.features.tvremote.ui.PairingDialogContent
import com.nuvio.app.features.tvremote.ui.TvAudioSheetContent
import com.nuvio.app.features.tvremote.ui.TvSheetColor
import com.nuvio.app.features.tvremote.ui.TvSubtitleSheetContent
import com.nuvio.app.features.tvremote.ui.TvTargetSheetContent
import com.nuvio.app.features.tvremote.ui.fallbackAmbientColor
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.test.Test

/**
 * Screenshots of the remote with real cover art and of every popup.
 * Only runs its captures when TV_REMOTE_SCREENSHOT_DIR is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TvRemoteVisualTest {
    @get:Rule
    val compose = createComposeRule()

    private val screenshotDir = System.getenv("TV_REMOTE_SCREENSHOT_DIR")?.let(::File)

    private enum class Screen { Control, Devices, Cast, Subtitles, Audio, Target, Pairing }

    private var pairingPreview by mutableStateOf<TvPairingState>(TvPairingState.Connecting)
    private var pairingCode by mutableStateOf("")

    @Test
    fun coverArtAndPopups() {
        val dir = screenshotDir ?: return
        val context = ApplicationProvider.getApplicationContext<Application>()
        TvRemoteStorage.initialize(context)
        TvRemoteRepository.ensureLoaded()
        TvRemoteRepository.removeTv(DEMO_TV_ID)
        TvRemoteRepository.setEnabled(true)
        val poster = writePoster(File(context.cacheDir, "poster.png"))
        val backdrop = writePoster(File(context.cacheDir, "backdrop.png"), landscape = true)

        compose.mainClock.autoAdvance = false
        var screen by mutableStateOf(Screen.Devices)
        compose.setContent {
            NuvioTheme(darkTheme = true, appTheme = AppTheme.WHITE) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.nuvio.colors.background)) {
                    when (screen) {
                        Screen.Control -> TvRemoteControlScreen(onBack = {})
                        Screen.Devices -> TvRemoteDevicesScreen(title = "Fernseher", isTablet = false, onBack = {})
                        Screen.Cast -> Box(Modifier.fillMaxSize()) {
                            TvCastButton(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                            TvNowPlayingBar(visible = true, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                        Screen.Subtitles -> SheetPreview { TvSubtitleSheetContent(session().playback, ambient) }
                        Screen.Audio -> SheetPreview { TvAudioSheetContent(session().playback, ambient) }
                        Screen.Target -> SheetPreview {
                            TvTargetSheetContent(
                                tvs = TvRemoteRepository.pairedTvs.value,
                                activeTvId = TvRemoteRepository.activeTvId.value,
                                session = session(),
                                onSelect = {},
                                onOpenRemote = {},
                                onManage = {},
                            )
                        }
                        Screen.Pairing -> DialogPreview {
                            PairingDialogContent(pairingPreview, pairingCode) {}
                            PairingDialogButtons(pairingPreview, pairingCode)
                        }
                    }
                }
            }
        }

        // Pairing dialog states, rendered inline (Robolectric does not draw dialog windows).
        screen = Screen.Pairing
        pairingPreview = TvPairingState.AwaitingCode("Wohnzimmer-TV")
        pairingCode = "1234"
        snapshotTopWindow(dir, "10-pairing-code")
        pairingPreview = TvPairingState.AwaitingCode("Wohnzimmer-TV", wrongCode = true)
        pairingCode = ""
        snapshotTopWindow(dir, "10b-pairing-wrong")
        pairingPreview = TvPairingState.Failed("unreachable")
        snapshotTopWindow(dir, "10c-pairing-failed")
        pairingPreview = TvPairingState.Paired(PairedTv("x", "Wohnzimmer-TV", "192.168.1.50", token = "t"))
        snapshotTopWindow(dir, "11-pairing-done")
        screen = Screen.Devices

        TvRemoteRepository.addDemoTv()
        waitFor { TvRemoteRepository.pairing.value is TvPairingState.AwaitingCode }
        TvRemoteRepository.confirmPairingCode(DEMO_TV_CODE)
        waitFor { TvRemoteRepository.pairing.value is TvPairingState.Paired }
        TvRemoteRepository.cancelPairing()
        waitFor { TvRemoteRepository.session.value.connection == TvConnectionStatus.Connected }

        TvRemoteRepository.play(
            RemotePlayRequest(
                media = RemoteMedia(
                    type = "series",
                    metaId = "tt0944947",
                    metaType = "series",
                    season = 1,
                    episode = 9,
                    title = "Game of Thrones",
                    episodeTitle = "Baelor",
                    poster = poster.absolutePath,
                    background = backdrop.absolutePath,
                ),
                stream = RemoteStream(
                    url = "https://example.com/got.mkv",
                    subtitles = listOf(RemoteSubtitle(id = "ext-0", url = "https://example.com/de.srt", lang = "ger", label = "Deutsch (OpenSubtitles)")),
                ),
                startPositionMs = 2_412_000,
            ),
        )
        waitFor { TvRemoteRepository.session.value.playback.status == RemotePlaybackStatus.Playing }
        TvRemoteRepository.selectSubtitleTrack("ext-0")
        TvRemoteRepository.setSubtitleDelay(-250)
        waitFor { TvRemoteRepository.session.value.playback.subtitleTracks.any { it.selected } }
        screen = Screen.Control
        // Image decoding and palette extraction run on real threads.
        repeat(20) { settle(); Thread.sleep(100) }
        snapshotTopWindow(dir, "12-remote-cover-art")

        ambient = fallbackAmbientColor("Game of Thrones").copy(red = 0.78f, green = 0.45f, blue = 0.16f)
        screen = Screen.Subtitles
        snapshotTopWindow(dir, "13-subtitle-sheet")
        screen = Screen.Audio
        snapshotTopWindow(dir, "14-audio-sheet")

        screen = Screen.Cast
        repeat(10) { settle(); Thread.sleep(100) }
        snapshotTopWindow(dir, "15-cover-bar")
        screen = Screen.Target
        snapshotTopWindow(dir, "16-cast-target-sheet")

        TvRemoteRepository.stop()
        TvRemoteRepository.removeTv(DEMO_TV_ID)
        TvRemoteRepository.setEnabled(false)
    }

    private var ambient by mutableStateOf(Color.Gray)

    private fun session() = TvRemoteRepository.session.value

    /** Mirrors NuvioModalBottomSheet's frame over the dimmed remote. */
    @Composable
    private fun SheetPreview(content: @Composable ColumnScope.() -> Unit) {
        Box(Modifier.fillMaxSize()) {
            TvRemoteControlScreen(onBack = {})
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(TvSheetColor)
                    .padding(top = 12.dp),
                content = content,
            )
        }
    }

    /** Mirrors DialogSurface's frame over the dimmed TV list. */
    @Composable
    private fun DialogPreview(content: @Composable ColumnScope.() -> Unit) {
        val tokens = MaterialTheme.nuvio
        Box(Modifier.fillMaxSize()) {
            TvRemoteDevicesScreen(title = "Fernseher", isTablet = false, onBack = {})
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(tokens.shapes.dialog)
                    .background(tokens.colors.surfaceDialog)
                    .padding(tokens.spacing.dialogPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
                content = content,
            )
        }
    }

    private fun writePoster(file: File, landscape: Boolean = false): File {
        val (w, h) = if (landscape) 960 to 540 else 400 to 600
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF7A1E12.toInt(), 0xFFD9822B.toInt(), 0xFF1B2A3A.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val text = Paint().apply {
            color = 0xFFF5E6C8.toInt()
            textSize = h * 0.09f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("GAME OF", w / 2f, h * 0.72f, text)
        canvas.drawText("THRONES", w / 2f, h * 0.83f, text)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(400)
        ShadowLooper.idleMainLooper()
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            settle()
            Thread.sleep(50)
        }
        settle()
    }

    /** Captures the top-most window, so dialogs and sheets are included. */
    private fun snapshotTopWindow(dir: File, name: String) {
        settle()
        dir.mkdirs()
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
        val count = roots.fetchSemanticsNodes(atLeastOneRootRequired = true).size
        val bitmap = roots[count - 1].captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
