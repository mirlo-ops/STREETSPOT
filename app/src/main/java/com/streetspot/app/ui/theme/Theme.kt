package com.streetspot.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal val MidnightColorScheme = darkColorScheme(
    primary = AccentMint,
    onPrimary = Background,
    primaryContainer = ElevatedSurface,
    onPrimaryContainer = PrimaryText,
    secondary = SecondaryText,
    onSecondary = Background,
    tertiary = WarningGold,
    onTertiary = Background,
    background = Background,
    onBackground = PrimaryText,
    surface = Surface,
    onSurface = PrimaryText,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = SecondaryText,
    outline = Outline,
)

@Composable
fun StreetSpotTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (
        useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        dynamicDarkColorScheme(context)
    } else {
        MidnightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StreetSpotTypography,
        content = content,
    )
}
