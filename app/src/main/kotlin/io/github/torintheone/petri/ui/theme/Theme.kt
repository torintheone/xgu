package io.github.torintheone.petri.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The look of a lab instrument: a dark room, panels of smoked glass with hairline edges, one cyan accent for
 * whatever is active, and tabular figures for every reading.
 */
object Lab {
    val Background = Color(0xFF05090C)

    /** Panels on an opaque screen. */
    val Panel = Color(0xFF0B1418)

    /** Panels over the dish: the cells stay faintly visible through them. */
    val Glass = Color(0xE00A1216)

    /** Buttons, fields and value chips inside panels. */
    val Raised = Color(0xFF111D23)
    val Hairline = Color(0xFF1E2E36)
    val HairlineStrong = Color(0xFF2D434D)

    val Accent = Color(0xFF5FE1EA)
    val AccentSoft = Color(0x295FE1EA)
    val OnAccent = Color(0xFF00282C)
    val Amber = Color(0xFFFFB648)
    val Lime = Color(0xFFC3E38D)
    val Violet = Color(0xFFB59CFF)
    val Danger = Color(0xFFFF7468)

    val Text = Color(0xFFE2EBEE)
    val TextDim = Color(0xFF91A3AB)
    val TextFaint = Color(0xFF5B6D75)
}

/** Text styles of the instrument on top of the Material ones. */
object LabType {
    /** Small capitals over readings and groups (callers upper-case the text). */
    val Eyebrow = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp)

    /** Readings: tabular figures, so numbers do not jitter while they change. */
    val Reading = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val ReadingSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

    /** Technical marks: scale bars, signal names, genome numbers. */
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
}

private val PetriColors = darkColorScheme(
    primary = Lab.Accent,
    onPrimary = Lab.OnAccent,
    primaryContainer = Color(0xFF0D3B40),
    onPrimaryContainer = Color(0xFFB1F4F8),
    inversePrimary = Color(0xFF00696F),
    secondary = Lab.Amber,
    onSecondary = Color(0xFF3B2600),
    secondaryContainer = Color(0xFF4A3412),
    onSecondaryContainer = Color(0xFFFFDDB2),
    tertiary = Lab.Lime,
    onTertiary = Color(0xFF213500),
    tertiaryContainer = Color(0xFF2F4512),
    onTertiaryContainer = Color(0xFFDDF5B6),
    background = Lab.Background,
    onBackground = Lab.Text,
    surface = Lab.Background,
    onSurface = Lab.Text,
    surfaceVariant = Lab.Raised,
    onSurfaceVariant = Lab.TextDim,
    surfaceTint = Lab.Accent,
    inverseSurface = Color(0xFFDDE7EA),
    inverseOnSurface = Color(0xFF11181C),
    error = Lab.Danger,
    onError = Color(0xFF3D0500),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFFFDAD5),
    outline = Lab.HairlineStrong,
    outlineVariant = Lab.Hairline,
    scrim = Color.Black,
    surfaceBright = Color(0xFF1A272E),
    surfaceDim = Lab.Background,
    surfaceContainerLowest = Color(0xFF030608),
    surfaceContainerLow = Color(0xFF080F13),
    surfaceContainer = Lab.Panel,
    surfaceContainerHigh = Lab.Raised,
    surfaceContainerHighest = Color(0xFF17252C),
)

private val PetriShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Always dark, like a microscope room: the dish view sets the mood for the whole app. */
@Composable
fun PetriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PetriColors, shapes = PetriShapes, content = content)
}
