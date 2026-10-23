package io.github.slaynaw.carnetduraider.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.ui.theme.Barlow
import io.github.slaynaw.carnetduraider.ui.theme.Prompt
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlin.math.roundToInt

/** Hauteur du bandeau de section, et de la grille dans laquelle ses motifs sont dessinés. */
private val HAUTEUR = 132.dp

/** Hauteur du bandeau replié, quand la liste de l'onglet défile. */
private val HAUTEUR_REPLIEE = 56.dp

/** Le bandeau se replie dès que la liste a défilé de plus que cela. */
private val SEUIL_REPLI = 24.dp
private const val GRILLE = 132f

/** Carte mise en avant (prochain raid, objectif terminé…) : coin bas droit coupé, comme les bandeaux. */
val FormeCoupee = CutCornerShape(bottomEnd = 14.dp)

/**
 * Bandeau en tête de chaque onglet : la couleur de la section, son code (« 03 / OPÉRATION »), son nom en
 * capitales et un motif qui lui est propre (bandes du logo, caisse, réticule, courbes de niveau, chevrons,
 * plan technique). On sait d'un coup d'œil sur quelle section on est. Il occupe toute la largeur de l'écran,
 * même dans une liste avec des marges ([marge]).
 *
 * Le motif vit doucement, en boucle : un reflet passe sur les bandes, les cases de la caisse s'assombrissent
 * une à une, la cible cherche puis verrouille, le repère flotte et émet une onde, une impulsion pousse les
 * chevrons, une ligne de scan descend sur le plan. Le texte ne bouge jamais. Avec « Supprimer les
 * animations » d'Android, le motif reste immobile.
 *
 * [repli] (de 0 à 1) le réduit à une barre de 56 dp, avec le nom seul : la liste de l'onglet gagne de la
 * place quand elle défile ([EcranDeSection]).
 */
@Composable
fun BandeauSection(onglet: Onglet, sousTitre: String, marge: Dp = 20.dp, repli: Float = 0f) {
    val temps = if (animationsActives()) tempsEnBoucle(periode(onglet)) else null
    val visible = 1f - repli
    Box(
        Modifier
            .pleineLargeur(marge)
            .height(lerp(HAUTEUR, HAUTEUR_REPLIEE, repli))
            .clip(CutCornerShape(bottomEnd = lerp(34.dp, 22.dp, repli)))
            .background(onglet.couleur),
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) { motif(onglet, temps?.value) }
        Column(Modifier.padding(start = 20.dp, top = lerp(18.dp, 12.dp, repli), end = 20.dp)) {
            Text(
                onglet.code,
                style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 3.sp),
                color = Signal.SurCouleur,
                modifier = Modifier.reduit(visible),
            )
            Text(
                onglet.titre.uppercase(),
                style = TextStyle(
                    fontFamily = Prompt,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (40f - 16f * repli).sp,
                    lineHeight = (44f - 14f * repli).sp,
                    letterSpacing = 1.sp,
                ),
                color = Signal.SurCouleur,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                sousTitre,
                style = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
                color = Signal.SurCouleur,
                maxLines = 1,
                modifier = Modifier.reduit(visible),
            )
        }
    }
}

/**
 * Un onglet : son bandeau, puis sa liste ([contenu], qui défile avec [etat]). Le bandeau se replie dès que
 * la liste défile et se déplie quand on revient tout en haut ; sans animations d'Android, d'un coup.
 */
@Composable
fun EcranDeSection(
    onglet: Onglet,
    sousTitre: String,
    etat: LazyListState = rememberLazyListState(),
    contenu: @Composable (LazyListState) -> Unit,
) {
    val seuil = with(LocalDensity.current) { SEUIL_REPLI.roundToPx() }
    val replie by remember(etat) { derivedStateOf { etat.firstVisibleItemIndex > 0 || etat.firstVisibleItemScrollOffset > seuil } }
    val repli by animateFloatAsState(
        targetValue = if (replie) 1f else 0f,
        animationSpec = if (animationsActives()) tween(220) else snap(),
        label = "repli",
    )
    Column(Modifier.fillMaxSize()) {
        BandeauSection(onglet, sousTitre, marge = 0.dp, repli = repli)
        Box(Modifier.weight(1f)) { contenu(etat) }
    }
}

/** Réduit la hauteur de l'élément à [part] de la sienne (0 : caché), en le rendant transparent d'autant. */
private fun Modifier.reduit(part: Float): Modifier = this
    .graphicsLayer { alpha = part }
    .layout { mesurable, contraintes ->
        val place = mesurable.measure(contraintes)
        layout(place.width, (place.height * part).roundToInt()) { place.place(0, 0) }
    }

/** Faux si le joueur a coupé les animations d'Android (échelle de durée des animations à 0). */
@Composable
fun animationsActives(): Boolean {
    val contexte = LocalContext.current
    return remember(contexte) {
        Settings.Global.getFloat(contexte.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Le temps écoulé dans la boucle du motif, de 0 à [periode] millisecondes, lu seulement au dessin. */
@Composable
private fun tempsEnBoucle(periode: Int): State<Float> =
    rememberInfiniteTransition(label = "motif").animateFloat(
        initialValue = 0f,
        targetValue = periode.toFloat(),
        animationSpec = infiniteRepeatable(tween(periode, easing = LinearEasing)),
        label = "temps",
    )

/** Durée d'une boucle du motif de chaque section, en millisecondes. */
private fun periode(onglet: Onglet): Int = when (onglet) {
    Onglet.ACCUEIL -> 4500
    Onglet.OBJETS -> 4500
    Onglet.RAID -> 7000
    Onglet.CARTES -> 3000
    Onglet.OBJECTIFS -> 3600
    Onglet.BUILDS -> 3400
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
 * de la hauteur du bandeau), à l'instant [ms] de sa boucle (null : immobile).
 */
private fun DrawScope.motif(onglet: Onglet, ms: Float?) {
    val largeur = when (onglet) {
        Onglet.ACCUEIL, Onglet.CARTES, Onglet.BUILDS -> 210f
        else -> 200f
    }
    val s = size.height / GRILLE
    translate(left = size.width - largeur * s) {
        scale(s, s, pivot = Offset.Zero) {
            when (onglet) {
                Onglet.ACCUEIL -> motifAccueil(ms)
                Onglet.OBJETS -> motifObjets(ms)
                Onglet.RAID -> motifRaid(ms)
                Onglet.CARTES -> motifCartes(ms)
                Onglet.OBJECTIFS -> motifObjectifs(ms)
                Onglet.BUILDS -> motifBuilds(ms)
            }
        }
    }
}

private val Encre = Signal.SurCouleur

/** Courbes des animations : adoucie aux deux bouts, et lente au départ et à l'arrivée pour ce qui traverse. */
private val Douce = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val Traversee = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
private val Onde = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
private val Impulsion = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

/** Où en est (de 0 à 1) une boucle de [periode] ms à l'instant [ms], qui commence [retard] ms plus tard. */
private fun fraction(ms: Float, periode: Float, retard: Float = 0f): Float = ((ms - retard) % periode + periode) % periode / periode

/**
 * Valeur à l'instant [f] (de 0 à 1) d'une suite d'images clés [cles] (instant, valeur, instant, valeur…),
 * adoucie entre deux clés ; avant la première et après la dernière, la valeur de celle-ci.
 */
private fun images(f: Float, vararg cles: Float): Float {
    if (f <= cles[0]) return cles[1]
    var i = 0
    while (i + 3 < cles.size) {
        val t0 = cles[i]
        val t1 = cles[i + 2]
        if (f <= t1) return cles[i + 1] + (cles[i + 3] - cles[i + 1]) * Douce.transform((f - t0) / (t1 - t0))
        i += 2
    }
    return cles[cles.size - 1]
}

private fun polygone(vararg points: Float): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    close()
}

/** Accueil : les quatre bandes du logo, en grand ; un reflet clair les traverse de temps en temps. */
private fun DrawScope.motifAccueil(ms: Float?) {
    val bandes = (0..3).map { i ->
        val x = 40f + 40f * i
        polygone(x, 132f, x + 26f, 132f, x + 78f, 0f, x + 52f, 0f)
    }
    bandes.zip(listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge)).forEach { (bande, couleur) -> drawPath(bande, couleur) }
    if (ms == null) return
    val f = fraction(ms, 4500f)
    if (f >= 0.22f) return
    val x = -80f + 330f * Traversee.transform(f / 0.22f)
    clipPath(Path().apply { bandes.forEach { addPath(it) } }) {
        drawPath(polygone(x, 132f, x + 16f, 132f, x + 68f, 0f, x + 52f, 0f), Color.White.copy(alpha = 0.45f))
    }
}

/** Les cases de la caisse dans l'ordre où l'inventaire les passe en revue (colonne, ligne), en serpentin. */
private val OrdreInventaire = listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 1 to 1, 0 to 1, 0 to 2, 1 to 2, 2 to 2)

/** Objets : rayures « danger » et caisse vue de face, dont les cases s'assombrissent une à une. */
private fun DrawScope.motifObjets(ms: Float?) {
    clipRect(left = 60f, top = 0f, right = 200f, bottom = 132f) {
        var x = -80f
        while (x < 200f) {
            drawLine(Encre.copy(alpha = 0.9f), Offset(x, 132f), Offset(x + 132f, 0f), strokeWidth = 11f)
            x += 31f
        }
    }
    drawRect(Signal.Or, topLeft = Offset(96f, 30f), size = Size(72f, 72f))
    if (ms != null) {
        OrdreInventaire.forEachIndexed { rang, (colonne, ligne) ->
            val f = fraction(ms, 4500f, 500f * rang)
            val alpha = when {
                f < 0.05f -> 0.4f * f / 0.05f
                f < 0.16f -> 0.4f * (1f - (f - 0.05f) / 0.11f)
                else -> 0f
            }
            if (alpha > 0f) drawRect(Encre.copy(alpha = alpha), topLeft = Offset(96f + 24f * colonne, 30f + 24f * ligne), size = Size(24f, 24f))
        }
    }
    drawRect(Encre, topLeft = Offset(96f, 30f), size = Size(72f, 72f), style = Stroke(4f))
    listOf(54f, 78f).forEach { y -> drawLine(Encre, Offset(96f, y), Offset(168f, y), strokeWidth = 2f) }
    listOf(120f, 144f).forEach { x -> drawLine(Encre, Offset(x, 30f), Offset(x, 102f), strokeWidth = 2f) }
}

/** Raid : réticule de visée sur des lignes de balayage ; il erre en cherchant sa cible, puis la verrouille. */
private fun DrawScope.motifRaid(ms: Float?) {
    var y = 0f
    while (y < 132f) {
        drawRect(Encre.copy(alpha = 0.18f), topLeft = Offset(0f, y), size = Size(200f, 2f))
        y += 6f
    }
    val f = ms?.let { fraction(it, 7000f) }
    val dx = f?.let { images(it, 0f, 0f, 0.16f, -14f, 0.32f, -7f, 0.48f, 11f, 0.64f, 13f, 0.78f, 0f) } ?: 0f
    val dy = f?.let { images(it, 0f, 0f, 0.16f, 9f, 0.32f, -11f, 0.48f, -5f, 0.64f, 9f, 0.78f, 0f) } ?: 0f
    val serre = f?.let { images(it, 0.76f, 1f, 0.83f, 0.88f, 0.9f, 1f) } ?: 1f
    val pouls = f?.let { images(it, 0.78f, 1f, 0.84f, 1.8f, 0.9f, 1f) } ?: 1f
    // En mouvement, les branches du viseur dépassent pour ne jamais laisser voir leur bout.
    val depasse = if (f == null) 0f else 20f
    translate(dx, dy) {
        val centre = Offset(130f, 62f)
        drawCircle(Encre, radius = 46f * serre, center = centre, style = Stroke(3f))
        drawCircle(Encre, radius = 26f * serre, center = centre, style = Stroke(3f))
        drawLine(Encre, Offset(130f, -depasse), Offset(130f, 30f), strokeWidth = 3f)
        drawLine(Encre, Offset(130f, 94f), Offset(130f, 132f + depasse), strokeWidth = 3f)
        drawLine(Encre, Offset(70f - depasse, 62f), Offset(104f, 62f), strokeWidth = 3f)
        drawLine(Encre, Offset(156f, 62f), Offset(200f + depasse, 62f), strokeWidth = 3f)
        drawCircle(Encre, radius = 5f * pouls, center = centre)
    }
}

/** Cartes : courbes de niveau sur une grille de coordonnées, et un repère qui flotte en émettant une onde. */
private fun DrawScope.motifCartes(ms: Float?) {
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
    if (ms != null) {
        val f = fraction(ms, 3000f, 400f)
        if (f < 0.4f) {
            val a = Onde.transform(f / 0.4f)
            drawCircle(Encre.copy(alpha = 0.7f * (1f - a)), radius = 6f * (1f + 3.5f * a), center = Offset(178f, 35f), style = Stroke(1.5f))
        }
    }
    val flotte = ms?.let { val f = fraction(it, 3000f); 3f * Douce.transform(if (f < 0.5f) f * 2f else (1f - f) * 2f) } ?: 0f
    drawPath(polygone(178f, 26f - flotte, 170f, 40f - flotte, 186f, 40f - flotte), Encre)
}

/** Un chevron du motif des objectifs, à l'abscisse [x]. */
private fun chevron(x: Float): Path = polygone(x, 0f, x + 22f, 0f, x + 62f, 66f, x + 22f, 132f, x, 132f, x + 40f, 66f)

/** Objectifs : trois chevrons qui avancent ; une impulsion les traverse et les pousse l'un après l'autre. */
private fun DrawScope.motifObjectifs(ms: Float?) {
    val bases = listOf(0.25f, 0.5f, 1f)
    val eclats = listOf(0.85f, 0.95f, 1f)
    val retards = listOf(90f, 170f, 250f)
    bases.forEachIndexed { i, base ->
        val f = ms?.let { fraction(it, 3600f, retards[i]) }
        val alpha = f?.let { images(it, 0.05f, base, 0.1f, eclats[i], 0.22f, base) } ?: base
        val pousse = f?.let { images(it, 0.05f, 0f, 0.1f, 6f, 0.22f, 0f) } ?: 0f
        drawPath(chevron(60f + 40f * i + pousse), Encre.copy(alpha = alpha))
    }
    if (ms == null) return
    val f = fraction(ms, 3600f)
    if (f < 0.16f) {
        clipRect(left = 0f, top = 0f, right = 200f, bottom = 132f) {
            drawPath(chevron(-70f + 280f * Impulsion.transform(f / 0.16f)), Encre.copy(alpha = 0.9f))
        }
    }
}

/** Builds : plan technique d'une arme sur papier millimétré, avec sa cote ; une ligne de scan le parcourt. */
private fun DrawScope.motifBuilds(ms: Float?) {
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
    if (ms == null) return
    val f = fraction(ms, 3400f)
    val y = if (f < 0.68f) -4f + 136f * Traversee.transform(f / 0.68f) else 132f
    val visible = when {
        f < 0.08f -> f / 0.08f
        f < 0.68f -> 1f
        f < 0.7f -> 1f - (f - 0.68f) / 0.02f
        else -> 0f
    }
    if (visible <= 0f) return
    drawRect(
        Brush.verticalGradient(listOf(Encre.copy(alpha = 0f), Encre.copy(alpha = 0.14f * visible)), startY = y - 18f, endY = y),
        topLeft = Offset(0f, y - 18f),
        size = Size(210f, 18f),
    )
    drawRect(Encre.copy(alpha = 0.5f * visible), topLeft = Offset(0f, y - 1f), size = Size(210f, 2f))
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
