package io.github.linuxkernel44.autocomp.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val enabled: Boolean = false,
    val startOnBoot: Boolean = true,
    val preset: PresetId = PresetId.NORMAL,
    val custom: CompressorParams = Presets.normal,
    val profiles: Map<String, String> = emptyMap(),
) {
    /** Params to use for [pkg], or null when the app must be left untouched. */
    fun paramsFor(pkg: String?): CompressorParams? {
        val ref = profiles[pkg] ?: ProfileRef.DEFAULT
        return when (ref) {
            ProfileRef.BYPASS -> null
            ProfileRef.DEFAULT -> Presets.params(preset, custom)
            else -> Presets.params(runCatching { PresetId.valueOf(ref) }.getOrDefault(preset), custom)
        }
    }

    fun profileLabelFor(pkg: String?): String = profiles[pkg] ?: ProfileRef.DEFAULT
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Settings> = _flow

    val value get() = _flow.value

    fun update(block: (Settings) -> Settings) {
        val next = block(_flow.value)
        if (next == _flow.value) return
        _flow.value = next
        prefs.edit().putString("json", encode(next).toString()).apply()
    }

    private fun load(): Settings = runCatching { decode(JSONObject(prefs.getString("json", null) ?: return Settings())) }
        .getOrDefault(Settings())

    companion object {
        fun encode(s: Settings) = JSONObject().apply {
            put("enabled", s.enabled)
            put("startOnBoot", s.startOnBoot)
            put("preset", s.preset.name)
            put("custom", encodeParams(s.custom))
            put("profiles", JSONObject(s.profiles as Map<*, *>))
        }

        fun decode(o: JSONObject): Settings {
            val profiles = o.optJSONObject("profiles")?.let { p -> p.keys().asSequence().associateWith { p.getString(it) } }.orEmpty()
            return Settings(
                enabled = o.optBoolean("enabled", false),
                startOnBoot = o.optBoolean("startOnBoot", true),
                preset = runCatching { PresetId.valueOf(o.optString("preset")) }.getOrDefault(PresetId.NORMAL),
                custom = o.optJSONObject("custom")?.let { runCatching { decodeParams(it) }.getOrNull() } ?: Presets.normal,
                profiles = profiles,
            )
        }

        private fun encodeParams(p: CompressorParams) = JSONObject().apply {
            put("attack", p.attackMs.toDouble()); put("release", p.releaseMs.toDouble()); put("knee", p.kneeDb.toDouble())
            put("limiter", p.limiterThresholdDb.toDouble()); put("multiband", p.multiband)
            put("bands", JSONArray().also { a ->
                p.bands.forEach { b ->
                    a.put(JSONObject().put("on", b.enabled).put("thr", b.thresholdDb.toDouble())
                        .put("ratio", b.ratio.toDouble()).put("makeup", b.makeupDb.toDouble()))
                }
            })
        }

        private fun decodeParams(o: JSONObject): CompressorParams {
            val a = o.getJSONArray("bands")
            return CompressorParams(
                attackMs = o.getDouble("attack").toFloat(), releaseMs = o.getDouble("release").toFloat(),
                kneeDb = o.getDouble("knee").toFloat(), limiterThresholdDb = o.getDouble("limiter").toFloat(),
                multiband = o.optBoolean("multiband"),
                bands = List(BAND_COUNT) { i ->
                    val b = a.getJSONObject(i)
                    BandParams(b.getBoolean("on"), b.getDouble("thr").toFloat(), b.getDouble("ratio").toFloat(), b.getDouble("makeup").toFloat())
                },
            ).clamped()
        }
    }
}
