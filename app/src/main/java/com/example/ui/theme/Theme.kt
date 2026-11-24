package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val AaCraveDarkColorScheme = darkColorScheme(
    primary = CraveGold,
    onPrimary = OnCraveGold,
    primaryContainer = CraveGoldContainer,
    onPrimaryContainer = CraveGoldBright,
    secondary = CraveGoldMuted,
    onSecondary = IvoryText,
    secondaryContainer = ObsidianSurfaceHigh,
    onSecondaryContainer = IvoryText,
    tertiary = StatusEmerald,
    onTertiary = OnCraveGold,
    background = ObsidianBg,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = MutedSandText,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorder,
    error = StatusCrimson,
    onError = IvoryText,
    errorContainer = StatusCrimsonBg,
    onErrorContainer = StatusCrimson
)

val AaCraveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AaCraveTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = AaCraveDarkColorScheme,
        typography = AaCraveTypography,
        shapes = AaCraveShapes,
        content = content
    )
}
