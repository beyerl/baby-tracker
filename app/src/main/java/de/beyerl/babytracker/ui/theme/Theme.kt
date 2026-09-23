package de.beyerl.babytracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app is always dark, modelled on the Napper night look. Dynamic (Material
 * You) colors are not used so the design is the same on every device.
 */
private val NightColors = darkColorScheme(
    primary = Lavender,
    onPrimary = Night,
    primaryContainer = LavenderDark,
    onPrimaryContainer = OnNight,
    secondary = LavenderLight,
    onSecondary = Night,
    secondaryContainer = NightSurfaceHigh,
    onSecondaryContainer = OnNight,
    tertiary = FeedColor,
    background = Night,
    onBackground = OnNight,
    surface = Night,
    onSurface = OnNight,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = OnNightMuted,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = NightSurfaceHigh,
    outline = OnNightMuted,
    outlineVariant = NightOutline,
)

@Composable
fun BabyTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColors,
        typography = Typography,
        content = content,
    )
}
