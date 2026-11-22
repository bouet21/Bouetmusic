package com.boumusic.player.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

data class EqualizerBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val minLevel: Short,
    val maxLevel: Short,
    var currentLevel: Short
)

class EqualizerManager(private val context: Context) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    var isEnabled: Boolean = true
        private set

    var isSupported: Boolean = false
        private set

    var currentPresetIndex: Short = -1
        private set

    var bassStrength: Short = 0
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
        } catch (e: Throwable) {
            isSupported = false
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
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
        return if (list.isNotEmpty()) list else fallbackBands
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            currentPresetIndex = -1
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
                        // Algunos OEM lanzan excepción al consultar presets
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
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun setBassBoostStrength(strength: Short) {
        bassStrength = strength.coerceIn(0, 1000)
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
    }
}
