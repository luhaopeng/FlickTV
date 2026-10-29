package com.luhaopeng.flicktv.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VodBackground = Color(0xFF0A0A0F)
val VodSurface = Color(0xFF17171F)
val VodAccent = Color(0xFF6FE7C8)
val VodTextSecondary = Color(0xFFA8A8B3)

@Composable
fun VodBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = VodAccent,
            background = VodBackground,
            surface = VodSurface,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}
