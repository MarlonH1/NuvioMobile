package com.nuvio.app

import java.awt.GraphicsConfiguration
import java.awt.Rectangle
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * How much larger the UI should be drawn on the window's monitor, on top of what Java already
 * scales. Java sees every monitor at scale 1 under XWayland (e.g. Hyprland), so a 4K screen with a
 * desktop scale of 2 showed the UI at half size. Order:
 *  1. NUVIO_UI_SCALE (environment) or -Dnuvio.uiScale: the user's choice for every monitor.
 *  2. Java already scales (Windows, macOS, GNOME): nothing more.
 *  3. Hyprland: that monitor's scale (`hyprctl monitors`), matched by output name via xrandr.
 *  4. Otherwise, on Linux: estimated from the monitor height (1080p = 1).
 */
internal object DesktopDisplayScale {
    private const val MaxScale = 4f
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var hyprlandScales: Map<Rectangle, Float>? = null

    private val override: Float? =
        (System.getenv("NUVIO_UI_SCALE") ?: System.getProperty("nuvio.uiScale"))
            ?.trim()?.toFloatOrNull()?.takeIf { it > 0f }?.coerceAtMost(MaxScale)

    fun forConfiguration(configuration: GraphicsConfiguration?): Float {
        override?.let { return it }
        configuration ?: return 1f
        if (configuration.defaultTransform.scaleX > 1.01) return 1f
        if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return 1f
        val bounds = configuration.bounds
        hyprlandScaleFor(bounds)?.let { return it }
        // Rounded to quarter steps: 1440p = 1.25, 4K = 2.
        return (Math.round(bounds.height / 1080f * 4f) / 4f).coerceIn(1f, MaxScale)
    }

    /** Monitors were added, removed or rearranged: read Hyprland's scales again. */
    fun invalidate() {
        hyprlandScales = null
    }

    private fun hyprlandScaleFor(bounds: Rectangle): Float? {
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE").isNullOrBlank()) return null
        val scales = hyprlandScales ?: readHyprlandScales().also { hyprlandScales = it }
        return scales[bounds] ?: scales.entries.firstOrNull { it.key.x == bounds.x && it.key.y == bounds.y }?.value
    }

    /** X screen bounds (as Java sees them) to Hyprland scale, joined by output name. */
    private fun readHyprlandScales(): Map<Rectangle, Float> {
        val byName = run("hyprctl", "monitors", "-j")?.let { output ->
            runCatching { json.parseToJsonElement(output) as JsonArray }.getOrNull()
        }?.mapNotNull { element ->
            val monitor = element as? JsonObject ?: return@mapNotNull null
            val name = monitor["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val scale = monitor["scale"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
            name to scale.coerceIn(1f, MaxScale)
        }?.toMap().orEmpty()
        if (byName.isEmpty()) return emptyMap()
        // " 0: +HDMI-A-1 3840/1600x2160/900+0+0  HDMI-A-1"
        val line = Regex("""^\s*\d+:\s+\+?\*?(\S+)\s+(\d+)/\d+x(\d+)/\d+\+(-?\d+)\+(-?\d+)""")
        return run("xrandr", "--listmonitors")?.lineSequence()?.mapNotNull { text ->
            val match = line.find(text) ?: return@mapNotNull null
            val (name, width, height, x, y) = match.destructured
            val scale = byName[name] ?: return@mapNotNull null
            Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt()) to scale
        }?.toMap().orEmpty()
    }

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(false).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() != 0) null else output
    }.getOrNull()
}
