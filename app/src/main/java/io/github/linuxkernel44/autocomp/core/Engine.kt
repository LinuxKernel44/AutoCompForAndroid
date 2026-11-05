package io.github.linuxkernel44.autocomp.core

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import io.github.linuxkernel44.autocomp.shizuku.ShizukuConnector
import io.github.linuxkernel44.autocomp.shizuku.ShizukuState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.log10

data class ActiveApp(
    val pkg: String,
    val label: String,
    val sessionId: Int,
    val playing: Boolean,
    val compressed: Boolean,
    val error: String? = null,
    /** Output peak level in dBFS, or null when not measured. */
    val levelDb: Float? = null,
)

data class EngineState(val running: Boolean = false, val apps: List<ActiveApp> = emptyList())

/**
 * Tracks the audio sessions of other apps (via Shizuku) and keeps a DynamicsProcessing effect
 * configured on each of them.
 */
class Engine(
    private val context: Context,
    private val settings: SettingsStore,
    private val shizuku: ShizukuConnector,
) {
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private class Slot(val dp: DynamicsProcessing, var applied: CompressorParams?)

    private val slots = HashMap<Int, Slot>()
    private val failedUntil = HashMap<Int, Long>()
    private val errors = HashMap<Int, String>()
    private val visualizers = HashMap<Int, Visualizer>()
    private val levels = HashMap<Int, Float>()
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private val ownUid = Process.myUid()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pm = context.packageManager

    @Volatile var meterWanted = false
        set(v) { field = v; trigger.trySend(Unit) }

    fun poke() { trigger.trySend(Unit) }

    suspend fun run() = coroutineScope {
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) { trigger.trySend(Unit) }
        }
        audioManager.registerAudioPlaybackCallback(cb, Handler(Looper.getMainLooper()))
        val ticker = launch { while (true) { delay(2000); trigger.trySend(Unit) } }
        val sub = launch { settings.flow.collect { trigger.trySend(Unit) } }
        val subShizuku = launch { shizuku.state.collect { trigger.trySend(Unit) } }
        _state.value = EngineState(running = true)
        try {
            for (t in trigger) {
                runCatching { refresh() }.onFailure { Log.w(TAG, "refresh failed", it) }
            }
        } finally {
            audioManager.unregisterAudioPlaybackCallback(cb)
            ticker.cancel(); sub.cancel(); subShizuku.cancel()
            releaseAll()
            _state.value = EngineState(running = false)
        }
    }

    private suspend fun refresh() {
        val svc = shizuku.service
        if (shizuku.state.value != ShizukuState.READY || svc == null) {
            releaseAll(); publish(emptyList()); return
        }
        val text = withContext(Dispatchers.IO) { runCatching { svc.dumpAudio() }.getOrDefault("") }
        val players = PlaybackParser.parse(text).filter { PlaybackParser.isCompressible(it, ownUid) }
        val s = settings.value
        val now = System.currentTimeMillis()
        val seen = HashSet<Int>()
        val apps = ArrayList<ActiveApp>()
        for ((sessionId, group) in players.groupBy { it.sessionId }) {
            seen += sessionId
            val player = group.first()
            val pkg = pm.getPackagesForUid(player.uid)?.firstOrNull() ?: "uid:${player.uid}"
            val params = s.paramsFor(pkg)
            val playing = group.any { it.isStarted }
            var compressed = false
            if (params == null) {
                release(sessionId)
            } else {
                val slot = slots[sessionId] ?: create(sessionId, now)
                if (slot != null) {
                    if (slot.applied != params) { apply(slot, params); slot.applied = params }
                    compressed = true
                    errors.remove(sessionId)
                }
            }
            apps += ActiveApp(pkg, labelOf(pkg), sessionId, playing, compressed, errors[sessionId], levels[sessionId])
        }
        (slots.keys - seen).toList().forEach { release(it) }
        failedUntil.keys.retainAll(seen); errors.keys.retainAll(seen)
        updateMeters(apps.filter { it.playing && it.compressed }.map { it.sessionId })
        publish(apps.map { it.copy(levelDb = levels[it.sessionId]) })
    }

    private fun publish(apps: List<ActiveApp>) { _state.value = EngineState(true, apps.sortedByDescending { it.playing }) }

    private fun create(sessionId: Int, now: Long): Slot? {
        if ((failedUntil[sessionId] ?: 0) > now) return null
        return try {
            Slot(DynamicsProcessing(0, sessionId, null), null).also { slots[sessionId] = it }
        } catch (t: Throwable) {
            Log.w(TAG, "effect creation failed for session $sessionId", t)
            errors[sessionId] = t.message ?: t.javaClass.simpleName
            failedUntil[sessionId] = now + 10_000
            null
        }
    }

    private fun apply(slot: Slot, p: CompressorParams) {
        val dp = slot.dp
        for (c in 0 until dp.channelCount) {
            val count = minOf(BAND_COUNT, dp.getMbcByChannelIndex(c).bandCount)
            for (i in 0 until count) {
                val b = p.bands[i]
                val cutoff = dp.getMbcBandByChannelIndex(c, i).cutoffFrequency
                dp.setMbcBandByChannelIndex(c, i, DynamicsProcessing.MbcBand(
                    b.enabled, cutoff, p.attackMs, p.releaseMs, b.ratio, b.thresholdDb, p.kneeDb, -90f, 1f, 0f, b.makeupDb))
            }
            dp.setLimiterByChannelIndex(c, DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, p.limiterThresholdDb, 0f))
        }
        dp.setEnabled(true)
    }

    private fun release(sessionId: Int) {
        slots.remove(sessionId)?.let { runCatching { it.dp.release() } }
        visualizers.remove(sessionId)?.let { runCatching { it.release() } }
        levels.remove(sessionId)
    }

    private fun releaseAll() {
        (slots.keys + visualizers.keys).toList().forEach { release(it) }
    }

    private fun updateMeters(sessions: List<Int>) {
        val allowed = meterWanted && context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val wanted = if (allowed) sessions.take(4).toSet() else emptySet()
        (visualizers.keys - wanted).toList().forEach { visualizers.remove(it)?.let { v -> runCatching { v.release() } }; levels.remove(it) }
        for (sid in wanted - visualizers.keys) {
            runCatching {
                val v = Visualizer(sid)
                v.captureSize = Visualizer.getCaptureSizeRange()[0]
                v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer?, wf: ByteArray?, rate: Int) {
                        wf ?: return
                        var peak = 0
                        for (b in wf) peak = maxOf(peak, abs((b.toInt() and 0xFF) - 128))
                        levels[sid] = if (peak == 0) -90f else (20 * log10(peak / 128f)).coerceAtLeast(-90f)
                    }
                    override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                v.enabled = true
                visualizers[sid] = v
            }.onFailure { Log.w(TAG, "visualizer failed for $sid", it) }
        }
    }

    private val labels = HashMap<String, String>()
    private fun labelOf(pkg: String) = labels.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    companion object { private const val TAG = "AutoCompEngine" }
}
