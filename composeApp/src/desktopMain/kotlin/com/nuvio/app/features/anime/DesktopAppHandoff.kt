package com.nuvio.app.features.anime

import com.nuvio.app.core.ui.platformExitApp
import java.io.File

/**
 * Desktop side of [AppHandoff]: starts the companion app as a separate process and hands over.
 * The companion's executable can be overridden with the NUVIO_COMPANION_APP environment variable.
 */
internal object DesktopAppHandoff {
    private const val READY_ARG = "--nuvio-handoff-ready="
    private const val COVER_ARG = "--nuvio-handoff-cover="

    private val logDir: File get() = File(System.getProperty("user.home").orEmpty(), ".cache/nuvio-handoff")

    private fun log(message: String) {
        runCatching {
            logDir.mkdirs()
            File(logDir, "handoff.log").appendText("${java.time.LocalDateTime.now()} [${ProcessHandle.current().pid()}] $message\n")
        }
    }

    /** Call first thing in main(): wires up an arrival from the other app and installs the launcher. */
    fun install(args: Array<String>, companionName: String, goingToAnime: Boolean, candidates: List<File>) {
        log("install companion=$companionName args=${args.toList()}")
        // Dev hook: NUVIO_HANDOFF_SELFTEST=<seconds> presses the switch by itself after that delay.
        System.getenv("NUVIO_HANDOFF_SELFTEST")?.toLongOrNull()?.let { seconds ->
            Thread {
                Thread.sleep(seconds * 1000)
                log("selftest: switching")
                AppHandoff.switch()
            }.apply { isDaemon = true }.start()
        }
        args.firstOrNull { it.startsWith(READY_ARG) }?.removePrefix(READY_ARG)?.takeIf { it.isNotBlank() }?.let { path ->
            AppHandoff.arrivalCoverIsAnime = args.any { it == "${COVER_ARG}anime" }
            AppHandoff.arrivalReadySignal = {
                log("arrived, signalling ready -> $path")
                runCatching { File(path).writeText("ready") }.onFailure { log("ready signal failed: $it") }
            }
        }
        AppHandoff.launcher = Launcher(companionName, goingToAnime, candidates)
    }

    private class Launcher(
        override val companionName: String,
        override val goingToAnime: Boolean,
        private val candidates: List<File>,
    ) : AppHandoff.Launcher {
        override fun launch(): AppHandoff.Session? {
            val override = System.getenv("NUVIO_COMPANION_APP")?.takeIf { it.isNotBlank() }?.let(::File)
            val all = listOfNotNull(override) + candidates
            val executable = all.firstOrNull { it.isFile && it.canExecute() }
            if (executable == null) {
                log("launch: no executable among ${all.map { "${it.path} file=${it.isFile} exec=${it.canExecute()}" }}")
                return null
            }
            val readyFile = File.createTempFile("nuvio-handoff-", ".ready").apply { delete(); deleteOnExit() }
            logDir.mkdirs()
            val companionLog = File(logDir, "companion.log")
            val process = runCatching {
                ProcessBuilder(
                    executable.absolutePath,
                    READY_ARG + readyFile.absolutePath,
                    COVER_ARG + if (goingToAnime) "anime" else "regular",
                )
                    .directory(executable.parentFile)
                    .redirectErrorStream(true)
                    .redirectOutput(companionLog)
                    .also { builder ->
                        // The jpackage launcher marks its own process (e.g. _JPACKAGE_LAUNCHER) so it can
                        // re-exec itself as a plain JVM. A child launcher inheriting that marker treats our
                        // arguments as JVM options and dies, so strip launcher-internal variables.
                        val environment = builder.environment()
                        val internal = environment.keys.filter { key ->
                            key.startsWith("_JPACKAGE") || key.startsWith("JPACKAGE_") ||
                                key.startsWith("JAVA_MAIN_CLASS") || key.startsWith("_JAVA_VERSION_SET") ||
                                key.startsWith("_JAVA_LAUNCHER") || key == "JAVA_STARTED_ON_FIRST_THREAD"
                        }
                        internal.forEach(environment::remove)
                        log("launch: cleared env $internal")
                    }
                    .start()
            }.onFailure { log("launch: start failed: $it") }.getOrNull() ?: return null
            log("launch: started ${executable.path} pid=${process.pid()} ready=${readyFile.path}")
            process.onExit().thenAccept { log("launch: companion exited code=${it.exitValue()}") }
            return object : AppHandoff.Session {
                override val isReady: Boolean get() = readyFile.exists()
                override val isAlive: Boolean get() = process.isAlive
            }
        }

        override fun exitThisApp() {
            log("handoff complete, exiting")
            platformExitApp()
        }
    }

    private val home: File get() = File(System.getProperty("user.home").orEmpty())

    private fun windowsDirs(): List<File> = listOfNotNull(
        System.getenv("LOCALAPPDATA")?.let(::File),
        System.getenv("ProgramFiles")?.let(::File),
    )

    /** Where the anime app (NuvioCodeineXO) is usually installed. */
    fun animeAppCandidates(): List<File> = listOf(
        File(home, "Applications/NuvioCodeineXO/bin/NuvioCodeineXO"),
        File("/opt/nuviocodeinexo/bin/NuvioCodeineXO"),
        File("/Applications/NuvioCodeineXO.app/Contents/MacOS/NuvioCodeineXO"),
    ) + windowsDirs().map { File(it, "NuvioCodeineXO/NuvioCodeineXO.exe") }

    /** Where the regular Nuvio app is usually installed. */
    fun regularAppCandidates(): List<File> = listOf(
        File(home, "Applications/Nuvio-shad/bin/Nuvio"),
        File("/opt/nuvio/bin/Nuvio"),
        File("/Applications/Nuvio.app/Contents/MacOS/Nuvio"),
    ) + windowsDirs().map { File(it, "Nuvio/Nuvio.exe") }
}
