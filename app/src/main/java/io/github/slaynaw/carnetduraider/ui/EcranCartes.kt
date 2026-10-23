package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    var aVenirChoisi by rememberSaveable { mutableStateOf(false) }
    val enCours = calendrier?.enCours(region, t).orEmpty()
    val cartes = vm.catalogue.donnees.cartes
    // Une condition sur une carte absente des données du jeu s'affiche quand même, sans plan.
    val inconnues = enCours.map { it.carte }.filter { nom -> cartes.none { it.nom == nom } }.distinct()
    val actives = cartes.count { c -> enCours.any { it.carte == c.nom } } + inconnues.size

    EcranDeSection(
        Onglet.CARTES,
        if (calendrier != null) pluriel(actives, "carte avec une condition en cours", "cartes avec une condition en cours") else "Plans, zones et conditions",
    ) { defilement, haut ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = defilement,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = haut + 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when {
                                etat.chargement -> "Lecture du calendrier officiel d'Embark…"
                                etat.donnees != null -> "Calendrier officiel d'Embark · lu ${depuis(etat.donnees!!.luLe, t)}"
                                else -> "Le calendrier officiel d'Embark n'a pas encore été lu."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Signal.TexteDiscret,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { portee.launch { vm.calendrier.actualiser() } }, enabled = !etat.chargement) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualiser le calendrier", tint = Signal.TexteDoux)
                        }
                        ChoixRegion(region, vm::changerRegion)
                    }
                    etat.erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal.Rouge) }
                    TextButton(onClick = { runCatching { liens.openUri(Calendrier.ADRESSE) } }, contentPadding = PaddingValues(0.dp)) {
                        Text("Ouvrir le calendrier officiel", color = Signal.Cyan, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (calendrier != null) {
                item { Onglets(aVenirChoisi, choisir = { aVenirChoisi = it }) }
            }

            if (!aVenirChoisi || calendrier == null) {
                // Chaque carte sur sa capture en jeu, avec ses conditions en cours ; la toucher ouvre son plan.
                items(cartes, key = { "carte-${it.id}" }) { c ->
                    CarteMaintenant(c.id, c.nom, enCours.filter { it.carte == c.nom }, t, calendrier != null, onClick = { ouvrirPlan(c.id) })
                }
                items(inconnues, key = { "inconnue-$it" }) { nom ->
                    CarteMaintenant(null, nom, enCours.filter { it.carte == nom }, t, calendrierLu = true, onClick = null)
                }
            } else {
                val aVenir = calendrier.aVenir(region, t, HORIZON_MS)
                item { Discret("Touche la cloche pour être prévenu avant le début d'une condition.") }
                item { Titre("Dans les prochaines heures") }
                if (aVenir.isEmpty()) {
                    item { Discret("Aucune condition annoncée dans les 6 prochaines heures.") }
                }
                aVenir.groupBy { it.debut }.forEach { (debut, creneaux) ->
                    item(key = "avenir-$debut") { Horaire(vm, debut, creneaux, t) }
                }
                val prochaines = calendrier.prochaineFois(region, t)
                if (prochaines.isNotEmpty()) {
                    item { Titre("Prochaine fois") }
                    items(prochaines, key = { "prochaine-${it.conditionId ?: it.condition}" }) { ProchaineFois(vm, it, t) }
                }
                if (calendrier.connuJusqua(region) < t + HORIZON_MS) {
                    item { Discret("Le calendrier lu s'arrête bientôt\u00a0: actualise-le une fois connecté.") }
                }
            }
        }
    }
}

/** La région du calendrier, en pastille : la toucher ouvre la liste des régions. */
@Composable
private fun ChoixRegion(region: Region, changer: (Region) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { ouvert = true },
            color = Signal.Fond,
            contentColor = Signal.Cyan,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.5.dp, Signal.Cyan),
            modifier = Modifier.semantics { contentDescription = "Région du calendrier\u00a0: ${region.libelle}. Changer de région" },
        ) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(region.libelle, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            Region.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(r.libelle, color = if (r == region) Signal.Cyan else Signal.Texte) },
                    onClick = {
                        changer(r)
                        ouvert = false
                    },
                )
            }
        }
    }
}

/** « En ce moment » / « À venir » : deux vues du calendrier, l'une ou l'autre. */
@Composable
private fun Onglets(aVenir: Boolean, choisir: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Signal.Surface).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(false to "En ce moment", true to "À venir").forEach { (valeur, texte) ->
            val choisi = valeur == aVenir
            Surface(
                selected = choisi,
                onClick = { choisir(valeur) },
                color = if (choisi) Signal.Cyan else Signal.Surface,
                contentColor = if (choisi) Signal.SurCouleur else Signal.TexteDoux,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).height(40.dp).semantics { role = Role.Tab },
            ) {
                Box(contentAlignment = Alignment.Center) { Text(texte, style = MaterialTheme.typography.titleSmall) }
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

/**
 * Une carte, sur sa capture en jeu (assombrie à gauche, comme la meilleure carte du Raid), avec ses
 * conditions en cours et leur minuteur, et « Plan ». Un trait cyan à gauche signale une condition en cours.
 */
@Composable
private fun CarteMaintenant(carteId: String?, carte: String, creneaux: List<Creneau>, t: Long, calendrierLu: Boolean, onClick: (() -> Unit)?) {
    val image = rememberFondCarte(carteId)
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = Signal.Surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.heightIn(min = 92.dp)) {
            if (image != null) FondEnJeu(image)
            Box(Modifier.matchParentSize().padding(end = 0.dp)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(if (creneaux.isEmpty()) Signal.Trait else Signal.Cyan))
            }
            Row(
                Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(carte, style = MaterialTheme.typography.titleMedium)
                    if (creneaux.isEmpty()) {
                        Text(if (calendrierLu) "Aucune condition" else "Conditions\u00a0: calendrier pas encore lu", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
                    }
                    creneaux.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Point(c)
                            Text(
                                c.condition,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (c.majeure) Signal.Rouge else Signal.Texte,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text("encore ${chrono(c.fin - t)}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Signal.TexteDoux)
                        }
                    }
                }
                if (onClick != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Signal.Fond.copy(alpha = 0.72f))
                            .border(1.dp, Signal.Cyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconeCarte(Signal.Cyan)
                        Text("Plan", style = MaterialTheme.typography.labelLarge, color = Signal.Cyan)
                    }
                }
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
private fun Horaire(vm: CarnetViewModel, debut: Long, creneaux: List<Creneau>, t: Long) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(heureLocale(debut, t), style = MaterialTheme.typography.titleMedium)
                Text("dans ${chrono(debut - t)}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Signal.Or)
            }
            creneaux.forEach { c ->
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Point(c)
                    Column(Modifier.weight(1f)) {
                        Text(c.condition, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(c.carte, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                    }
                    c.conditionId?.let { BoutonAlerte(vm, it, c.condition) }
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
