package io.github.linuxkernel44.autocomp.spike

import android.media.audiofx.DynamicsProcessing
import android.util.Log

/** Phase 0 spike: try several DynamicsProcessing configurations on an arbitrary session. */
object SpikeCompressor {
    private const val TAG = "AutoCompSpike"
    private val effects = HashMap<Int, DynamicsProcessing>()

    private fun tryCfg(name: String, session: Int, f: () -> DynamicsProcessing): DynamicsProcessing? = try {
        f().also { Log.i(TAG, "OK   $name session=$session") }
    } catch (t: Throwable) { Log.e(TAG, "FAIL $name session=$session: $t"); null }

    fun attach(session: Int): String {
        effects.remove(session)?.release()
        val variants = listOf(
            "default-null" to { DynamicsProcessing(0, session, null) },
            "default-ctor" to { DynamicsProcessing(session) },
            "freq-2ch-pre0-mbc1-post0" to { DynamicsProcessing(0, session, DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, true, 1, false, 0, true).build()) },
            "time-1ch-mbc1" to { DynamicsProcessing(0, session, DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, 1, false, 0, true, 1, false, 0, false).build()) },
            "freq-2ch-mbc1-eq1" to { DynamicsProcessing(0, session, DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, 1, true, 1, true, 1, true).build()) },
        )
        for ((n, f) in variants) {
            val dp = tryCfg(n, session, f) ?: continue
            effects[session] = dp
            return configure(dp, session, n)
        }
        return "all failed"
    }

    private fun configure(dp: DynamicsProcessing, session: Int, n: String): String = try {
        val c = dp.config
        Log.i(TAG, "cfg ch=${dp.channelCount} cfg=$c")
        val mbc = dp.getMbcByChannelIndex(0)
        Log.i(TAG, "mbc bands=${mbc.bandCount} inUse=${mbc.isInUse} band0=${mbc.getBand(0)}")
        val band = DynamicsProcessing.MbcBand(true, 20000f, 5f, 100f, 10f, -40f, 3f, -90f, 1f, 0f, 12f)
        for (ch in 0 until dp.channelCount) dp.setMbcBandByChannelIndex(ch, 0, band)
        val rc = dp.setEnabled(true)
        "configured via $n enable=$rc control=${dp.hasControl()}".also { Log.i(TAG, it) }
    } catch (t: Throwable) { "configure FAILED: $t".also { Log.e(TAG, it, t) } }

    fun release(session: Int) { effects.remove(session)?.release() }
    fun releaseAll() { effects.values.forEach { it.release() }; effects.clear() }
}
