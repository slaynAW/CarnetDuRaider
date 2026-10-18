package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.BuildConfig
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.miseajour.MisesAJour
import io.github.slaynaw.carnetduraider.miseajour.EtatMiseAJour
import io.github.slaynaw.carnetduraider.miseajour.GestionnaireMiseAJour
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlinx.coroutines.launch

@Composable
fun EcranReglages(vm: CarnetViewModel, miseAJour: GestionnaireMiseAJour, etat: EtatMiseAJour, afficherMiseAJour: () -> Unit, onRetour: () -> Unit) {
    val portee = rememberCoroutineScope()
    val liens = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Bandeau("Réglages", Signal.Texte)
        }
        Text("Réglages", style = MaterialTheme.typography.headlineMedium)

        Text("Mises à jour", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        val nouvelle = etat.nouvelle
        Reglage(
            titre = "Rechercher une mise à jour",
            detail = when {
                !etat.disponible -> "Indisponible pour cette installation\u00a0: télécharge les versions depuis GitHub."
                etat.recherche -> "Recherche en cours…"
                nouvelle != null -> "Version ${nouvelle.version} disponible\u00a0: touche pour la voir."
                etat.erreur != null -> etat.erreur
                etat.derniereRecherche > 0 -> "Tu as la dernière version (recherche ${depuis(etat.derniereRecherche)})."
                else -> "Jamais recherchée."
            },
            onClick = {
                when {
                    !etat.disponible -> runCatching { liens.openUri(MisesAJour.ADRESSE) }
                    nouvelle != null -> afficherMiseAJour()
                    else -> portee.launch { if (miseAJour.rechercher().nouvelle != null) afficherMiseAJour() }
                }
            },
        )
        if (etat.disponible) {
            Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Rechercher automatiquement", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "À l'ouverture de l'appli, au plus toutes les 12\u00a0h.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Signal.TexteDoux,
                        )
                    }
                    Switch(
                        checked = etat.automatique,
                        onCheckedChange = miseAJour::changerAutomatique,
                        colors = SwitchDefaults.colors(checkedThumbColor = Signal.SurCouleur, checkedTrackColor = Signal.Or),
                    )
                }
            }
        }
        Text(
            "Les mises à jour viennent uniquement des versions officielles publiées sur GitHub. Chacune est " +
                "vérifiée avant l'installation (signature, taille et empreinte du fichier), puis Android contrôle " +
                "qu'elle est signée avec la même clé que l'appli installée. L'appli ne se connecte qu'à GitHub (mises à jour) " +
                "et au site d'Embark (calendrier des conditions de la carte)\u00a0: aucune donnée personnelle n'est envoyée.",
            style = MaterialTheme.typography.bodySmall,
            color = Signal.TexteDiscret,
        )

        SectionAlertes(vm)

        SectionSauvegarde(vm)

        Text("À propos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Reglage(
            titre = "Carnet du Raider ${BuildConfig.VERSION_NAME}",
            detail = "Voir les versions et les nouveautés sur GitHub",
            onClick = { runCatching { liens.openUri(MisesAJour.ADRESSE) } },
        )
        Text(
            "Projet de fan, non officiel. ARC Raiders est une marque d'Embark Studios AB\u00a0; cette appli n'est ni " +
                "affiliée à Embark Studios ni approuvée par Embark Studios.",
            style = MaterialTheme.typography.bodySmall,
            color = Signal.TexteDiscret,
        )
    }
}

@Composable
private fun Reglage(titre: String, detail: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titre, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
        }
    }
}

/** « à l'instant », « il y a 12 min », « il y a 3 h », « il y a 2 j ». */
internal fun depuis(instant: Long): String {
    val minutes = (System.currentTimeMillis() - instant).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes\u00a0min"
        minutes < 48 * 60 -> "il y a ${minutes / 60}\u00a0h"
        else -> "il y a ${minutes / (24 * 60)}\u00a0j"
    }
}
