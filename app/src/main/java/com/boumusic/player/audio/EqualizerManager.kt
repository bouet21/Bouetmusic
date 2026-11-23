package com.boumusic.player.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

data class EqualizerBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val minLevel: Short,
    val maxLevel: Short,
    val currentLevel: Short
)

class EqualizerManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("boumusic_audio_prefs", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var isEnabled: Boolean = prefs.getBoolean("eq_enabled", true)
        private set

    var isSupported: Boolean = false
        private set

    var currentPresetIndex: Short = prefs.getInt("current_preset", -1).toShort()
        private set

    var bassStrength: Short = prefs.getInt("bass_strength", 0).toShort()
        private set

    var isLoudnessEnabled: Boolean = prefs.getBoolean("loudness_enabled", false)
        private set

    var loudnessGainMb: Int = prefs.getInt("loudness_gain", 600)
        private set

    private val fallbackPresets = listOf(
        "Normal", "Rock", "Pop", "Clásica", "Jazz", "Electrónica", "Refuerzo de graves"
    )

    private val fallbackBands = listOf(
        EqualizerBand(0, 60, -1500, 1500, 0),
        EqualizerBand(1, 230, -1500, 1500, 0),
        EqualizerBand(2, 910, -1500, 1500, 0),
        EqualizerBand(3, 3600, -1500, 1500, 0),
        EqualizerBand(4, 14000, -1500, 1500, 0)
    )

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            release()
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = isEnabled
                    if (currentPresetIndex >= 0 && currentPresetIndex < numberOfPresets) {
                        try {
                            usePreset(currentPresetIndex)
                        } catch (ignored: Throwable) {}
                    } else {
                        try {
                            for (i in 0 until numberOfBands) {
                                val b = i.toShort()
                                if (prefs.contains("band_$b")) {
                                    setBandLevel(b, prefs.getInt("band_$b", 0).toShort())
                                }
                            }
                        } catch (ignored: Throwable) {}
                    }
                }
                isSupported = true
            } catch (e: Throwable) {
                equalizer = null
                isSupported = false
            }

            try {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = isEnabled
                    if (strengthSupported) {
                        setStrength(bassStrength)
                    }
                }
            } catch (e: Throwable) {
                bassBoost = null
            }

            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = isLoudnessEnabled
                    setTargetGain(loudnessGainMb)
                }
            } catch (e: Throwable) {
                loudnessEnhancer = null
            }
        } catch (e: Throwable) {
            isSupported = false
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        isLoudnessEnabled = enabled
        prefs.edit().putBoolean("loudness_enabled", enabled).apply()
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        loudnessGainMb = gainMb.coerceIn(0, 2000)
        prefs.edit().putInt("loudness_gain", loudnessGainMb).apply()
        try {
            loudnessEnhancer?.setTargetGain(loudnessGainMb)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun getBands(): List<EqualizerBand> {
        val list = mutableListOf<EqualizerBand>()
        val eq = equalizer
        if (eq != null) {
            try {
                val range = eq.bandLevelRange
                val min = range[0]
                val max = range[1]
                for (i in 0 until eq.numberOfBands) {
                    val band = i.toShort()
                    val freq = eq.getCenterFreq(band) / 1000
                    val level = eq.getBandLevel(band)
                    list.add(EqualizerBand(band, freq, min, max, level))
                }
            } catch (e: Throwable) {
                list.clear()
            }
        }
        return if (list.isNotEmpty()) list else fallbackBands.map { b ->
            val saved = prefs.getInt("band_${b.bandIndex}", b.currentLevel.toInt()).toShort()
            b.copy(currentLevel = saved)
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            currentPresetIndex = -1
            prefs.edit()
                .putInt("current_preset", -1)
                .putInt("band_$band", level.toInt())
                .apply()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun getPresetNames(): List<String> {
        val list = mutableListOf<String>()
        val eq = equalizer
        if (eq != null) {
            try {
                val numPresets = eq.numberOfPresets
                for (i in 0 until numPresets) {
                    try {
                        val name = eq.getPresetName(i.toShort())
                        if (!name.isNullOrBlank()) {
                            list.add(name)
                        }
                    } catch (e: Throwable) {
                        break
                    }
                }
            } catch (e: Throwable) {
                list.clear()
            }
        }
        return if (list.isNotEmpty()) list else fallbackPresets
    }

    fun applyPreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            currentPresetIndex = presetIndex
            prefs.edit().putInt("current_preset", presetIndex.toInt()).apply()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun setBassBoostStrength(strength: Short) {
        bassStrength = strength.coerceIn(0, 1000)
        prefs.edit().putInt("bass_strength", bassStrength.toInt()).apply()
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bassStrength)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (ignored: Throwable) {}
        equalizer = null
        try {
            bassBoost?.release()
        } catch (ignored: Throwable) {}
        bassBoost = null
        try {
            loudnessEnhancer?.release()
        } catch (ignored: Throwable) {}
        loudnessEnhancer = null
    }
}
