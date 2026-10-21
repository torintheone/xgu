package io.github.torintheone.petri.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Always dark, like a microscope room: the dish view sets the mood for the whole app. */
private val PetriColors = darkColorScheme(
    primary = Color(0xFF7FD8C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1C4F46),
    onPrimaryContainer = Color(0xFFA6F2DF),
    secondary = Color(0xFFAAC7E4),
    onSecondary = Color(0xFF12324B),
    secondaryContainer = Color(0xFF26445F),
    onSecondaryContainer = Color(0xFFD1E4F8),
    background = Color(0xFF070B0E),
    onBackground = Color(0xFFDDE4E8),
    surface = Color(0xFF0B1115),
    onSurface = Color(0xFFDDE4E8),
    surfaceVariant = Color(0xFF1D262C),
    onSurfaceVariant = Color(0xFFA9B4BB),
    surfaceContainer = Color(0xFF111A1F),
    outline = Color(0xFF6F7B82),
    outlineVariant = Color(0xFF3A464D),
)

@Composable
fun PetriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PetriColors, content = content)
}
