package com.example.bankanalyzer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5EC9C7),
    secondary = Color(0xFF9DB4D9),
    tertiary = Color(0xFF9DB4D9)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0E7C7B),
    secondary = Color(0xFF1F3864),
    tertiary = Color(0xFF1F3864)
)

@Composable
fun BankAnalyzerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Fixed money-app palette (teal / navy) instead of wallpaper colours.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}