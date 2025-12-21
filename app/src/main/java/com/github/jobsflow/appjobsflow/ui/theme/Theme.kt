package com.github.jobsflow.appjobsflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.os.Build

// --- Простая и контрастная схема: Синий и Золотой ---

// Светлая тема по Color.md
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B82F6),
    onPrimaryContainer = Color.White,

    secondary = Color(0xFF6B7280),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1D5DB),
    onSecondaryContainer = Color(0xFF1F2937),

    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2937),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),

    error = Color(0xFFEF4444),
    onError = Color.White
)

// Тёмная тема по Color.md
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color.White,

    secondary = Color(0xFF9CA3AF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4B5563),
    onSecondaryContainer = Color.White,

    background = Color(0xFF111827),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF9FAFB),

    error = Color(0xFFF87171),
    onError = Color.Black
)


@Composable
fun AppJobsFlowBaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp), // Для маленьких компонентов (чипы, бейджи)
        small = RoundedCornerShape(8.dp),      // Для небольших кнопок, текстовых полей
        medium = RoundedCornerShape(12.dp),    // Для большинства кнопок
        large = RoundedCornerShape(16.dp),     // Для крупных элементов
        extraLarge = RoundedCornerShape(24.dp) // Для очень больших компонентов
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = Typography, // Убедитесь, что у вас определён объект Typography
        content = content
    )
}
