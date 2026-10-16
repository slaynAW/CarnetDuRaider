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
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.time.LocalTime

@Composable
fun EcranAccueil(
    vm: CarnetViewModel,
    aller: (Onglet) -> Unit,
    nouvelleVersion: String?,
    /** Conditions de la carte en cours (calendrier officiel), ou `null` s'il n'a pas encore été lu. */
    conditionsEnCours: Int?,
    retourDeRaid: () -> Unit,
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
                titre = "Conditions",
                chiffre = conditionsEnCours?.toString() ?: donnees.cartes.size.toString(),
                legende = if (conditionsEnCours != null) "en cours" else pluriel(donnees.cartes.size, "carte", "cartes"),
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

/** « 1 objet », « 3 objets » (le nombre est inclus). */
fun pluriel(n: Int, singulier: String, pluriel: String): String = "$n ${if (n > 1) pluriel else singulier}"
