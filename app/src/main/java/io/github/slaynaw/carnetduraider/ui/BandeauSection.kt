package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.ui.theme.Barlow
import io.github.slaynaw.carnetduraider.ui.theme.Prompt
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Hauteur du bandeau de section, et de la grille dans laquelle ses motifs sont dessinés. */
private val HAUTEUR = 132.dp
private const val GRILLE = 132f

/** Carte mise en avant (prochain raid, objectif terminé…) : coin bas droit coupé, comme les bandeaux. */
val FormeCoupee = CutCornerShape(bottomEnd = 14.dp)

/**
 * Bandeau en tête de chaque onglet : la couleur de la section, son code (« 03 / OPÉRATION »), son nom en
 * capitales et un motif qui lui est propre (bandes du logo, caisse, réticule, courbes de niveau, chevrons,
 * plan technique). On sait d'un coup d'œil sur quelle section on est. Il occupe toute la largeur de l'écran,
 * même dans une liste avec des marges ([marge]).
 */
@Composable
fun BandeauSection(onglet: Onglet, sousTitre: String, marge: Dp = 20.dp) {
    Box(
        Modifier
            .pleineLargeur(marge)
            .height(HAUTEUR)
            .clip(CutCornerShape(bottomEnd = 34.dp))
            .background(onglet.couleur),
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) { motif(onglet) }
        Column(Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp)) {
            Text(
                onglet.code,
                style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 3.sp),
                color = Signal.SurCouleur,
            )
            Text(
                onglet.titre.uppercase(),
                style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 1.sp),
                color = Signal.SurCouleur,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                sousTitre,
                style = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
                color = Signal.SurCouleur,
                maxLines = 1,
            )
        }
    }
}

/** Élargit l'élément de [marge] de chaque côté : il passe sous les marges de la liste qui le contient. */
private fun Modifier.pleineLargeur(marge: Dp): Modifier = this.layout { mesurable, contraintes ->
    val m = marge.roundToPx()
    val largeur = contraintes.maxWidth + 2 * m
    val place = mesurable.measure(contraintes.copy(minWidth = largeur, maxWidth = largeur))
    layout(contraintes.maxWidth, place.height) { place.place(-m, 0) }
}

/**
 * Le motif de la section, dessiné à droite du bandeau dans une grille de [largeur] × 132 (mise à l'échelle
 * de la hauteur du bandeau).
 */
private fun DrawScope.motif(onglet: Onglet) {
    val largeur = when (onglet) {
        Onglet.ACCUEIL, Onglet.CARTES, Onglet.BUILDS -> 210f
        else -> 200f
    }
    val s = size.height / GRILLE
    translate(left = size.width - largeur * s) {
        scale(s, s, pivot = Offset.Zero) {
            when (onglet) {
                Onglet.ACCUEIL -> motifAccueil()
                Onglet.OBJETS -> motifObjets()
                Onglet.RAID -> motifRaid()
                Onglet.CARTES -> motifCartes()
                Onglet.OBJECTIFS -> motifObjectifs()
                Onglet.BUILDS -> motifBuilds()
            }
        }
    }
}

private val Encre = Signal.SurCouleur

private fun polygone(vararg points: Float): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    close()
}

/** Accueil : les quatre bandes du logo, en grand. */
private fun DrawScope.motifAccueil() {
    listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge).forEachIndexed { i, couleur ->
        val x = 40f + 40f * i
        drawPath(polygone(x, 132f, x + 26f, 132f, x + 78f, 0f, x + 52f, 0f), couleur)
    }
}

/** Objets : rayures « danger » et caisse vue de face. */
private fun DrawScope.motifObjets() {
    clipRect(left = 60f, top = 0f, right = 200f, bottom = 132f) {
        var x = -80f
        while (x < 200f) {
            drawLine(Encre.copy(alpha = 0.9f), Offset(x, 132f), Offset(x + 132f, 0f), strokeWidth = 11f)
            x += 31f
        }
    }
    drawRect(Signal.Or, topLeft = Offset(96f, 30f), size = Size(72f, 72f))
    drawRect(Encre, topLeft = Offset(96f, 30f), size = Size(72f, 72f), style = Stroke(4f))
    listOf(54f, 78f).forEach { y -> drawLine(Encre, Offset(96f, y), Offset(168f, y), strokeWidth = 2f) }
    listOf(120f, 144f).forEach { x -> drawLine(Encre, Offset(x, 30f), Offset(x, 102f), strokeWidth = 2f) }
}

/** Raid : réticule de visée sur des lignes de balayage. */
private fun DrawScope.motifRaid() {
    var y = 0f
    while (y < 132f) {
        drawRect(Encre.copy(alpha = 0.18f), topLeft = Offset(0f, y), size = Size(200f, 2f))
        y += 6f
    }
    val centre = Offset(130f, 62f)
    drawCircle(Encre, radius = 46f, center = centre, style = Stroke(3f))
    drawCircle(Encre, radius = 26f, center = centre, style = Stroke(3f))
    drawLine(Encre, Offset(130f, 0f), Offset(130f, 30f), strokeWidth = 3f)
    drawLine(Encre, Offset(130f, 94f), Offset(130f, 132f), strokeWidth = 3f)
    drawLine(Encre, Offset(70f, 62f), Offset(104f, 62f), strokeWidth = 3f)
    drawLine(Encre, Offset(156f, 62f), Offset(200f, 62f), strokeWidth = 3f)
    drawCircle(Encre, radius = 5f, center = centre)
}

/** Cartes : courbes de niveau sur une grille de coordonnées, et un repère. */
private fun DrawScope.motifCartes() {
    val grille = Encre.copy(alpha = 0.35f)
    listOf(30f, 70f, 110f, 150f, 190f).forEach { x -> drawLine(grille, Offset(x, 0f), Offset(x, 132f), strokeWidth = 1f) }
    listOf(33f, 66f, 99f).forEach { y -> drawLine(grille, Offset(0f, y), Offset(210f, y), strokeWidth = 1f) }
    val trait = Stroke(2.5f)
    drawPath(
        Path().apply {
            moveTo(60f, 110f)
            cubicTo(70f, 70f, 110f, 50f, 150f, 60f)
            cubicTo(190f, 70f, 190f, 110f, 160f, 120f)
            cubicTo(130f, 130f, 70f, 140f, 60f, 110f)
            close()
        },
        Encre,
        style = trait,
    )
    drawPath(
        Path().apply {
            moveTo(85f, 95f)
            cubicTo(93f, 73f, 120f, 63f, 143f, 69f)
            cubicTo(166f, 75f, 165f, 97f, 148f, 103f)
            cubicTo(131f, 109f, 93f, 115f, 85f, 95f)
            close()
        },
        Encre,
        style = trait,
    )
    drawPath(
        Path().apply {
            moveTo(108f, 84f)
            cubicTo(112f, 74f, 126f, 70f, 136f, 74f)
            cubicTo(146f, 78f, 145f, 87f, 137f, 90f)
            cubicTo(129f, 93f, 112f, 94f, 108f, 84f)
            close()
        },
        Encre,
        style = trait,
    )
    drawPath(Path().apply { moveTo(40f, 20f); cubicTo(70f, 4f, 120f, 2f, 160f, 16f) }, Encre, style = trait)
    drawPath(polygone(178f, 26f, 170f, 40f, 186f, 40f), Encre)
}

/** Objectifs : trois chevrons qui avancent. */
private fun DrawScope.motifObjectifs() {
    listOf(0.25f, 0.5f, 1f).forEachIndexed { i, alpha ->
        val x = 60f + 40f * i
        drawPath(polygone(x, 0f, x + 22f, 0f, x + 62f, 66f, x + 22f, 132f, x, 132f, x + 40f, 66f), Encre.copy(alpha = alpha))
    }
}

/** Builds : plan technique d'une arme sur papier millimétré, avec sa cote. */
private fun DrawScope.motifBuilds() {
    val grille = Encre.copy(alpha = 0.3f)
    var v = 20f
    while (v <= 200f) {
        drawLine(grille, Offset(v, 0f), Offset(v, 132f), strokeWidth = 0.8f)
        if (v <= 120f) drawLine(grille, Offset(0f, v), Offset(210f, v), strokeWidth = 0.8f)
        v += 20f
    }
    val trait = Stroke(2.5f)
    drawRect(Encre, topLeft = Offset(70f, 40f), size = Size(96f, 30f), style = trait)
    drawLine(Encre, Offset(166f, 50f), Offset(196f, 50f), strokeWidth = 2.5f)
    drawPath(Path().apply { moveTo(70f, 70f); lineTo(56f, 104f); lineTo(78f, 104f); lineTo(88f, 70f) }, Encre, style = trait)
    drawPath(Path().apply { moveTo(110f, 70f); lineTo(110f, 86f); lineTo(132f, 86f) }, Encre, style = trait)
    drawCircle(Encre, radius = 7f, center = Offset(150f, 55f), style = trait)
    drawLine(Encre, Offset(70f, 24f), Offset(166f, 24f), strokeWidth = 1.5f)
    drawLine(Encre, Offset(70f, 18f), Offset(70f, 30f), strokeWidth = 1.5f)
    drawLine(Encre, Offset(166f, 18f), Offset(166f, 30f), strokeWidth = 1.5f)
}

/** Petite étiquette en capitales dans une carte (« CE QU'IL TE FAUT », « MEILLEURE CARTE »…). */
@Composable
fun Etiquette(texte: String, couleur: Color, modifier: Modifier = Modifier) {
    Text(
        texte.uppercase(),
        style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 2.sp),
        color = couleur,
        modifier = modifier,
    )
}
