package io.github.slaynaw.carnetduraider.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/** Zoom maximal du plan (l'image fait 2048 px de côté). */
private const val ZOOM_MAX = 6f

/** Plan en jeu de la carte (`assets/cartes/<id>.webp`), décodé en 16 bits pour ménager la mémoire. */
private suspend fun chargerPlan(context: Context, carteId: String, reduction: Int = 1): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = reduction
        }
        context.assets.open("cartes/$carteId.webp").use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    } catch (e: IOException) {
        null
    }
}

/**
 * Plan interactif simplifié d'une carte : le plan en jeu, les zones (les plus petites apparaissent en
 * zoomant) et les trappes de sortie, avec leurs noms français officiels ; en tête, les conditions en
 * cours sur cette carte et leur minuteur. Deux doigts pour zoomer et déplacer, double-tap pour zoomer.
 */
@Composable
fun EcranPlan(vm: CarnetViewModel, carteId: String, onRetour: () -> Unit) {
    val carte = vm.catalogue.carte(carteId) ?: return
    val context = LocalContext.current.applicationContext
    val image by produceState<ImageBitmap?>(null, carteId) { value = chargerPlan(context, carteId) }
    var echelle by remember(carteId) { mutableFloatStateOf(1f) }
    var decalage by remember(carteId) { mutableStateOf(Offset.Zero) }
    var zones by rememberSaveable { mutableStateOf(true) }
    var sorties by rememberSaveable { mutableStateOf(true) }

    val etat by vm.calendrier.etat.collectAsStateWithLifecycle()
    val calendrier = remember(etat.donnees) { etat.donnees?.let(vm::calendrierOfficiel) }
    val t = maintenant(1_000)
    val enCours = calendrier?.enCours(vm.region, t)?.filter { it.carteId == carteId }.orEmpty()
    val prochaine = calendrier?.aVenir(vm.region, t, 24 * 3_600_000L)?.firstOrNull { it.carteId == carteId }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                Bandeau("Plan", Signal.Cyan)
            }
            Text(carte.nom, style = MaterialTheme.typography.headlineMedium)
            enCours.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.condition, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (c.majeure) Signal.Rouge else Signal.Cyan, modifier = Modifier.weight(1f))
                    Text("encore ${chrono(c.fin - t)}", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                }
            }
            if (enCours.isEmpty()) {
                Text(
                    prochaine?.let { "Aucune condition en ce moment. Prochaine\u00a0: ${it.condition} dans ${chrono(it.debut - t)}" }
                        ?: "Aucune condition annoncée sur cette carte.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Signal.TexteDoux,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Interrupteur("Zones", zones) { zones = !zones }
                if (carte.plan.sorties.isNotEmpty()) Interrupteur("Trappes de sortie", sorties) { sorties = !sorties }
                if (echelle > 1f) TextButton(onClick = { echelle = 1f; decalage = Offset.Zero }) { Text("Vue entière", color = Signal.Cyan) }
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .background(Signal.Fond),
        ) {
            val largeur = constraints.maxWidth.toFloat()
            val hauteur = constraints.maxHeight.toFloat()
            val cote = minOf(largeur, hauteur)
            // Le plan carré est centré ; [decalage] et [echelle] le déplacent et l'agrandissent.
            val origine = Offset((largeur - cote) / 2f, (hauteur - cote) / 2f)
            // Le plan couvre toujours le centre de la vue : on ne le perd jamais de vue.
            fun limiter(d: Offset, e: Float) = Offset(
                d.x.coerceIn(largeur / 2f - origine.x - cote * e, largeur / 2f - origine.x),
                d.y.coerceIn(hauteur / 2f - origine.y - cote * e, hauteur / 2f - origine.y),
            )
            fun versEcran(u: Float, v: Float) = Offset(origine.x + decalage.x + u * cote * echelle, origine.y + decalage.y + v * cote * echelle)

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(largeur, hauteur) {
                        detectTransformGestures { centre, deplacement, zoom, _ ->
                            val nouvelle = (echelle * zoom).coerceIn(1f, ZOOM_MAX)
                            // Le point sous les doigts reste sous les doigts.
                            val point = (centre - origine - decalage) / echelle
                            decalage = limiter(centre - origine - point * nouvelle + deplacement, nouvelle)
                            echelle = nouvelle
                        }
                    }
                    .pointerInput(largeur, hauteur) {
                        detectTapGestures(onDoubleTap = { p ->
                            val nouvelle = if (echelle >= ZOOM_MAX * 0.75f) 1f else (echelle * 2f).coerceAtMost(ZOOM_MAX)
                            val point = (p - origine - decalage) / echelle
                            decalage = if (nouvelle == 1f) Offset.Zero else limiter(p - origine - point * nouvelle, nouvelle)
                            echelle = nouvelle
                        })
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val haut = versEcran(0f, 0f)
                    image?.let {
                        drawImage(
                            it,
                            dstOffset = IntOffset(haut.x.roundToInt(), haut.y.roundToInt()),
                            dstSize = IntSize((cote * echelle).roundToInt(), (cote * echelle).roundToInt()),
                        )
                    }
                    if (sorties) {
                        carte.plan.sorties.forEach { s ->
                            val p = versEcran(s.x, s.y)
                            drawCircle(Signal.Fond, radius = 9.dp.toPx(), center = p)
                            drawCircle(Signal.Cyan, radius = 6.dp.toPx(), center = p)
                        }
                    }
                }
                if (image == null) {
                    Text("Chargement du plan…", color = Signal.TexteDoux, modifier = Modifier.align(Alignment.Center))
                }
                // Noms : grandes zones toujours, les autres en zoomant.
                if (zones) {
                    carte.plan.zones.filter { it.detail == 0 || (it.detail == 1 && echelle >= 1.8f) || echelle >= 3f }.forEach { z ->
                        Etiquette(z.nom, versEcran(z.x, z.y), principale = z.detail == 0)
                    }
                }
                if (sorties && echelle >= 3f) {
                    val sousLePoint = with(LocalDensity.current) { 22.dp.toPx() }
                    carte.plan.sorties.forEach { s -> Etiquette(s.nom, versEcran(s.x, s.y) + Offset(0f, sousLePoint), principale = false, couleur = Signal.Cyan) }
                }
            }
        }
    }
}

/** Nom posé sur le plan, centré sur son point, lisible sur l'image. */
@Composable
private fun Etiquette(texte: String, point: Offset, principale: Boolean, couleur: Color = Signal.Texte) {
    Surface(
        color = Signal.Fond.copy(alpha = 0.72f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .offset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) }
            .layout { mesurable, contraintes ->
                val p = mesurable.measure(contraintes)
                layout(p.width, p.height) { p.place(-p.width / 2, -p.height / 2) }
            },
    ) {
        Text(
            texte,
            style = if (principale) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = couleur,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Interrupteur(texte: String, actif: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = actif,
        onClick = onClick,
        label = { Text(texte) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Signal.Cyan,
            selectedLabelColor = Signal.SurCouleur,
            labelColor = Signal.TexteDoux,
        ),
    )
}

/** Petite vue du plan pour l'onglet Cartes (même image, décodée en 256 px). */
@Composable
fun ApercuPlan(carteId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val image by produceState<ImageBitmap?>(null, carteId) { value = chargerPlan(context, carteId, reduction = 8) }
    Box(modifier.background(Signal.Releve)) {
        image?.let { img ->
            Canvas(Modifier.fillMaxSize()) {
                // Recadrage carré, centré.
                val c = minOf(size.width, size.height)
                drawImage(
                    img,
                    dstOffset = IntOffset(((size.width - c) / 2).roundToInt(), ((size.height - c) / 2).roundToInt()),
                    dstSize = IntSize(c.roundToInt(), c.roundToInt()),
                )
            }
        }
    }
}
