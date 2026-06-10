package com.popcorn.inventory.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6F5E),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A33),
    tertiary = Color(0xFFC45F3B),
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0EEE8),
    outline = Color(0xFFD8D3C8),
    error = Color(0xFFB3261E)
)

@Composable
fun PopcornTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
