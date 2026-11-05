package io.github.linuxkernel44.autocomp.shizuku

import io.github.linuxkernel44.autocomp.IShellService
import kotlin.system.exitProcess

/** Runs in the Shizuku server process with the `shell` uid. Deliberately exposes a tiny, fixed API. */
class ShellService : IShellService.Stub() {
    override fun destroy() { exitProcess(0) }

    override fun dumpAudio(): String = run("dumpsys audio")
        .lineSequence().filter { it.contains("AudioPlaybackConfiguration") }.joinToString("\n")

    override fun grantRecordAudio(): String = run("pm grant io.github.linuxkernel44.autocomp android.permission.RECORD_AUDIO")

    private fun run(cmd: String): String {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        return p.inputStream.bufferedReader().readText().also { p.waitFor() }
    }
}
