package nl.tostimer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import nl.tostimer.app.R

/** Dutch snack-bar palette: butter-yellow signage, crust-brown ink, one ketchup-red action colour. */
object TostiPalette {
    val Butter = Color(0xFFFFD95A)
    val Crust = Color(0xFF3A2212)
    val Ketchup = Color(0xFFD9402B)
    val ToastGold = Color(0xFFD98B2B)
    val Plate = Color(0xFFFFF4D6)

    /** Background of the "tosti is ready" moment. */
    val CheeseOrange = Color(0xFFFFA83A)

    // Dark mode: a late-night snack bar.
    val NightCrust = Color(0xFF2A1A10)
    val NightPlate = Color(0xFF45301F)
    val NightButter = Color(0xFFFFD95A)
    val NightKetchup = Color(0xFFFF6B4F)
    val NightCheese = Color(0xFF7A3F12)
}

@OptIn(ExperimentalTextApi::class)
val TostiFont = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.fredoka,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val Light = lightColorScheme(
    primary = TostiPalette.Ketchup,
    onPrimary = TostiPalette.Plate,
    secondary = TostiPalette.Crust,
    onSecondary = TostiPalette.Plate,
    background = TostiPalette.Butter,
    onBackground = TostiPalette.Crust,
    surface = TostiPalette.Butter,
    onSurface = TostiPalette.Crust,
    surfaceVariant = TostiPalette.Plate,
    onSurfaceVariant = TostiPalette.Crust,
    outline = TostiPalette.Crust,
)

private val Dark = darkColorScheme(
    primary = TostiPalette.NightKetchup,
    onPrimary = TostiPalette.NightCrust,
    secondary = TostiPalette.NightButter,
    onSecondary = TostiPalette.NightCrust,
    background = TostiPalette.NightCrust,
    onBackground = TostiPalette.Plate,
    surface = TostiPalette.NightCrust,
    onSurface = TostiPalette.Plate,
    surfaceVariant = TostiPalette.NightPlate,
    onSurfaceVariant = TostiPalette.Plate,
    outline = TostiPalette.NightButter,
)

@Composable
fun TostimerTheme(content: @Composable () -> Unit) {
    // Deliberately no dynamic colour: the palette is the identity of the app.
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography().withFamily(TostiFont),
        content = content,
    )
}
