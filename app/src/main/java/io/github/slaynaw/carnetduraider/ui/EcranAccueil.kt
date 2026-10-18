package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.time.LocalTime

@Composable
fun EcranAccueil(
    vm: CarnetViewModel,
    aller: (Onglet) -> Unit,
    nouvelleVersion: String?,
    /** Calendrier officiel des conditions de la carte, ou `null` s'il n'a pas encore été lu. */
    calendrier: Calendrier?,
    retourDeRaid: () -> Unit,
    preparerRaid: () -> Unit,
    afficherMiseAJour: () -> Unit,
) {
    val donnees = vm.catalogue.donnees
    val liste = vm.listeDeCourses()
    val heure = LocalTime.now().hour
    val salutation = if (heure >= 18 || heure < 5) "Bonsoir, Raider" else "Bonjour, Raider"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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

        // Accès direct à la question la plus fréquente du jeu.
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

        if (calendrier != null) BlocConditions(vm, calendrier, onClick = { aller(Onglet.CARTES) })

        // Avant un raid : où aller pour trouver ce qu'on cherche.
        Button(
            onClick = preparerRaid,
            colors = ButtonDefaults.buttonColors(containerColor = Signal.Cyan, contentColor = Signal.SurCouleur),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                if (vm.listeRaid.isEmpty()) "Préparer mon raid" else "Préparer mon raid (${pluriel(vm.listeRaid.size, "objet", "objets")})",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        // Après chaque raid : saisie rapide de ce qu'on rapporte.
        Button(
            onClick = retourDeRaid,
            colors = ButtonDefaults.buttonColors(containerColor = Signal.Or, contentColor = Signal.SurCouleur),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Retour de raid\u00a0: ajouter mon butin", style = MaterialTheme.typography.titleSmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tuile(
                titre = "À trouver",
                chiffre = liste.objetsManquants.toString(),
                legende = if (vm.objectifs.isEmpty()) "aucun objectif suivi" else pluriel(liste.lignes.count { !it.complet }, "objet différent", "objets différents"),
                couleur = Signal.Or,
                onClick = { aller(Onglet.PROGRESSION) },
                modifier = Modifier.weight(1f),
            )
            Tuile(
                titre = "Objectifs",
                chiffre = vm.objectifs.size.toString(),
                legende = "suivis",
                couleur = Signal.Vert,
                onClick = { aller(Onglet.PROGRESSION) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tuile(
                titre = "Cartes",
                chiffre = donnees.cartes.size.toString(),
                legende = "plans et conditions",
                couleur = Signal.Cyan,
                onClick = { aller(Onglet.CARTES) },
                modifier = Modifier.weight(1f),
            )
            Tuile(
                titre = "Objets",
                chiffre = donnees.objets.size.toString(),
                legende = "dans le catalogue",
                couleur = Signal.Releve,
                contenu = Signal.Texte,
                onClick = { aller(Onglet.OBJETS) },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            "Données du jeu ${donnees.versionJeu}, noms français officiels uniquement.",
            style = MaterialTheme.typography.bodySmall,
            color = Signal.TexteDiscret,
        )
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
