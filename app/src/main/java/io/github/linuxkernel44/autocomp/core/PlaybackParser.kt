package io.github.linuxkernel44.autocomp.core

data class Player(
    val piid: Int,
    val uid: Int,
    val pid: Int,
    val state: String,
    val usage: String,
    val sessionId: Int,
) {
    val isStarted get() = state == "started"
}

/** Parses the `AudioPlaybackConfiguration` lines of `dumpsys audio`. */
object PlaybackParser {
    private val piid = Regex("""piid:(\d+)""")
    private val uidPid = Regex("""u/pid:(\d+)/(\d+)""")
    private val state = Regex("""state:(\w+)""")
    private val usage = Regex("""usage=(\w+)""")
    private val session = Regex("""sessionId:(-?\d+)""")

    fun parse(text: String): List<Player> = text.lineSequence()
        .filter { it.contains("AudioPlaybackConfiguration") }
        .mapNotNull { line ->
            val up = uidPid.find(line) ?: return@mapNotNull null
            Player(
                piid = piid.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null,
                uid = up.groupValues[1].toInt(),
                pid = up.groupValues[2].toInt(),
                state = state.find(line)?.groupValues?.get(1) ?: "unknown",
                usage = usage.find(line)?.groupValues?.get(1) ?: "USAGE_UNKNOWN",
                sessionId = session.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            )
        }.toList()

    /** Usages we compress; notifications, alarms, calls and system sounds are left alone. */
    private val compressibleUsages = setOf("USAGE_MEDIA", "USAGE_GAME", "USAGE_UNKNOWN", "USAGE_ASSISTANT")

    fun isCompressible(p: Player, ownUid: Int) =
        p.uid != ownUid && p.uid >= 10000 && p.sessionId > 0 && p.usage in compressibleUsages
}
