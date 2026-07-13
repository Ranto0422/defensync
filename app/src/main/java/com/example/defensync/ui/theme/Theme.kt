package com.example.defensync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = MapuaRed,
    onPrimary = Color.White,
    primaryContainer = MapuaRedContainer,
    onPrimaryContainer = OnMapuaRedContainer,
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = BrandBlueContainer,
    onSecondaryContainer = OnBrandBlueContainer,
    tertiary = BrandBlue,
    onTertiary = Color.White,
    tertiaryContainer = BrandBlueContainer,
    onTertiaryContainer = OnBrandBlueContainer,
    background = AppBackground,
    onBackground = AppOnSurface,
    surface = AppWhite,
    onSurface = AppOnSurface,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppOnSurfaceVariant,
    outline = AppOutline,
    outlineVariant = AppOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = MapuaRedLight,
    onPrimary = Color(0xFF680014),
    primaryContainer = MapuaRedDark,
    onPrimaryContainer = MapuaRedContainer,
    secondary = BrandBlueLight,
    onSecondary = Color(0xFF002F63),
    secondaryContainer = Color(0xFF17416F),
    onSecondaryContainer = BrandBlueContainer,
    tertiary = BrandBlueLight,
    onTertiary = Color(0xFF002F63),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF9F8C8C),
    outlineVariant = DarkSurfaceVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun DefensyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
