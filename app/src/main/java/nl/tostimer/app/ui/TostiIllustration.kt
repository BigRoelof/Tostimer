package nl.tostimer.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import nl.tostimer.app.Ingredient

private val BreadPale = Color(0xFFF6E3B4)
private val BreadToasted = Color(0xFFC27A2C)
private val CrustPale = Color(0xFFDDAE6B)
private val CrustToasted = Color(0xFF7A431A)
private val GrillMark = Color(0xFF4A2810)
private val Cheese = Color(0xFFF5A300)
private val Ham = Color(0xFFF08A8F)
private val Pesto = Color(0xFF5E9B3C)
private val Other = Color(0xFFE2432F)

private const val RestingTilt = -4f

/**
 * A tosti seen from above. [progress] (0..1) is how toasted it is: the bread browns, the grill
 * marks appear and the cheese starts to ooze. With [wobble] it rocks back and forth.
 */
@Composable
fun TostiIllustration(
    progress: Float,
    modifier: Modifier = Modifier,
    ingredients: Set<Ingredient> = setOf(Ingredient.CHEESE),
    wobble: Boolean = false,
    plate: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val wobbleAngle by rememberInfiniteTransition(label = "wobble").animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "wobbleAngle",
    )
    // At rest it sits at a slight angle, like it was just put down on the plate.
    val tilt = if (wobble) wobbleAngle else RestingTilt
    Canvas(modifier.aspectRatio(1f)) { drawTosti(progress, plate, ingredients, tilt) }
}

/** Renders the tosti off-screen, for the home-screen widget (which can't run Compose). */
fun renderTostiBitmap(progress: Float, ingredients: Set<Ingredient>, plate: Color, sizePx: Int = 256): android.graphics.Bitmap {
    val image = ImageBitmap(sizePx, sizePx)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GfxCanvas(image), Size(sizePx.toFloat(), sizePx.toFloat())) {
        drawTosti(progress, plate, ingredients, RestingTilt)
    }
    return image.asAndroidBitmap()
}

fun DrawScope.drawTosti(progress: Float, plate: Color, ingredients: Set<Ingredient>, tilt: Float) {
    val p = progress.coerceIn(0f, 1f)
    val s = size.minDimension

    // the plate it sits on
    drawCircle(plate, radius = s * 0.5f, center = Offset(s / 2, s / 2))
    drawCircle(Color(0x143A2212), radius = s * 0.5f, center = Offset(s / 2, s / 2), style = Stroke(s * 0.012f))

    rotate(tilt, pivot = Offset(s / 2, s / 2)) {
        val body = Rect(Offset(s * 0.11f, s * 0.11f), Size(s * 0.78f, s * 0.78f))
        val inset = s * 0.055f
        val inner = Rect(body.left + inset, body.top + inset, body.right - inset, body.bottom - inset)

        // soft contact shadow
        drawRoundRect(
            Color(0x1F3A2212),
            topLeft = Offset(body.left + s * 0.02f, body.top + s * 0.03f),
            size = body.size,
            cornerRadius = CornerRadius(s * 0.16f),
        )

        // ingredients peeking out of the edges go under the bread
        if (Ingredient.HAM in ingredients) {
            drawRoundRect(
                Ham,
                topLeft = Offset(body.left - s * 0.045f, body.top + body.height * 0.3f),
                size = Size(s * 0.1f, body.height * 0.36f),
                cornerRadius = CornerRadius(s * 0.04f),
            )
        }
        if (Ingredient.PESTO in ingredients) {
            listOf(0.14f, 0.34f).forEach { at ->
                drawRoundRect(
                    Pesto,
                    topLeft = Offset(body.left + body.width * at, body.top - s * 0.04f),
                    size = Size(s * 0.12f, s * 0.09f),
                    cornerRadius = CornerRadius(s * 0.045f),
                )
            }
        }
        if (Ingredient.OTHER in ingredients) {
            listOf(0.6f, 0.78f).forEach { at ->
                drawRoundRect(
                    Other,
                    topLeft = Offset(body.left + body.width * at, body.top - s * 0.045f),
                    size = Size(s * 0.11f, s * 0.1f),
                    cornerRadius = CornerRadius(s * 0.05f),
                )
            }
        }

        // crust and bread
        drawRoundRect(lerp(CrustPale, CrustToasted, p), body.topLeft, body.size, CornerRadius(s * 0.16f))
        drawRoundRect(lerp(BreadPale, BreadToasted, p), inner.topLeft, inner.size, CornerRadius(s * 0.12f))

        // grill marks fade in as it toasts
        val markPath = Path().apply { addRoundRect(RoundRect(inner, CornerRadius(s * 0.12f))) }
        clipPath(markPath) {
            val gap = inner.width * 0.27f
            for (i in -1..4) {
                val x = inner.left + i * gap
                drawLine(
                    color = GrillMark.copy(alpha = 0.75f * p),
                    start = Offset(x, inner.top),
                    end = Offset(x + inner.height * 0.55f, inner.bottom),
                    strokeWidth = s * 0.05f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (Ingredient.CHEESE in ingredients) {
            // cheese oozing out of the right edge and dripping off the bottom, more as it melts
            val ooze = s * (0.035f + 0.05f * p)
            drawRoundRect(
                Cheese,
                topLeft = Offset(body.right - s * 0.03f, body.top + body.height * 0.22f),
                size = Size(ooze + s * 0.03f, body.height * 0.38f),
                cornerRadius = CornerRadius(s * 0.05f),
            )
            val drip = s * (0.035f + 0.08f * p)
            listOf(0.26f to 0.09f, 0.55f to 0.065f).forEach { (at, width) ->
                drawRoundRect(
                    Cheese,
                    topLeft = Offset(body.left + body.width * at, body.bottom - s * 0.03f),
                    size = Size(s * width, drip + s * 0.03f),
                    cornerRadius = CornerRadius(s * 0.03f),
                )
            }
        }
    }
}
