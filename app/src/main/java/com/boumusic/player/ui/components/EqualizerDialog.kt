package com.boumusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.audio.EqualizerManager
import com.boumusic.player.ui.theme.DarkBackground
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.DarkSurfaceVariant
import com.boumusic.player.ui.theme.ElectricPurple
import com.boumusic.player.ui.theme.NeonCyan
import com.boumusic.player.ui.theme.TextPrimary
import com.boumusic.player.ui.theme.TextSecondary

@Composable
fun EqualizerDialog(
    equalizerManager: EqualizerManager,
    crossfadeSeconds: Int = 3,
    onCrossfadeChange: (Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(equalizerManager.isEnabled) }
    val presets = remember { equalizerManager.getPresetNames() }
    var selectedPresetIndex by remember { mutableIntStateOf(equalizerManager.currentPresetIndex.toInt()) }
    var bassStrength by remember { mutableFloatStateOf(equalizerManager.bassStrength.toFloat()) }
    var bands by remember { mutableStateOf(equalizerManager.getBands()) }
    var isLoudnessEnabled by remember { mutableStateOf(equalizerManager.isLoudnessEnabled) }
    var loudnessGain by remember { mutableFloatStateOf(equalizerManager.loudnessGainMb.toFloat()) }
    var crossfadeVal by remember { mutableIntStateOf(crossfadeSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Ecualizador & Audio", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Interruptor Activar / Desactivar Ecualizador
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isEnabled) "Ecualizador activado" else "Ecualizador desactivado",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            equalizerManager.setEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }

                if (isEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Presets
                    if (presets.isNotEmpty()) {
                        Text(
                            text = "PRESETS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(presets) { index, name ->
                                FilterChip(
                                    selected = selectedPresetIndex == index,
                                    onClick = {
                                        selectedPresetIndex = index
                                        equalizerManager.applyPreset(index.toShort())
                                        bands = equalizerManager.getBands()
                                    },
                                    label = { Text(name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricPurple,
                                        selectedLabelColor = TextPrimary,
                                        containerColor = DarkSurfaceVariant,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Bandas de frecuencia
                    if (bands.isNotEmpty()) {
                        Text(
                            text = "BANDAS DE FRECUENCIA",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        bands.forEach { band ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val freqLabel = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz" else "${band.centerFreqHz} Hz"
                                    Text(freqLabel, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    val db = (band.currentLevel.toFloat() / 100.0f)
                                    Text(String.format("%+.1f dB", db), color = TextSecondary, fontSize = 12.sp)
                                }

                                val minLevel = band.minLevel.toFloat()
                                val maxLevel = if (band.maxLevel > band.minLevel) band.maxLevel.toFloat() else (minLevel + 1f)
                                Slider(
                                    value = band.currentLevel.toFloat().coerceIn(minLevel, maxLevel),
                                    onValueChange = { newVal ->
                                        val newLevel = newVal.toInt().toShort()
                                        equalizerManager.setBandLevel(band.bandIndex, newLevel)
                                        bands = bands.map { b ->
                                            if (b.bandIndex == band.bandIndex) b.copy(currentLevel = newLevel) else b
                                        }
                                        selectedPresetIndex = -1
                                    },
                                    valueRange = minLevel..maxLevel,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan,
                                        inactiveTrackColor = DarkSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Refuerzo de graves (Bass Boost)
                    Text(
                        text = "REFUERZO DE GRAVES (BASS BOOST)",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Graves", color = ElectricPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(bassStrength / 10).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                    }

                    Slider(
                        value = bassStrength,
                        onValueChange = {
                            bassStrength = it
                            equalizerManager.setBassBoostStrength(it.toInt().toShort())
                        },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricPurple,
                            activeTrackColor = ElectricPurple,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Normalización de volumen (Loudness Enhancer / ReplayGain)
                Text(
                    text = "NORMALIZACIÓN DE VOLUMEN (LOUDNESS)",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nivelar volumen (Anti-sustos)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Evita saltos bruscos de volumen entre canciones", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isLoudnessEnabled,
                        onCheckedChange = {
                            isLoudnessEnabled = it
                            equalizerManager.setLoudnessEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }

                if (isLoudnessEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Realce de volumen", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(String.format("+%.1f dB", loudnessGain / 100.0f), color = TextSecondary, fontSize = 12.sp)
                    }
                    Slider(
                        value = loudnessGain,
                        onValueChange = {
                            loudnessGain = it
                            equalizerManager.setLoudnessGain(it.toInt())
                        },
                        valueRange = 0f..1500f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Crossfade (Fundido entre canciones)
                Text(
                    text = "FUNDIDO CRUZADO (CROSSFADE)",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Transición entre canciones", color = ElectricPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (crossfadeVal == 0) "Desactivado" else "${crossfadeVal} segundos", color = TextSecondary, fontSize = 12.sp)
                }
                Slider(
                    value = crossfadeVal.toFloat(),
                    onValueChange = {
                        crossfadeVal = it.toInt()
                        onCrossfadeChange(crossfadeVal)
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricPurple,
                        activeTrackColor = ElectricPurple,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Listo", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        }
    )
}
