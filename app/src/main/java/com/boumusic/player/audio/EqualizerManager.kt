package com.boumusic.player.audio

import android.content.Context
import android.media.audiofx.Equalizer

class EqualizerManager(private val context: Context) {
    private var equalizer: Equalizer? = null

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        equalizer = null
    }
}
