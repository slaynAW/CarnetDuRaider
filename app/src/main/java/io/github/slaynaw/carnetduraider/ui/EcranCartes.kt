package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.core.calendrier.Region
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlinx.coroutines.launch

/** Durée affichée dans « Dans les prochaines heures ». */
private const val HORIZON_MS = 6 * Calendrier.UNE_HEURE

@Composable
fun EcranCartes(vm: CarnetViewModel, ouvrirPlan: (String) -> Unit) {
    val etat by vm.calendrier.etat.collectAsStateWithLifecycle()
    val liens = LocalUriHandler.current
    val portee = rememberCoroutineScope()
    val t = maintenant(1_000)
    val calendrier = remember(etat.donnees) { etat.donnees?.let(vm::calendrierOfficiel) }
    val region = vm.region

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BandeauSection(Onglet.CARTES, "Plans, zones et conditions")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Region.entries.forEach { r ->
                        PuceFiltre(r.libelle, choisie = r == region, couleur = Signal.Cyan, onClick = { vm.changerRegion(r) })
                    }
                }
                Text(
                    when {
                        etat.chargement -> "Lecture du calendrier officiel d'Embark…"
                        etat.donnees != null -> "Calendrier officiel d'Embark, lu ${depuis(etat.donnees!!.luLe, t)}. Heures de ton téléphone."
                        else -> "Le calendrier officiel d'Embark n'a pas encore été lu."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDiscret,
                )
                etat.erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal.Rouge) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runCatching { liens.openUri(Calendrier.ADRESSE) } }, shape = RoundedCornerShape(6.dp)) {
                        Text("Calendrier officiel", color = Signal.Cyan)
                    }
                    TextButton(onClick = { portee.launch { vm.calendrier.actualiser() } }, enabled = !etat.chargement) {
                        Text("Actualiser", color = Signal.TexteDoux)
                    }
                }
            }
        }

        // Chaque carte avec ses conditions en cours : on sait tout de suite où il se passe quelque chose.
        if (calendrier != null) {
            val enCours = calendrier.enCours(region, t)
            item { Titre("Conditions de la carte") }
            val cartes = vm.catalogue.donnees.cartes.map { it.nom } +
                enCours.map { it.carte }.filter { nom -> vm.catalogue.donnees.cartes.none { it.nom == nom } }.distinct()
            items(cartes, key = { "maintenant-$it" }) { carte ->
                val id = vm.catalogue.donnees.cartes.firstOrNull { it.nom == carte }?.id
                CarteMaintenant(carte, enCours.filter { it.carte == carte }, t, onClick = id?.let { { ouvrirPlan(it) } })
            }
        }

        item { Titre("Plans des cartes") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.catalogue.donnees.cartes.forEach { c ->
                    Surface(onClick = { ouvrirPlan(c.id) }, color = Signal.Surface, shape = RoundedCornerShape(6.dp)) {
                        Column(Modifier.width(132.dp)) {
                            ApercuPlan(c.id, Modifier.fillMaxWidth().height(96.dp))
                            Text(c.nom, style = MaterialTheme.typography.labelMedium, maxLines = 2, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }

        if (calendrier != null) {
            val aVenir = calendrier.aVenir(region, t, HORIZON_MS)
            item { Titre("Dans les prochaines heures") }
            if (aVenir.isEmpty()) {
                item { Discret("Aucune condition annoncée dans les 6 prochaines heures.") }
            }
            aVenir.groupBy { it.debut }.forEach { (debut, creneaux) ->
                item(key = "avenir-$debut") { Horaire(debut, creneaux, t) }
            }

            val prochaines = calendrier.prochaineFois(region, t)
            if (prochaines.isNotEmpty()) {
                item { Titre("Prochaine fois") }
                item { Discret("Touche la cloche pour être prévenu avant le début d'une condition.") }
                items(prochaines, key = { "prochaine-${it.conditionId ?: it.condition}" }) { ProchaineFois(vm, it, t) }
            }
            if (calendrier.connuJusqua(region) < t + HORIZON_MS) {
                item { Discret("Le calendrier lu s'arrête bientôt\u00a0: actualise-le une fois connecté.") }
            }
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Etiquette(texte, Signal.Cyan, Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Discret(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
}

@Composable
private fun Point(creneau: Creneau) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (creneau.majeure) Signal.Rouge else Signal.Cyan))
}

/** Une carte et ses conditions en cours, avec leur minuteur ; la toucher ouvre son plan. */
@Composable
private fun CarteMaintenant(carte: String, creneaux: List<Creneau>, t: Long, onClick: (() -> Unit)?) {
    Surface(onClick = { onClick?.invoke() }, enabled = onClick != null, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconeCarte(if (creneaux.isEmpty()) Signal.TexteDiscret else Signal.Cyan)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(carte, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (creneaux.isEmpty()) {
                    Text("Aucune condition", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
                }
                creneaux.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            c.condition,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (c.majeure) Signal.Rouge else Signal.Cyan,
                            modifier = Modifier.weight(1f),
                        )
                        Text(chrono(c.fin - t), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Signal.TexteDoux)
                    }
                }
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Plan", tint = Signal.TexteDiscret)
            }
        }
    }
}

/** Une carte pliée, en traits : l'icône des cartes du jeu dans les listes. */
@Composable
private fun IconeCarte(couleur: Color) {
    Canvas(Modifier.size(22.dp).clearAndSetSemantics { }) {
        val u = size.width / 24f
        val trait = Stroke(width = 2f * u, join = StrokeJoin.Round)
        val contour = Path().apply {
            moveTo(3f * u, 6f * u)
            lineTo(9f * u, 4f * u)
            lineTo(15f * u, 6f * u)
            lineTo(21f * u, 4f * u)
            lineTo(21f * u, 18f * u)
            lineTo(15f * u, 20f * u)
            lineTo(9f * u, 18f * u)
            lineTo(3f * u, 20f * u)
            close()
        }
        drawPath(contour, couleur, style = trait)
        drawLine(couleur, Offset(9f * u, 4f * u), Offset(9f * u, 18f * u), strokeWidth = 2f * u)
        drawLine(couleur, Offset(15f * u, 6f * u), Offset(15f * u, 20f * u), strokeWidth = 2f * u)
    }
}

@Composable
private fun Horaire(debut: Long, creneaux: List<Creneau>, t: Long) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(heureLocale(debut, t), style = MaterialTheme.typography.titleMedium)
                Text("dans ${chrono(debut - t)}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Signal.Or)
            }
            creneaux.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Point(c)
                    Text("${c.condition} · ${c.carte}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ProchaineFois(vm: CarnetViewModel, c: Creneau, t: Long) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Point(c)
            Column(Modifier.weight(1f)) {
                Text(c.condition, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(c.carte, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            c.conditionId?.let { BoutonAlerte(vm, it, c.condition) }
            Column(horizontalAlignment = Alignment.End) {
                if (c.enCours(t)) {
                    Text("En cours", style = MaterialTheme.typography.titleSmall, color = Signal.Cyan)
                    Text("encore ${chrono(c.fin - t)}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Signal.TexteDoux)
                } else {
                    Text(heureLocale(c.debut, t), style = MaterialTheme.typography.titleSmall)
                    Text("dans ${chrono(c.debut - t)}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Signal.TexteDoux)
                }
            }
        }
    }
}

/** « à l'instant », « il y a 12 min », « il y a 3 h ». */
private fun depuis(instant: Long, t: Long): String {
    val ecart = t - instant
    return if (ecart < 60_000) "à l'instant" else "il y a ${duree(ecart)}"
}
