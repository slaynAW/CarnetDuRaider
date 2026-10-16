package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
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
fun EcranCartes(vm: CarnetViewModel) {
    val etat by vm.calendrier.etat.collectAsStateWithLifecycle()
    val liens = LocalUriHandler.current
    val portee = rememberCoroutineScope()
    val t = maintenant()
    val calendrier = remember(etat.donnees) { etat.donnees?.let(vm::calendrierOfficiel) }
    val region = vm.region

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bandeau("Cartes", Signal.Cyan)
                Text("Conditions de la carte", style = MaterialTheme.typography.headlineLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Region.entries.forEach { r ->
                        FilterChip(
                            selected = r == region,
                            onClick = { vm.changerRegion(r) },
                            label = { Text(r.libelle) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Signal.Cyan,
                                selectedLabelColor = Signal.SurCouleur,
                                labelColor = Signal.TexteDoux,
                            ),
                        )
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
                    OutlinedButton(onClick = { runCatching { liens.openUri(Calendrier.ADRESSE) } }) {
                        Text("Calendrier officiel", color = Signal.Cyan)
                    }
                    TextButton(onClick = { portee.launch { vm.calendrier.actualiser() } }, enabled = !etat.chargement) {
                        Text("Actualiser", color = Signal.TexteDoux)
                    }
                }
            }
        }

        if (calendrier != null) {
            val enCours = calendrier.enCours(region, t)
            item { Titre("En ce moment") }
            val cartes = vm.catalogue.donnees.cartes.map { it.nom } +
                enCours.map { it.carte }.filter { nom -> vm.catalogue.donnees.cartes.none { it.nom == nom } }.distinct()
            items(cartes, key = { "maintenant-$it" }) { carte ->
                CarteMaintenant(carte, enCours.filter { it.carte == carte }, t)
            }

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
                items(prochaines, key = { "prochaine-${it.conditionId ?: it.condition}" }) { ProchaineFois(it, t) }
            }
            if (calendrier.connuJusqua(region) < t + HORIZON_MS) {
                item { Discret("Le calendrier lu s'arrête bientôt\u00a0: actualise-le une fois connecté.") }
            }
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Text(texte, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Discret(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
}

@Composable
private fun Point(creneau: Creneau) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (creneau.majeure) Signal.Rouge else Signal.Cyan))
}

@Composable
private fun CarteMaintenant(carte: String, creneaux: List<Creneau>, t: Long) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(carte, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (creneaux.isEmpty()) {
                Text("Aucune condition", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            creneaux.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Point(c)
                    Text(c.condition, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("encore ${duree(c.fin - t)}", style = MaterialTheme.typography.labelMedium, color = Signal.Cyan)
                }
            }
        }
    }
}

@Composable
private fun Horaire(debut: Long, creneaux: List<Creneau>, t: Long) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(heureLocale(debut, t), style = MaterialTheme.typography.titleMedium)
                Text("dans ${duree(debut - t)}", style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
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
private fun ProchaineFois(c: Creneau, t: Long) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Point(c)
            Column(Modifier.weight(1f)) {
                Text(c.condition, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(c.carte, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (c.enCours(t)) {
                    Text("En cours", style = MaterialTheme.typography.titleSmall, color = Signal.Cyan)
                    Text("encore ${duree(c.fin - t)}", style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
                } else {
                    Text(heureLocale(c.debut, t), style = MaterialTheme.typography.titleSmall)
                    Text("dans ${duree(c.debut - t)}", style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
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
