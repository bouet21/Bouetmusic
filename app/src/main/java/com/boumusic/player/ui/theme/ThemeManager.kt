package com.boumusic.player.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream

data class ThemePreset(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBorder: Color
)

object AppThemeState {
    var primaryColor by mutableStateOf(Color(0xFF00E5FF))
    var secondaryColor by mutableStateOf(Color(0xFF7C4DFF))
    var backgroundColor by mutableStateOf(Color(0xFF0F0F13))
    var surfaceColor by mutableStateOf(Color(0xFF181820))
    var surfaceVariantColor by mutableStateOf(Color(0xFF242430))
    var cardBorderColor by mutableStateOf(Color(0xFF2F2F3E))
    var textPrimaryColor by mutableStateOf(Color(0xFFF6F6F9))
    var textSecondaryColor by mutableStateOf(Color(0xFFA2A2B2))

    var wallpaperPath by mutableStateOf<String?>(null)
    var wallpaperDim by mutableFloatStateOf(0.55f)
    var animationsEnabled by mutableStateOf(true)
    var currentPresetId by mutableStateOf("CYBERPUNK")
}

object ThemeManager {
    private const val PREFS_NAME = "boumusic_theme_prefs"

    val PRESETS = listOf(
        ThemePreset(
            id = "CYBERPUNK",
            name = "Cyberpunk Neón",
            primary = Color(0xFF00E5FF),
            secondary = Color(0xFF7C4DFF),
            background = Color(0xFF0F0F13),
            surface = Color(0xFF181820),
            surfaceVariant = Color(0xFF242430),
            cardBorder = Color(0xFF2F2F3E)
        ),
        ThemePreset(
            id = "OLED",
            name = "OLED Negro Puro",
            primary = Color(0xFF00E5FF),
            secondary = Color(0xFF7C4DFF),
            background = Color(0xFF000000),
            surface = Color(0xFF0B0B0E),
            surfaceVariant = Color(0xFF16161A),
            cardBorder = Color(0xFF222228)
        ),
        ThemePreset(
            id = "EMERALD",
            name = "Esmeralda Synth",
            primary = Color(0xFF00E676),
            secondary = Color(0xFF00B0FF),
            background = Color(0xFF09140E),
            surface = Color(0xFF102117),
            surfaceVariant = Color(0xFF183022),
            cardBorder = Color(0xFF254734)
        ),
        ThemePreset(
            id = "SUNSET",
            name = "Vaporwave Rosa",
            primary = Color(0xFFFF4081),
            secondary = Color(0xFFFF9100),
            background = Color(0xFF140A13),
            surface = Color(0xFF20101E),
            surfaceVariant = Color(0xFF2D172A),
            cardBorder = Color(0xFF3F213B)
        ),
        ThemePreset(
            id = "MIDNIGHT",
            name = "Azul Medianoche",
            primary = Color(0xFF2979FF),
            secondary = Color(0xFF00E5FF),
            background = Color(0xFF080D1A),
            surface = Color(0xFF0F172E),
            surfaceVariant = Color(0xFF162242),
            cardBorder = Color(0xFF22325E)
        ),
        ThemePreset(
            id = "CRIMSON",
            name = "Rojo Carmesí",
            primary = Color(0xFFFF1744),
            secondary = Color(0xFFD500F9),
            background = Color(0xFF14080A),
            surface = Color(0xFF200E11),
            surfaceVariant = Color(0xFF2E1519),
            cardBorder = Color(0xFF421F25)
        ),
        ThemePreset(
            id = "SOLAR",
            name = "Ámbar Dorado",
            primary = Color(0xFFFFD600),
            secondary = Color(0xFFFF6D00),
            background = Color(0xFF141108),
            surface = Color(0xFF211D0E),
            surfaceVariant = Color(0xFF2F2914),
            cardBorder = Color(0xFF423A1D)
        )
    )

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val presetId = prefs.getString("preset_id", "CYBERPUNK") ?: "CYBERPUNK"
        AppThemeState.currentPresetId = presetId

        val preset = PRESETS.firstOrNull { it.id == presetId }
        if (preset != null && presetId != "CUSTOM") {
            applyPresetInternal(preset)
        } else {
            // Cargar colores personalizados
            val p = prefs.getInt("primary_color", Color(0xFF00E5FF).toArgb())
            val s = prefs.getInt("secondary_color", Color(0xFF7C4DFF).toArgb())
            val b = prefs.getInt("background_color", Color(0xFF0F0F13).toArgb())
            val surf = prefs.getInt("surface_color", Color(0xFF181820).toArgb())
            val surfVar = prefs.getInt("surface_var_color", Color(0xFF242430).toArgb())
            val border = prefs.getInt("card_border_color", Color(0xFF2F2F3E).toArgb())

            AppThemeState.primaryColor = Color(p)
            AppThemeState.secondaryColor = Color(s)
            AppThemeState.backgroundColor = Color(b)
            AppThemeState.surfaceColor = Color(surf)
            AppThemeState.surfaceVariantColor = Color(surfVar)
            AppThemeState.cardBorderColor = Color(border)
        }

        val wallPath = prefs.getString("wallpaper_path", null)
        if (wallPath != null && File(wallPath).exists()) {
            AppThemeState.wallpaperPath = wallPath
        } else {
            AppThemeState.wallpaperPath = null
        }

        AppThemeState.wallpaperDim = prefs.getFloat("wallpaper_dim", 0.55f)
        AppThemeState.animationsEnabled = prefs.getBoolean("animations_enabled", true)
    }

    private fun applyPresetInternal(preset: ThemePreset) {
        AppThemeState.primaryColor = preset.primary
        AppThemeState.secondaryColor = preset.secondary
        AppThemeState.backgroundColor = preset.background
        AppThemeState.surfaceColor = preset.surface
        AppThemeState.surfaceVariantColor = preset.surfaceVariant
        AppThemeState.cardBorderColor = preset.cardBorder
    }

    fun applyPreset(context: Context, preset: ThemePreset) {
        AppThemeState.currentPresetId = preset.id
        applyPresetInternal(preset)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("preset_id", preset.id)
            .putInt("primary_color", preset.primary.toArgb())
            .putInt("secondary_color", preset.secondary.toArgb())
            .putInt("background_color", preset.background.toArgb())
            .putInt("surface_color", preset.surface.toArgb())
            .putInt("surface_var_color", preset.surfaceVariant.toArgb())
            .putInt("card_border_color", preset.cardBorder.toArgb())
            .apply()
    }

    fun applyCustomColors(
        context: Context,
        primary: Color,
        secondary: Color,
        background: Color
    ) {
        AppThemeState.currentPresetId = "CUSTOM"
        AppThemeState.primaryColor = primary
        AppThemeState.secondaryColor = secondary
        AppThemeState.backgroundColor = background

        // Calcular superficies relativas al fondo
        val isPureBlack = background == Color(0xFF000000)
        val surf = if (isPureBlack) Color(0xFF0B0B0E) else Color(
            (background.red * 1.35f).coerceAtMost(1f),
            (background.green * 1.35f).coerceAtMost(1f),
            (background.blue * 1.35f).coerceAtMost(1f),
            1f
        )
        val surfVar = if (isPureBlack) Color(0xFF16161A) else Color(
            (background.red * 1.7f).coerceAtMost(1f),
            (background.green * 1.7f).coerceAtMost(1f),
            (background.blue * 1.7f).coerceAtMost(1f),
            1f
        )
        val border = if (isPureBlack) Color(0xFF222228) else Color(
            (background.red * 2.1f).coerceAtMost(1f),
            (background.green * 2.1f).coerceAtMost(1f),
            (background.blue * 2.1f).coerceAtMost(1f),
            1f
        )

        AppThemeState.surfaceColor = surf
        AppThemeState.surfaceVariantColor = surfVar
        AppThemeState.cardBorderColor = border

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("preset_id", "CUSTOM")
            .putInt("primary_color", primary.toArgb())
            .putInt("secondary_color", secondary.toArgb())
            .putInt("background_color", background.toArgb())
            .putInt("surface_color", surf.toArgb())
            .putInt("surface_var_color", surfVar.toArgb())
            .putInt("card_border_color", border.toArgb())
            .apply()
    }

    fun setWallpaper(context: Context, imageUri: Uri?): Boolean {
        if (imageUri == null) {
            clearWallpaper(context)
            return true
        }

        try {
            val destFile = File(context.filesDir, "custom_wallpaper.jpg")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) {
                AppThemeState.wallpaperPath = destFile.absolutePath
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("wallpaper_path", destFile.absolutePath)
                    .apply()
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun clearWallpaper(context: Context) {
        val file = File(context.filesDir, "custom_wallpaper.jpg")
        if (file.exists()) file.delete()
        AppThemeState.wallpaperPath = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("wallpaper_path")
            .apply()
    }

    fun setWallpaperDim(context: Context, dim: Float) {
        AppThemeState.wallpaperDim = dim.coerceIn(0.1f, 0.95f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat("wallpaper_dim", AppThemeState.wallpaperDim)
            .apply()
    }

    fun setAnimationsEnabled(context: Context, enabled: Boolean) {
        AppThemeState.animationsEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("animations_enabled", enabled)
            .apply()
    }

    fun resetToDefaults(context: Context) {
        clearWallpaper(context)
        setWallpaperDim(context, 0.55f)
        setAnimationsEnabled(context, true)
        val defaultPreset = PRESETS.first()
        applyPreset(context, defaultPreset)
    }
}
