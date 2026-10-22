package io.github.slaynaw.carnetduraider.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.BilanRaid
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.ObjetATrouver
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Au plus autant d'objectifs et d'objets affichés dans l'animation ; les autres sont comptés. */
private const val OBJECTIFS_AFFICHES = 3
private const val OBJETS_AFFICHES = 5

/**
 * Animation du retour de raid validé. Un objectif terminé de lui-même (tout a été rapporté) est validé à la
 * manière d'ARC Raiders : une bande verte balaie l'écran, « OBJECTIF TERMINÉ » s'y imprime en capitales,
 * puis chaque objectif reçoit son tampon « VALIDÉ ». Les objets qui ont atteint le nombre demandé
 * apparaissent ensuite un à un. Quand toute la liste est trouvée : « Raid terminé, objets trouvés », avec
 * une gerbe de bandes aux couleurs de l'appli. Une touche (ou « Retour ») la ferme.
 */
@Composable
fun CelebrationRaid(vm: CarnetViewModel, bilan: BilanRaid, onFin: () -> Unit) {
    BackHandler(onBack = onFin)
    val entree = remember(bilan) { Animatable(0f) }
    val titre = remember(bilan) { Animatable(0.6f) }
    LaunchedEffect(bilan) {
        entree.animateTo(1f, tween(durationMillis = 300))
        titre.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val restants = vm.aTrouver().count { !it.trouve }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = entree.value }
            .background(Signal.Fond.copy(alpha = 0.95f))
            .clickable(interactionSource = null, indication = null, onClickLabel = "Continuer", onClick = onFin),
        contentAlignment = Alignment.Center,
    ) {
        if (bilan.termine) Gerbe(bilan, Modifier.fillMaxSize())
        Column(
            Modifier.systemBarsPadding().padding(24.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val echelle = Modifier.graphicsLayer {
                scaleX = titre.value
                scaleY = titre.value
            }
            if (bilan.objectifsTermines.isNotEmpty()) {
                BanniereObjectif(bilan.objectifsTermines.size)
                Text(
                    if (bilan.termine) "Raid terminé, objets trouvés\u00a0! La suite s'ajoute à tes objectifs." else "Tout est rapporté. La suite s'ajoute à tes objectifs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bilan.termine) Signal.Texte else Signal.TexteDoux,
                    textAlign = TextAlign.Center,
                )
            } else if (bilan.termine) {
                Bandes(echelle, hauteur = 40.dp, largeurBande = 10.dp)
                Text("Raid terminé", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = echelle)
                Text("Objets trouvés\u00a0!", style = MaterialTheme.typography.headlineSmall, color = Signal.Vert, textAlign = TextAlign.Center)
            } else {
                Text("Objets trouvés", style = MaterialTheme.typography.headlineLarge, color = Signal.Vert, textAlign = TextAlign.Center, modifier = echelle)
                Text(
                    if (restants == 0) "Tout est trouvé." else "Encore ${pluriel(restants, "objet", "objets")} à trouver.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                    textAlign = TextAlign.Center,
                )
            }
            val objectifs = bilan.objectifsTermines.take(OBJECTIFS_AFFICHES)
            val objets = bilan.atteints.take(OBJETS_AFFICHES)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                objectifs.forEachIndexed { rang, o -> ObjectifTermine(vm, o, rang) }
                objets.forEachIndexed { rang, a -> ObjetAtteint(vm, a, objectifs.size + rang) }
            }
            val autres = bilan.objectifsTermines.size - objectifs.size + bilan.atteints.size - objets.size
            if (autres > 0) {
                Text("Et ${pluriel(autres, "autre", "autres")}.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            Text("Touche l'écran pour continuer", style = MaterialTheme.typography.labelMedium, color = Signal.TexteDiscret, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Arrivée d'un élément (de 0 à 1, avec un petit rebond), après les précédents ([rang]). */
@Composable
private fun arrivee(rang: Int): Animatable<Float, AnimationVector1D> {
    val apparition = remember(rang) { Animatable(0f) }
    LaunchedEffect(rang) {
        delay(350L + rang * 150L)
        apparition.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    return apparition
}

private fun Modifier.rebond(apparition: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    alpha = apparition.value.coerceIn(0f, 1f)
    scaleX = 0.7f + 0.3f * apparition.value
    scaleY = 0.7f + 0.3f * apparition.value
}

/**
 * « OBJECTIF TERMINÉ » à la manière d'ARC Raiders : une bande verte inclinée balaie l'écran de gauche à droite,
 * rayée de fines bandes qui défilent, et le titre en capitales s'y resserre.
 */
@Composable
private fun BanniereObjectif(nombre: Int) {
    val balayage = remember { Animatable(0f) }
    val resserrage = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        balayage.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
        resserrage.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }
    val titre = if (nombre > 1) "OBJECTIFS TERMINÉS" else "OBJECTIF TERMINÉ"
    Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val pente = size.height * 0.4f
            val fin = (size.width + pente) * balayage.value
            val bande = Path().apply {
                moveTo(0f, size.height)
                lineTo((fin - pente).coerceAtLeast(0f), size.height)
                lineTo(fin, 0f)
                lineTo(pente.coerceAtMost(fin), 0f)
                close()
            }
            drawPath(bande, Signal.Vert)
            // Rayures sombres qui défilent dans la bande, comme sur les écrans du jeu.
            val pas = 18.dp.toPx()
            val decalage = pas * 2f * resserrage.value
            clipPath(bande) {
                var x = -size.height + decalage % pas - pas
                while (x < fin) {
                    val rayure = Path().apply {
                        moveTo(x, size.height)
                        lineTo(x + 4.dp.toPx(), size.height)
                        lineTo(x + 4.dp.toPx() + pente, 0f)
                        lineTo(x + pente, 0f)
                        close()
                    }
                    drawPath(rayure, Signal.Fond, alpha = 0.12f)
                    x += pas
                }
            }
        }
        Text(
            titre,
            style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (10f - 8f * resserrage.value).sp),
            color = Signal.SurCouleur,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = ((balayage.value - 0.5f) * 2f).coerceIn(0f, 1f) },
        )
    }
}

/** Un objectif dont tout a été rapporté : il arrive, puis reçoit son tampon « VALIDÉ » ; la suite apparaît. */
@Composable
private fun ObjectifTermine(vm: CarnetViewModel, objectif: Objectif, rang: Int) {
    val tampon = remember(rang) { Animatable(0f) }
    LaunchedEffect(rang) {
        delay(900L + rang * 250L)
        tampon.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().rebond(arrivee(rang))) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Signal.Vert))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(vm.planificateur.libelle(objectif), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("Tout est rapporté", style = MaterialTheme.typography.labelMedium, color = Signal.Vert)
            }
            Text(
                "VALIDÉ",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                color = Signal.Vert,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .graphicsLayer {
                        val t = tampon.value
                        alpha = t.coerceIn(0f, 1f)
                        scaleX = 2.2f - 1.2f * t
                        scaleY = 2.2f - 1.2f * t
                        rotationZ = -12f
                    }
                    .border(2.dp, Signal.Vert, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Un objet qui a atteint son nombre : il arrive avec un petit rebond, coché en vert. */
@Composable
private fun ObjetAtteint(vm: CarnetViewModel, a: ObjetATrouver, rang: Int) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().rebond(arrivee(rang))) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                IconeObjet(a.objetId, vm.catalogue.objet(a.objetId)?.rarete, 44.dp)
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Signal.Vert,
                    modifier = Modifier.align(Alignment.BottomEnd).size(18.dp).background(Signal.Fond, CircleShape),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(vm.catalogue.nomObjet(a.objetId), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${minOf(a.trouves, a.voulu)}/${a.voulu} · " + a.pour.joinToString(" · ") { it.libelle },
                    style = MaterialTheme.typography.labelMedium,
                    color = Signal.TexteDoux,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Une bande de la gerbe : direction, vitesse, couleur et inclinaison de départ. */
private class BandeGerbe(val angle: Float, val vitesse: Float, val couleur: Color, val inclinaison: Float)

/** Gerbe de bandes inclinées (le motif du logo) qui jaillit du haut de l'écran et retombe en s'effaçant. */
@Composable
private fun Gerbe(bilan: BilanRaid, modifier: Modifier = Modifier) {
    val temps = remember(bilan) { Animatable(0f) }
    LaunchedEffect(bilan) { temps.animateTo(1f, tween(durationMillis = 1800, easing = LinearEasing)) }
    val bandes = remember {
        val hasard = Random(7)
        val couleurs = listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge)
        List(40) { i ->
            BandeGerbe(
                angle = hasard.nextFloat() * 2f * PI.toFloat(),
                vitesse = 0.35f + hasard.nextFloat() * 0.5f,
                couleur = couleurs[i % couleurs.size],
                inclinaison = hasard.nextFloat() * 360f,
            )
        }
    }
    Canvas(modifier) {
        val t = temps.value
        if (t >= 1f) return@Canvas
        val centre = Offset(size.width / 2f, size.height * 0.3f)
        val portee = size.minDimension * 0.6f
        val chute = size.height * 0.45f
        val largeur = 8.dp.toPx()
        val hauteur = 22.dp.toPx()
        val elan = 1f - (1f - t) * (1f - t)
        bandes.forEach { b ->
            val distance = portee * b.vitesse * elan
            val position = centre + Offset(cos(b.angle) * distance, sin(b.angle) * distance + chute * t * t)
            rotate(b.inclinaison + 300f * t, pivot = position) {
                drawRect(b.couleur, topLeft = position - Offset(largeur / 2f, hauteur / 2f), size = Size(largeur, hauteur), alpha = 1f - t)
            }
        }
    }
}
