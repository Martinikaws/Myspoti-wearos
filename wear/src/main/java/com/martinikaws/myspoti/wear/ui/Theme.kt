package com.martinikaws.myspoti.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Spotify-style palette: near-black surfaces, one green accent, grey secondary text. */
object MyspotiColors {
    val Green = Color(0xFF1DB954)
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceHigh = Color(0xFF282828)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val Track = Color(0xFF4D4D4D)
}

@Composable
fun MyspotiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = MyspotiColors.Green,
            onPrimary = MyspotiColors.Black,
            secondary = MyspotiColors.Green,
            background = MyspotiColors.Black,
            onBackground = MyspotiColors.TextPrimary,
            surface = MyspotiColors.Surface,
            onSurface = MyspotiColors.TextPrimary,
            onSurfaceVariant = MyspotiColors.TextSecondary,
        ),
        content = content,
    )
}
