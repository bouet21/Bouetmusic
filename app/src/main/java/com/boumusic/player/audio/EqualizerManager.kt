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

    var currentPresetIndex: Short = -1
        private set

    var bassStrength: Short = 0
        private set

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isEnabled
                if (strengthSupported) {
                    setStrength(bassStrength)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBands(): List<EqualizerBand> {
        val list = mutableListOf<EqualizerBand>()
        val eq = equalizer ?: return list
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            currentPresetIndex = -1
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPresetNames(): List<String> {
        val list = mutableListOf<String>()
        val eq = equalizer ?: return list
        for (i in 0 until eq.numberOfPresets) {
            list.add(eq.getPresetName(i.toShort()))
        }
        return list
    }

    fun applyPreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            currentPresetIndex = presetIndex
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBassBoostStrength(strength: Short) {
        bassStrength = strength.coerceIn(0, 1000)
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bassStrength)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (ignored: Exception) {}
        equalizer = null
        try {
            bassBoost?.release()
        } catch (ignored: Exception) {}
        bassBoost = null
    }
}
