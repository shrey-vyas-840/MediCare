package com.medicare.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFFB6F2EA),
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF101918),
    surface = Color(0xFF101918),
    surfaceVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F2EA),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFF5FAF8),
    surface = Color(0xFFF9FCFA),
    surfaceVariant = Color(0xFFE0E9E6),
    onSurfaceVariant = Color(0xFF414A48),
    outline = Color(0xFF707976),
    error = Color(0xFFBA1A1A)
)

@Composable
fun MediCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
