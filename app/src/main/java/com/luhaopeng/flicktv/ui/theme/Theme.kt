package com.luhaopeng.flicktv.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VodBackground = Color(0xFF0A0A0F)
val VodSurface = Color(0xFF17171F)
val VodSurfaceRaised = Color(0xFF202029)
val VodAccent = Color(0xFF6FE7C8)
val VodFocus = Color.White
val VodTextPrimary = Color(0xFFF6F6F8)
val VodTextSecondary = Color(0xFFA8A8B3)
val VodTextMuted = Color(0xFF7F8792)
val VodSuccess = Color(0xFF63E6A8)
val VodWarning = Color(0xFFFFC66D)
val VodError = Color(0xFFFF9B72)
val VodDisabled = Color(0xFF73737D)

object VodDimens {
    val ScreenHorizontal = 24.dp
    val ScreenVertical = 12.dp
    val Section = 16.dp
    val ItemGap = 8.dp
    val PillRadius = 10.dp
    val CardRadius = 12.dp
    val FocusBorder = 3.dp
}

object VodType {
    val Brand = 26.sp
    val Title = 30.sp
    val Body = 15.sp
    val Secondary = 13.sp
    val Caption = 12.sp
}

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
