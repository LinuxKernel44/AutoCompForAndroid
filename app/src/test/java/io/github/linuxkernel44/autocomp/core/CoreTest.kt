package io.github.linuxkernel44.autocomp.core

import io.github.linuxkernel44.autocomp.ui.compressDb
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private val sample = """
      players:
      AudioPlaybackConfiguration piid:4119 deviceId:3 type:android.media.AudioTrack u/pid:10232/6982 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00 tags= bundle=null sessionId:13905
      AudioPlaybackConfiguration piid:63 deviceId:0 type:android.media.SoundPool u/pid:10036/7009 state:idle attr:AudioAttributes: usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:0
      10-30 09:25:29:601 new player piid:3911 uid/pid:10036/7009 type:android.media.MediaPlayer attr:AudioAttributes: usage=USAGE_UNKNOWN content=CONTENT_TYPE_UNKNOWN flags=0x800 tags= bundle=null session:0
    """.trimIndent()

    @Test fun parsesOnlyPlaybackConfigurationLines() {
        val p = PlaybackParser.parse(sample)
        assertEquals(2, p.size)
        assertEquals(Player(4119, 10232, 6982, "started", "USAGE_MEDIA", 13905), p[0])
    }

    @Test fun filtersCompressiblePlayers() {
        val p = PlaybackParser.parse(sample)
        assertTrue(PlaybackParser.isCompressible(p[0], ownUid = 10500))
        assertFalse("own uid", PlaybackParser.isCompressible(p[0], ownUid = 10232))
        assertFalse("system sound / session 0", PlaybackParser.isCompressible(p[1], ownUid = 10500))
    }

    @Test fun presetsAreWithinLimitsAndOrdered() {
        listOf(Presets.soft, Presets.normal, Presets.strong, Presets.night).forEach { assertEquals(it, it.clamped()) }
        assertTrue(Presets.strong.bands[0].ratio > Presets.normal.bands[0].ratio)
        assertTrue(Presets.strong.bands[0].thresholdDb < Presets.normal.bands[0].thresholdDb)
    }

    @Test fun clampingBoundsValues() {
        val c = CompressorParams.uniform(-200f, 99f, 99f, 0f, 99999f, 99f, 5f)
        assertEquals(-80f, c.bands[0].thresholdDb); assertEquals(20f, c.bands[0].ratio); assertEquals(24f, c.bands[0].makeupDb)
        assertEquals(0.5f, c.attackMs); assertEquals(2000f, c.releaseMs); assertEquals(24f, c.kneeDb); assertEquals(0f, c.limiterThresholdDb)
    }

    @Test fun settingsRoundTripAndProfiles() {
        val s = Settings(enabled = true, preset = PresetId.STRONG, custom = Presets.night,
            profiles = mapOf("a.b" to ProfileRef.BYPASS, "c.d" to "SOFT"))
        val back = SettingsStore.decode(JSONObject(SettingsStore.encode(s).toString()))
        assertEquals(s, back)
        assertNull(back.paramsFor("a.b"))
        assertEquals(Presets.soft, back.paramsFor("c.d"))
        assertEquals(Presets.strong, back.paramsFor("other"))
    }

    @Test fun transferCurveCompressesAboveThreshold() {
        val b = BandParams(true, -30f, 4f, 0f)
        assertEquals(-50f, compressDb(-50f, b, 0f), 0.01f)
        assertEquals(-25f, compressDb(-10f, b, 0f) , 0.01f)
    }
}
