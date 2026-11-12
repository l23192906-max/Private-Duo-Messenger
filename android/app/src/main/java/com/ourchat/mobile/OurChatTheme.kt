package com.ourchat.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChatLightColors = lightColorScheme(
    primary = Color(0xFF0E746A),
    onPrimary = Color.White,
    secondary = Color(0xFF39877D),
    background = Color(0xFFF4F7F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EFED),
    onSurface = Color(0xFF17302E),
    onSurfaceVariant = Color(0xFF526562),
    error = Color(0xFFB42318),
)

@Composable
fun OurChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChatLightColors,
        content = content,
    )
}
