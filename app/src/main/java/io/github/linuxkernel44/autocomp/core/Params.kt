package io.github.linuxkernel44.autocomp.core

const val BAND_COUNT = 6

data class BandParams(
    val enabled: Boolean = true,
    val thresholdDb: Float,
    val ratio: Float,
    val makeupDb: Float,
) {
    fun clamped() = copy(
        thresholdDb = thresholdDb.coerceIn(-80f, 0f),
        ratio = ratio.coerceIn(1f, 20f),
        makeupDb = makeupDb.coerceIn(-12f, 24f),
    )
}

data class CompressorParams(
    val attackMs: Float = 10f,
    val releaseMs: Float = 200f,
    val kneeDb: Float = 6f,
    val limiterThresholdDb: Float = -1f,
    val multiband: Boolean = false,
    val bands: List<BandParams>,
) {
    init { require(bands.size == BAND_COUNT) { "bands must have $BAND_COUNT entries" } }

    fun clamped() = copy(
        attackMs = attackMs.coerceIn(0.5f, 200f),
        releaseMs = releaseMs.coerceIn(10f, 2000f),
        kneeDb = kneeDb.coerceIn(0f, 24f),
        limiterThresholdDb = limiterThresholdDb.coerceIn(-20f, 0f),
        bands = bands.map { it.clamped() },
    )

    /** Sets threshold/ratio/makeup on every band (single-band behaviour). */
    fun uniform(thresholdDb: Float, ratio: Float, makeupDb: Float) =
        copy(bands = List(BAND_COUNT) { BandParams(true, thresholdDb, ratio, makeupDb) }).clamped()

    companion object {
        fun uniform(thresholdDb: Float, ratio: Float, makeupDb: Float, attackMs: Float = 10f, releaseMs: Float = 200f,
                    kneeDb: Float = 6f, limiterDb: Float = -1f) =
            CompressorParams(attackMs, releaseMs, kneeDb, limiterDb, false,
                List(BAND_COUNT) { BandParams(true, thresholdDb, ratio, makeupDb) }).clamped()
    }
}

enum class PresetId { SOFT, NORMAL, STRONG, NIGHT, CUSTOM }

object Presets {
    val soft = CompressorParams.uniform(-24f, 2f, 3f, 15f, 250f, 8f, -1f)
    val normal = CompressorParams.uniform(-30f, 3f, 6f, 10f, 200f, 6f, -1f)
    val strong = CompressorParams.uniform(-36f, 5f, 9f, 5f, 150f, 4f, -1.5f)
    val night = CompressorParams.uniform(-42f, 6f, 10f, 3f, 120f, 3f, -6f)

    fun params(id: PresetId, custom: CompressorParams): CompressorParams = when (id) {
        PresetId.SOFT -> soft
        PresetId.NORMAL -> normal
        PresetId.STRONG -> strong
        PresetId.NIGHT -> night
        PresetId.CUSTOM -> custom
    }
}

/** Value stored per app: follow the global preset, bypass the app, or force a preset. */
object ProfileRef {
    const val DEFAULT = "default"
    const val BYPASS = "bypass"
}
