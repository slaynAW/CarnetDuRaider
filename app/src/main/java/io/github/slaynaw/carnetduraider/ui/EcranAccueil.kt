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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.time.LocalTime

/**
 * Accueil : ce qui compte maintenant, en un coup d'œil. Garder ou vendre un objet, où aller au prochain
 * raid, les conditions de la carte en cours, et où en sont les objectifs. Chaque bloc mène à son onglet.
 */
@Composable
fun EcranAccueil(
    vm: CarnetViewModel,
    aller: (Onglet) -> Unit,
    nouvelleVersion: String?,
    /** Calendrier officiel des conditions de la carte, ou `null` s'il n'a pas encore été lu. */
    calendrier: Calendrier?,
    afficherMiseAJour: () -> Unit,
) {
    val donnees = vm.catalogue.donnees
    val heure = LocalTime.now().hour
    val salutation = if (heure >= 18 || heure < 5) "Bonsoir, Raider" else "Bonjour, Raider"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(salutation, style = MaterialTheme.typography.headlineMedium)

        if (nouvelleVersion != null) {
            Surface(onClick = afficherMiseAJour, color = Signal.Releve, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Signal.Or)
                    Column {
                        Text("Mise à jour disponible", style = MaterialTheme.typography.titleSmall)
                        Text("Version $nouvelleVersion\u00a0: touche pour voir les nouveautés", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                    }
                }
            }
        }

        // La question la plus fréquente en jeu.
        Surface(
            onClick = { aller(Onglet.OBJETS) },
            color = Signal.Surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Or)
                Column {
                    Text("Garder, recycler ou vendre\u00a0?", style = MaterialTheme.typography.titleSmall)
                    Text("Cherche un objet…", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                }
            }
        }

        ProchainRaid(vm, aller)

        if (calendrier != null) BlocConditions(vm, calendrier, onClick = { aller(Onglet.CARTES) })

        MesObjectifs(vm, onClick = { aller(Onglet.OBJECTIFS) })

        Text(
            "Données du jeu ${donnees.versionJeu}, noms français officiels uniquement.",
            style = MaterialTheme.typography.bodySmall,
            color = Signal.TexteDiscret,
        )
    }
}

/** Où aller au prochain raid pour trouver ce qui manque (ou, s'il ne manque rien, choisir ses objectifs). */
@Composable
private fun ProchainRaid(vm: CarnetViewModel, aller: (Onglet) -> Unit) {
    val restants = vm.aTrouver().filter { !it.trouve }
    val conseil = remember(restants.map { it.objetId }) { vm.conseilRaid() }
    val premiere = conseil.cartes.firstOrNull()
    Surface(
        onClick = { aller(if (restants.isEmpty()) Onglet.OBJECTIFS else Onglet.RAID) },
        color = if (premiere != null) Signal.Rouge else Signal.Surface,
        contentColor = if (premiere != null) Signal.SurCouleur else Signal.Texte,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TON PROCHAIN RAID", style = MaterialTheme.typography.labelSmall)
            when {
                premiere != null -> {
                    Text("Va sur ${vm.catalogue.carte(premiere.carteId)?.nom ?: Catalogue.NOM_A_VERIFIER}", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        buildString {
                            append("${premiere.objets.size} de tes ${pluriel(restants.size, "objet", "objets")} à trouver")
                            premiere.zones.firstOrNull()?.let { append(" · surtout ${it.zone}") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (conseil.parcours.size > 1) {
                        Text(
                            "Puis " + conseil.parcours.drop(1).joinToString(", ") { vm.catalogue.carte(it.carteId)?.nom ?: Catalogue.NOM_A_VERIFIER } + " pour tout trouver.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                restants.isNotEmpty() -> {
                    Text("Rien dans le butin des cartes", style = MaterialTheme.typography.titleMedium)
                    Text("Ce qu'il te faut s'obtient autrement\u00a0: regarde l'onglet Raid.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                }
                else -> {
                    Text("Rien à chercher pour l'instant", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Indique ce que tu as en cours (quêtes, ateliers, projets, expéditions)\u00a0: l'appli te dira où aller.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Signal.TexteDoux,
                    )
                }
            }
        }
    }
}

/** Où en sont les objectifs en cours. */
@Composable
private fun MesObjectifs(vm: CarnetViewModel, onClick: () -> Unit) {
    val liste = vm.listeDeCourses()
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Objectifs", style = MaterialTheme.typography.titleSmall, color = Signal.Vert)
            if (vm.objectifs.isEmpty()) {
                Text("Rien en cours. Touche ici pour indiquer tes quêtes, ateliers, projets ou expéditions en cours.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
            } else {
                Text(
                    "${pluriel(vm.objectifs.size, "objectif en cours", "objectifs en cours")} · ${pluriel(liste.objetsATrouver, "objet", "objets")} à trouver",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Barre(liste.progression.toFloat(), Signal.Vert)
            }
        }
    }
}

/** Conditions de la carte en cours et à venir, avec leur minuteur à la seconde. */
@Composable
private fun BlocConditions(vm: CarnetViewModel, calendrier: Calendrier, onClick: () -> Unit) {
    val t = maintenant(1_000)
    val enCours = calendrier.enCours(vm.region, t)
    val suivantes = calendrier.aVenir(vm.region, t, 6 * Calendrier.UNE_HEURE).take(3 - enCours.size.coerceAtMost(2))
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Conditions de la carte", style = MaterialTheme.typography.titleSmall, color = Signal.Cyan)
            if (enCours.isEmpty() && suivantes.isEmpty()) {
                Text("Rien d'annoncé dans les prochaines heures.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            enCours.forEach { LigneMinuteur(it, "encore ${chrono(it.fin - t)}", Signal.Cyan) }
            suivantes.forEach { LigneMinuteur(it, "dans ${chrono(it.debut - t)}", Signal.Or) }
        }
    }
}

@Composable
private fun LigneMinuteur(c: Creneau, minuteur: String, couleur: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.condition, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (c.majeure) Signal.Rouge else Signal.Texte)
            Text(c.carte, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
        }
        Text(minuteur, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = couleur)
    }
}

/** « 1 objet », « 3 objets » (le nombre est inclus). */
fun pluriel(n: Int, singulier: String, pluriel: String): String = "$n ${if (n > 1) pluriel else singulier}"
