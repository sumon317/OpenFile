package com.example.core.media

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int
) {
    val label: String
        get() = when {
            centerFreqHz >= 1000 && centerFreqHz % 1000 == 0 -> "${centerFreqHz / 1000}kHz"
            centerFreqHz >= 1000 -> String.format(Locale.US, "%.1fkHz", centerFreqHz / 1000f)
            else -> "${centerFreqHz}Hz"
        }

    val roleLabel: String
        get() = when {
            centerFreqHz < 120 -> "SUB"
            centerFreqHz < 450 -> "BASS"
            centerFreqHz < 1800 -> "MID"
            centerFreqHz < 6500 -> "PRES"
            else -> "AIR"
        }

    val levelDb: Float
        get() = levelMb / 100f
}

data class EqualizerPreset(
    val name: String,
    val levelsMb: List<Int>,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudnessGainMb: Int = 0,
    val category: String = "Studio"
)

data class EqualizerUiState(
    val enabled: Boolean = false,
    val selectedPreset: String = "Flat",
    val bands: List<EqualizerBand> = defaultBands(),
    val bassBoostStrength: Int = 0,     // 0..1000
    val virtualizerStrength: Int = 0,   // 0..1000
    val loudnessGainMb: Int = 0,        // 0..1500
    val isHardwareEffectBound: Boolean = false
) {
    val estimatedPeakBoostDb: Float
        get() {
            val maxBandDb = bands.maxOfOrNull { it.levelDb.coerceAtLeast(0f) } ?: 0f
            val bassDb = (bassBoostStrength / 1000f) * 3.5f
            val preampDb = loudnessGainMb / 100f
            return maxBandDb + bassDb + preampDb
        }

    companion object {
        val BUILTIN_PRESETS = listOf(
            EqualizerPreset("Flat", listOf(0, 0, 0, 0, 0), 0, 0, 0, "Reference"),
            EqualizerPreset("Studio Monitor", listOf(200, 100, 0, 150, 300), 150, 100, 150, "Reference"),
            EqualizerPreset("Cinema", listOf(350, 150, -100, 200, 400), 450, 500, 250, "Cinema"),
            EqualizerPreset("Bass Boost", listOf(750, 500, 100, 0, 150), 800, 150, 200, "Bass"),
            EqualizerPreset("Vocal Clarity", listOf(-250, 100, 550, 450, 200), 0, 200, 200, "Vocal"),
            EqualizerPreset("Electronic", listOf(600, 400, -150, 350, 650), 600, 400, 300, "Music"),
            EqualizerPreset("Acoustic", listOf(250, 150, 200, 300, 350), 100, 250, 100, "Music"),
            EqualizerPreset("Jazz", listOf(300, 200, -150, 250, 400), 200, 350, 150, "Music"),
            EqualizerPreset("Rock", listOf(500, 300, -100, 350, 550), 450, 250, 200, "Music"),
            EqualizerPreset("Night Mode", listOf(-300, -150, 200, -100, -350), 0, 100, -200, "Night")
        )

        fun defaultBands(): List<EqualizerBand> {
            val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
            return defaultFreqs.mapIndexed { idx, freq ->
                EqualizerBand(
                    index = idx,
                    centerFreqHz = freq,
                    levelMb = 0,
                    minLevelMb = -1500,
                    maxLevelMb = 1500
                )
            }
        }
    }
}

object EqualizerEngine {
    private val effectExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "openfile-equalizer-dsp").apply { isDaemon = true }
    }
    private val applyPending = AtomicBoolean(false)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedAudioSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()
    val presets: List<EqualizerPreset> get() = EqualizerUiState.BUILTIN_PRESETS

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("openfile_eq_settings", Context.MODE_PRIVATE)
            loadSavedSettings()
        }
    }

    private fun loadSavedSettings() {
        val p = prefs ?: return
        val enabled = p.getBoolean("eq_enabled", false)
        val preset = p.getString("eq_preset", "Flat") ?: "Flat"
        val bass = p.getInt("eq_bass", 0)
        val virt = p.getInt("eq_virt", 0)
        val gain = p.getInt("eq_gain", 0)
        val levelsStr = p.getString("eq_levels", null)

        val levels = levelsStr?.split(",")?.mapNotNull { it.toIntOrNull() }

        _state.update { cur ->
            val updatedBands = if (levels != null && levels.size == cur.bands.size) {
                cur.bands.mapIndexed { i, b -> b.copy(levelMb = levels[i]) }
            } else {
                cur.bands
            }
            cur.copy(
                enabled = enabled,
                selectedPreset = preset,
                bassBoostStrength = bass,
                virtualizerStrength = virt,
                loudnessGainMb = gain,
                bands = updatedBands
            )
        }
    }

    private fun persistSettings() {
        val p = prefs ?: return
        val cur = _state.value
        p.edit()
            .putBoolean("eq_enabled", cur.enabled)
            .putString("eq_preset", cur.selectedPreset)
            .putInt("eq_bass", cur.bassBoostStrength)
            .putInt("eq_virt", cur.virtualizerStrength)
            .putInt("eq_gain", cur.loudnessGainMb)
            .putString("eq_levels", cur.bands.joinToString(",") { it.levelMb.toString() })
            .apply()
    }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedAudioSessionId) return
        attachedAudioSessionId = audioSessionId

        effectExecutor.execute {
            releaseEffectsInternal()
            runCatching {
                val eq = Equalizer(0, audioSessionId)
                val numBands = eq.numberOfBands.toInt()
                val minLevel = eq.bandLevelRange[0].toInt()
                val maxLevel = eq.bandLevelRange[1].toInt()

                val configuredBands = (0 until numBands).map { idx ->
                    val centerFreq = eq.getCenterFreq(idx.toShort()) / 1000
                    val existingLevel = _state.value.bands.getOrNull(idx)?.levelMb ?: 0
                    EqualizerBand(
                        index = idx,
                        centerFreqHz = centerFreq,
                        levelMb = existingLevel.coerceIn(minLevel, maxLevel),
                        minLevelMb = minLevel,
                        maxLevelMb = maxLevel
                    )
                }

                equalizer = eq
                bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
                virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
                loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

                _state.update {
                    it.copy(
                        bands = if (configuredBands.isNotEmpty()) configuredBands else it.bands,
                        isHardwareEffectBound = true
                    )
                }
                applyAllEffectsInternal()
            }.onFailure {
                _state.update { s -> s.copy(isHardwareEffectBound = false) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        scheduleApplyAllEffects()
        persistSettings()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        _state.update { current ->
            val updatedBands = current.bands.map { band ->
                if (band.index == bandIndex) {
                    band.copy(levelMb = levelMb.coerceIn(band.minLevelMb, band.maxLevelMb))
                } else {
                    band
                }
            }
            current.copy(
                enabled = true,
                selectedPreset = "Custom",
                bands = updatedBands
            )
        }
        scheduleApplyAllEffects()
        persistSettings()
    }

    fun applyPreset(preset: EqualizerPreset) {
        _state.update { current ->
            val updatedBands = current.bands.mapIndexed { idx, band ->
                val targetLevel = preset.levelsMb.getOrElse(idx) { 0 }
                band.copy(levelMb = targetLevel.coerceIn(band.minLevelMb, band.maxLevelMb))
            }
            current.copy(
                enabled = true,
                selectedPreset = preset.name,
                bands = updatedBands,
                bassBoostStrength = preset.bassBoost,
                virtualizerStrength = preset.virtualizer,
                loudnessGainMb = preset.loudnessGainMb
            )
        }
        scheduleApplyAllEffects()
        persistSettings()
    }

    fun setBassBoost(strength: Int) {
        _state.update { current ->
            current.copy(
                enabled = true,
                selectedPreset = if (current.selectedPreset == "Flat") "Custom" else current.selectedPreset,
                bassBoostStrength = strength.coerceIn(0, 1000)
            )
        }
        scheduleApplyAllEffects()
        persistSettings()
    }

    fun setVirtualizer(strength: Int) {
        _state.update { current ->
            current.copy(
                enabled = true,
                selectedPreset = if (current.selectedPreset == "Flat") "Custom" else current.selectedPreset,
                virtualizerStrength = strength.coerceIn(0, 1000)
            )
        }
        scheduleApplyAllEffects()
        persistSettings()
    }

    fun setLoudnessGain(gainMb: Int) {
        _state.update { current ->
            current.copy(
                enabled = true,
                selectedPreset = if (current.selectedPreset == "Flat") "Custom" else current.selectedPreset,
                loudnessGainMb = gainMb.coerceIn(0, 1500)
            )
        }
        scheduleApplyAllEffects()
        persistSettings()
    }

    fun resetToFlat() {
        applyPreset(EqualizerUiState.BUILTIN_PRESETS.first())
    }

    private fun scheduleApplyAllEffects() {
        if (applyPending.compareAndSet(false, true)) {
            effectExecutor.execute {
                applyPending.set(false)
                applyAllEffectsInternal()
            }
        }
    }

    private fun applyAllEffectsInternal() {
        val current = _state.value
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = current.enabled
                if (current.enabled) {
                    current.bands.forEach { band ->
                        if (band.index < eq.numberOfBands) {
                            eq.setBandLevel(band.index.toShort(), band.levelMb.toShort())
                        }
                    }
                }
            }
        }
        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = current.enabled && current.bassBoostStrength > 0
                if (bb.strengthSupported && current.enabled) {
                    bb.setStrength(current.bassBoostStrength.toShort())
                }
            }
        }
        runCatching {
            virtualizer?.let { vz ->
                vz.enabled = current.enabled && current.virtualizerStrength > 0
                if (vz.strengthSupported && current.enabled) {
                    vz.setStrength(current.virtualizerStrength.toShort())
                }
            }
        }
        runCatching {
            loudnessEnhancer?.let { le ->
                le.enabled = current.enabled && current.loudnessGainMb > 0
                if (current.enabled) {
                    le.setTargetGain(current.loudnessGainMb)
                }
            }
        }
    }

    fun releaseEffects() {
        effectExecutor.execute {
            releaseEffectsInternal()
        }
    }

    private fun releaseEffectsInternal() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        attachedAudioSessionId = 0
    }
}
