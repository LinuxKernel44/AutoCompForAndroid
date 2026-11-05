package io.github.linuxkernel44.autocomp.spike

import io.github.linuxkernel44.autocomp.IProbeService
import kotlin.system.exitProcess

/** Runs inside the Shizuku server process (shell uid). Phase 0 spike only. */
class ProbeService : IProbeService.Stub() {
    override fun destroy() { exitProcess(0) }
    override fun exec(command: String): String {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        return p.inputStream.bufferedReader().readText().also { p.waitFor() }
    }
}
