package com.example.core.media

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
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
    val isHardwareEffectBound: Boolean = false,
    val isSpatializerAvailable: Boolean = false,
    val isSpatializerEnabled: Boolean = false
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
            EqualizerPreset("Night Mode", listOf(-300, -150, 200, -100, -350), 0, 100, 0, "Night")
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

/**
 * Backward-compatible wrapper for [android.media.audiofx.Virtualizer].
 *
 * Android API level 35+ (Android 15) deprecated [android.media.audiofx.Virtualizer] in favor of platform-level
 * [android.media.Spatializer] and [android.media.AudioAttributes.Builder.setSpatializationBehavior].
 * For backward compatibility with older Android platforms (API 24-34) and session-level DSP processing,
 * this wrapper encapsulates the legacy effect with appropriate deprecation suppression.
 */
@Suppress("DEPRECATION")
private class VirtualizerCompat private constructor(
    private val effect: android.media.audiofx.Virtualizer
) {
    var enabled: Boolean
        get() = runCatching { effect.enabled }.getOrDefault(false)
        set(value) { runCatching { effect.enabled = value } }

    val strengthSupported: Boolean
        get() = runCatching { effect.strengthSupported }.getOrDefault(false)

    fun setStrength(strength: Short) {
        runCatching { effect.setStrength(strength) }
    }

    fun release() {
        runCatching { effect.release() }
    }

    companion object {
        fun create(audioSessionId: Int): VirtualizerCompat? {
            return runCatching {
                val effect = android.media.audiofx.Virtualizer(0, audioSessionId)
                VirtualizerCompat(effect)
            }.getOrNull()
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
    private var virtualizer: VirtualizerCompat? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    @Volatile
    private var attachedAudioSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()
    val presets: List<EqualizerPreset> get() = EqualizerUiState.BUILTIN_PRESETS

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (prefs == null) {
            prefs = appContext.getSharedPreferences("openfile_eq_settings", Context.MODE_PRIVATE)
            loadSavedSettings()
        }
        querySpatializerSupport(appContext)
    }

    private fun querySpatializerSupport(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            runCatching {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val spatializer = audioManager?.spatializer
                if (spatializer != null) {
                    val available = spatializer.isAvailable
                    val enabled = spatializer.isEnabled
                    _state.update { it.copy(isSpatializerAvailable = available, isSpatializerEnabled = enabled) }
                }
            }
        }
    }

    private fun loadSavedSettings() {
        val p = prefs ?: return
        val enabled = p.getBoolean("eq_enabled", false)
        val preset = p.getString("eq_preset", "Flat") ?: "Flat"
        val bass = p.getInt("eq_bass", 0).coerceIn(0, 1000)
        val virt = p.getInt("eq_virt", 0).coerceIn(0, 1000)
        val gain = p.getInt("eq_gain", 0).coerceIn(0, 1500)
        val levelsStr = p.getString("eq_levels", null)

        val levels = levelsStr?.split(",")?.mapNotNull { it.toIntOrNull() }

        _state.update { cur ->
            val updatedBands = if (levels != null && levels.size == cur.bands.size) {
                cur.bands.mapIndexed { i, b -> b.copy(levelMb = levels[i].coerceIn(b.minLevelMb, b.maxLevelMb)) }
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
                val numBands = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
                val range = runCatching { eq.bandLevelRange }.getOrNull()
                val minLevel = range?.getOrNull(0)?.toInt() ?: -1500
                val maxLevel = range?.getOrNull(1)?.toInt() ?: 1500

                val configuredBands = (0 until numBands).mapNotNull { idx ->
                    runCatching {
                        val centerFreq = eq.getCenterFreq(idx.toShort()) / 1000
                        val existingLevel = _state.value.bands.getOrNull(idx)?.levelMb ?: 0
                        EqualizerBand(
                            index = idx,
                            centerFreqHz = centerFreq,
                            levelMb = existingLevel.coerceIn(minLevel, maxLevel),
                            minLevelMb = minLevel,
                            maxLevelMb = maxLevel
                        )
                    }.getOrNull()
                }

                equalizer = eq
                bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
                virtualizer = VirtualizerCompat.create(audioSessionId)
                loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

                _state.update {
                    it.copy(
                        bands = if (configuredBands.isNotEmpty()) configuredBands else it.bands,
                        isHardwareEffectBound = true
                    )
                }
                applyAllEffectsInternal()
            }.onFailure {
                attachedAudioSessionId = 0
                _state.update { s -> s.copy(isHardwareEffectBound = false) }
            }
        }
    }

    fun detachAudioSession(audioSessionId: Int) {
        if (audioSessionId > 0 && audioSessionId == attachedAudioSessionId) {
            releaseEffects()
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
                bassBoostStrength = preset.bassBoost.coerceIn(0, 1000),
                virtualizerStrength = preset.virtualizer.coerceIn(0, 1000),
                loudnessGainMb = preset.loudnessGainMb.coerceIn(0, 1500)
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
                    val numBands = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
                    val range = runCatching { eq.bandLevelRange }.getOrNull()
                    val minLevel = range?.getOrNull(0)?.toInt() ?: -1500
                    val maxLevel = range?.getOrNull(1)?.toInt() ?: 1500
                    current.bands.forEach { band ->
                        if (band.index in 0 until numBands) {
                            runCatching {
                                eq.setBandLevel(
                                    band.index.toShort(),
                                    band.levelMb.coerceIn(minLevel, maxLevel).toShort()
                                )
                            }
                        }
                    }
                }
            }
        }
        runCatching {
            bassBoost?.let { bb ->
                val bass = current.bassBoostStrength.coerceIn(0, 1000)
                bb.enabled = current.enabled && bass > 0
                if (bb.strengthSupported && current.enabled) {
                    bb.setStrength(bass.toShort())
                }
            }
        }
        runCatching {
            virtualizer?.let { vz ->
                val virt = current.virtualizerStrength.coerceIn(0, 1000)
                vz.enabled = current.enabled && virt > 0
                if (vz.strengthSupported && current.enabled) {
                    vz.setStrength(virt.toShort())
                }
            }
        }
        runCatching {
            loudnessEnhancer?.let { le ->
                val gain = current.loudnessGainMb.coerceIn(0, 1500)
                le.enabled = current.enabled && gain > 0
                if (current.enabled && gain > 0) {
                    le.setTargetGain(gain)
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
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
        }
        runCatching {
            bassBoost?.enabled = false
            bassBoost?.release()
        }
        runCatching {
            virtualizer?.enabled = false
            virtualizer?.release()
        }
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        attachedAudioSessionId = 0
        _state.update { it.copy(isHardwareEffectBound = false) }
    }
}
