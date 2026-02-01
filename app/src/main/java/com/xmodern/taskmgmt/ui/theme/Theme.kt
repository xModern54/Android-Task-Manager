package com.xmodern.taskmgmt.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkBackground,
    onBackground = TextWhite,
    onSurface = TextWhite
)

private val LightColorScheme = lightColorScheme(
    primary = NeonCyan,
    background = TextWhite,
    surface = TextWhite,
    onPrimary = DarkBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground
)

@Composable
fun TaskManagerTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) DarkColorScheme else LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
