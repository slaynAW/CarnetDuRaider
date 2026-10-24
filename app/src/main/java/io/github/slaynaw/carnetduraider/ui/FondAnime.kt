package io.github.slaynaw.carnetduraider.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/** Une boucle du mouvement « caméra à l'épaule », lente. */
private const val BOUCLE_MS = 16_000

/** L'image est un peu plus grande que le cadre : on n'en voit jamais le bord en bougeant. */
private const val AGRANDI = 1.06f

/** Les positions de la caméra (part de la boucle, décalage x et y en dp, rotation en degrés), reliées en douceur. */
private val Positions = listOf(
    floatArrayOf(0f, 0f, 0f, 0f),
    floatArrayOf(0.18f, -2.5f, 1.5f, -0.25f),
    floatArrayOf(0.37f, 1.5f, 2.5f, 0.18f),
    floatArrayOf(0.55f, 3f, -1f, 0.3f),
    floatArrayOf(0.74f, -1f, -2.5f, -0.15f),
    floatArrayOf(1f, 0f, 0f, 0f),
)

/**
 * Deux glitchs par boucle, très brefs (un peu plus d'un dixième de seconde) : une bande de l'image décalée de
 * quelques dp, bordée d'un fil cyan et d'un fil rouge. Part de la boucle où il commence, bande (haut et bas, en
 * part de la hauteur), décalage en dp.
 */
private class Glitch(val debut: Float, val haut: Float, val bas: Float, val decalage: Float)

private val Glitchs = listOf(Glitch(0.31f, 0.32f, 0.40f, 6f), Glitch(0.83f, 0.62f, 0.67f, -5f))
private const val DUREE_GLITCH = 0.009f

/**
 * La capture en jeu de « Ton prochain raid » à l'accueil, qui bouge doucement comme filmée à l'épaule, avec de
 * rares glitchs discrets, puis assombrie à gauche et en bas pour le texte. Tout est lu au dessin seulement, et
 * l'image reste immobile avec « Supprimer les animations ».
 */
@Composable
fun BoxScope.FondEnJeuAnime(image: ImageBitmap) {
    val temps = if (animationsActives()) {
        rememberInfiniteTransition(label = "fond").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BOUCLE_MS, easing = LinearEasing)),
            label = "boucle",
        )
    } else {
        null
    }
    Canvas(Modifier.matchParentSize()) {
        val p = temps?.value
        val (dx, dy, rotation) = if (p == null) Triple(0f, 0f, 0f) else position(p)
        val camera: DrawScope.(Float) -> Unit = { decalage ->
            withTransform({
                translate(dx.dp.toPx() + decalage, dy.dp.toPx())
                rotate(rotation)
                scale(AGRANDI, AGRANDI)
            }) { dessinerRecadre(image) }
        }
        camera(0f)
        if (p != null) {
            Glitchs.forEach { g ->
                val dans = p - g.debut
                if (dans in 0f..DUREE_GLITCH) {
                    // Deux à-coups : la bande part d'un côté, puis revient un peu de l'autre.
                    val decalage = (if (dans < DUREE_GLITCH / 2) g.decalage else -g.decalage * 0.6f).dp.toPx()
                    val haut = size.height * g.haut
                    val bas = size.height * g.bas
                    clipRect(top = haut, bottom = bas) { camera(decalage) }
                    drawRect(Signal.Cyan.copy(alpha = 0.45f), topLeft = Offset(decalage, haut), size = Size(size.width, 1.dp.toPx()))
                    drawRect(Signal.Rouge.copy(alpha = 0.45f), topLeft = Offset(-decalage, bas - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                }
            }
        }
        // Ombre latérale (le texte est à gauche) et pied assombri (les zones sont en bas).
        drawRect(
            Brush.horizontalGradient(
                0f to Signal.Fond.copy(alpha = 0.94f),
                0.6f to Signal.Fond.copy(alpha = 0.55f),
                1f to Signal.Fond.copy(alpha = 0.12f),
            ),
        )
        drawRect(Brush.verticalGradient(0.55f to Signal.Fond.copy(alpha = 0f), 1f to Signal.Fond.copy(alpha = 0.55f)))
    }
}

/** Décalage x, y (dp) et rotation (degrés) à l'instant [p] de la boucle, d'une position à la suivante en sinus. */
private fun position(p: Float): Triple<Float, Float, Float> {
    val i = Positions.indexOfLast { it[0] <= p }.coerceIn(0, Positions.size - 2)
    val a = Positions[i]
    val b = Positions[i + 1]
    val f = ((p - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f)
    val e = ((1 - cos(PI * f)) / 2).toFloat()
    return Triple(a[1] + (b[1] - a[1]) * e, a[2] + (b[2] - a[2]) * e, a[3] + (b[3] - a[3]) * e)
}

/** L'image recadrée pour remplir tout le cadre, centrée (comme `ContentScale.Crop`). */
private fun DrawScope.dessinerRecadre(image: ImageBitmap) {
    val echelle = max(size.width / image.width, size.height / image.height)
    val largeur = (image.width * echelle).roundToInt()
    val hauteur = (image.height * echelle).roundToInt()
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(((size.width - largeur) / 2).roundToInt(), ((size.height - hauteur) / 2).roundToInt()),
        dstSize = IntSize(largeur, hauteur),
    )
}
