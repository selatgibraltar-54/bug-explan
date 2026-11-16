package com.browser.client.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.browser.client.data.preferences.ThemeMode

private val ClearPassDarkColorScheme = darkColorScheme(
    primary = AmberPulse,
    onPrimary = Color.White,
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = OnAmberContainerDark,
    secondary = EmeraldCleared,
    onSecondary = Obsidian950,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    tertiary = SkyAccent,
    onTertiary = Obsidian950,
    background = Obsidian950,
    onBackground = TextPrimaryDark,
    surface = Obsidian900,
    onSurface = TextPrimaryDark,
    surfaceVariant = Obsidian800,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Obsidian850,
    surfaceContainerHigh = Obsidian800,
    surfaceContainerHighest = Obsidian700,
    outline = SlateBorderDark,
    outlineVariant = Obsidian700,
    error = CoralAlert,
    onError = Color.White
)

private val ClearPassLightColorScheme = lightColorScheme(
    primary = AmberPulse,
    onPrimary = Color.White,
    primaryContainer = AmberContainerLight,
    onPrimaryContainer = OnAmberContainerLight,
    secondary = EmeraldClearedLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = OnEmeraldContainerLight,
    tertiary = SkyAccent,
    onTertiary = TextPrimaryLight,
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightBg,
    surfaceContainer = DaylightSurfaceVariant,
    surfaceContainerHigh = DaylightSurfaceVariant,
    surfaceContainerHighest = DaylightBorder,
    outline = DaylightBorder,
    outlineVariant = DaylightSurfaceVariant,
    error = CoralAlert,
    onError = Color.White
)

private val ClearPassIncognitoColorScheme = darkColorScheme(
    primary = StealthViolet,
    onPrimary = StealthBg,
    primaryContainer = StealthVioletContainer,
    onPrimaryContainer = OnStealthVioletContainer,
    secondary = EmeraldCleared,
    onSecondary = StealthBg,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    tertiary = SkyAccent,
    onTertiary = StealthBg,
    background = StealthBg,
    onBackground = StealthTextPrimary,
    surface = StealthSurface,
    onSurface = StealthTextPrimary,
    surfaceVariant = StealthSurfaceContainerHigh,
    onSurfaceVariant = StealthTextSecondary,
    surfaceContainerLowest = StealthBg,
    surfaceContainerLow = StealthSurfaceContainerLow,
    surfaceContainer = StealthSurfaceContainer,
    surfaceContainerHigh = StealthSurfaceContainerHigh,
    surfaceContainerHighest = StealthSurfaceContainerHighest,
    outline = StealthBorder,
    outlineVariant = StealthSurfaceContainerHighest,
    error = CoralAlert,
    onError = Color.White
)

val ClearPassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClearPassTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    isIncognito: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = isIncognito || when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        isIncognito -> ClearPassIncognitoColorScheme
        darkTheme -> ClearPassDarkColorScheme
        else -> ClearPassLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClearPassTypography,
        shapes = ClearPassShapes,
        content = content
    )
}
