package io.github.linuxkernel44.autocomp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.linuxkernel44.autocomp.R
import io.github.linuxkernel44.autocomp.app
import io.github.linuxkernel44.autocomp.core.*
import java.util.Locale

private val BAND_NAMES = listOf("< 220 Hz", "220–540 Hz", "540 Hz–1.3 kHz", "1.3–3.3 kHz", "3.3–8.1 kHz", "> 8.1 kHz")

@Composable
fun AdvancedScreen(onBack: () -> Unit) {
    val store = LocalContext.current.app.settings
    val s by store.flow.collectAsState()
    val p = s.custom
    val shown = Presets.params(s.preset, s.custom)

    fun edit(f: (CompressorParams) -> CompressorParams) = store.update {
        // Start from whatever preset is active so editing never jumps.
        it.copy(preset = PresetId.CUSTOM, custom = f(Presets.params(it.preset, it.custom)).clamped())
    }

    ScreenScaffold(stringResource(R.string.adv_title), onBack) {
        Text(stringResource(R.string.adv_note), style = MaterialTheme.typography.bodySmall)
        PresetSelector(s.preset) { sel -> store.update { it.copy(preset = sel) } }

        TransferCurve(shown.bands.first(), shown.kneeDb, shown.limiterThresholdDb)

        val b0 = shown.bands.first()
        if (!shown.multiband) {
            ParamSlider(stringResource(R.string.threshold), b0.thresholdDb, -80f..0f, "dB") { v -> edit { c -> c.uniform(v, c.bands[0].ratio, c.bands[0].makeupDb).copy(multiband = false, attackMs = c.attackMs, releaseMs = c.releaseMs, kneeDb = c.kneeDb, limiterThresholdDb = c.limiterThresholdDb) } }
            ParamSlider(stringResource(R.string.ratio), b0.ratio, 1f..20f, ":1") { v -> edit { c -> c.uniform(c.bands[0].thresholdDb, v, c.bands[0].makeupDb).copy(multiband = false) } }
            ParamSlider(stringResource(R.string.makeup), b0.makeupDb, -12f..24f, "dB") { v -> edit { c -> c.uniform(c.bands[0].thresholdDb, c.bands[0].ratio, v).copy(multiband = false) } }
        }
        ParamSlider(stringResource(R.string.attack), shown.attackMs, 0.5f..200f, "ms") { v -> edit { it.copy(attackMs = v) } }
        ParamSlider(stringResource(R.string.release), shown.releaseMs, 10f..2000f, "ms") { v -> edit { it.copy(releaseMs = v) } }
        ParamSlider(stringResource(R.string.knee), shown.kneeDb, 0f..24f, "dB") { v -> edit { it.copy(kneeDb = v) } }
        ParamSlider(stringResource(R.string.limiter), shown.limiterThresholdDb, -20f..0f, "dB") { v -> edit { it.copy(limiterThresholdDb = v) } }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.multiband), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(shown.multiband, { on -> edit { it.copy(multiband = on) } })
        }
        if (shown.multiband) {
            shown.bands.forEachIndexed { i, b ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(BAND_NAMES[i], Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.band_enabled), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(8.dp))
                            Switch(b.enabled, { on -> edit { c -> c.copy(multiband = true, bands = c.bands.toMutableList().also { l -> l[i] = b.copy(enabled = on) }) } })
                        }
                        ParamSlider(stringResource(R.string.threshold), b.thresholdDb, -80f..0f, "dB") { v -> edit { c -> c.copy(multiband = true, bands = c.bands.toMutableList().also { l -> l[i] = b.copy(thresholdDb = v) }) } }
                        ParamSlider(stringResource(R.string.ratio), b.ratio, 1f..20f, ":1") { v -> edit { c -> c.copy(multiband = true, bands = c.bands.toMutableList().also { l -> l[i] = b.copy(ratio = v) }) } }
                        ParamSlider(stringResource(R.string.makeup), b.makeupDb, -12f..24f, "dB") { v -> edit { c -> c.copy(multiband = true, bands = c.bands.toMutableList().also { l -> l[i] = b.copy(makeupDb = v) }) } }
                    }
                }
            }
        }
    }
}

@Composable
fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(String.format(Locale.ROOT, "%.1f %s", local, unit), style = MaterialTheme.typography.labelLarge)
        }
        Slider(local, { local = it }, valueRange = range, onValueChangeFinished = { onChange(local) })
    }
}

/** Static transfer curve (input dB -> output dB) of one band, with makeup and limiter. */
@Composable
private fun TransferCurve(b: BandParams, kneeDb: Float, limiterDb: Float) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val lo = -80f; val hi = 6f
        fun x(db: Float) = (db - lo) / (hi - lo) * size.width
        fun y(db: Float) = size.height - (db - lo) / (hi - lo) * size.height
        drawLine(grid, Offset(0f, y(0f)), Offset(size.width, y(0f)))
        drawLine(grid, Offset(x(0f), 0f), Offset(x(0f), size.height))
        drawLine(grid, Offset(x(lo), y(lo)), Offset(x(hi), y(hi)))
        val path = Path()
        var first = true
        var inDb = lo
        while (inDb <= hi) {
            val out = minOf(compressDb(inDb, b, kneeDb), limiterDb).coerceIn(lo, hi)
            if (first) { path.moveTo(x(inDb), y(out)); first = false } else path.lineTo(x(inDb), y(out))
            inDb += 1f
        }
        drawPath(path, line, style = Stroke(width = 4f))
    }
}

/** Soft-knee compressor static curve plus makeup gain. */
fun compressDb(inDb: Float, b: BandParams, knee: Float): Float {
    val over = inDb - b.thresholdDb
    val slope = 1f / b.ratio - 1f
    val out = when {
        2 * over < -knee -> inDb
        2 * kotlin.math.abs(over) <= knee -> inDb + slope * (over + knee / 2).let { it * it } / (2 * knee)
        else -> b.thresholdDb + over / b.ratio
    }
    return out + b.makeupDb
}
