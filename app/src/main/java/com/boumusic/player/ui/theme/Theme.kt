package com.boumusic.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

@Composable
fun BoumusicTheme(
    content: @Composable () -> Unit
) {
    val dynamicScheme = darkColorScheme(
        primary = AppThemeState.primaryColor,
        onPrimary = AppThemeState.backgroundColor,
        secondary = AppThemeState.secondaryColor,
        onSecondary = AppThemeState.textPrimaryColor,
        background = AppThemeState.backgroundColor,
        onBackground = AppThemeState.textPrimaryColor,
        surface = AppThemeState.surfaceColor,
        onSurface = AppThemeState.textPrimaryColor,
        surfaceVariant = AppThemeState.surfaceVariantColor,
        onSurfaceVariant = AppThemeState.textSecondaryColor
    )

    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = Typography
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppThemeState.backgroundColor)
        ) {
            val wallPath = AppThemeState.wallpaperPath
            if (!wallPath.isNullOrBlank() && File(wallPath).exists()) {
                AsyncImage(
                    model = File(wallPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = AppThemeState.wallpaperDim))
                )
            }
            content()
        }
    }
}
