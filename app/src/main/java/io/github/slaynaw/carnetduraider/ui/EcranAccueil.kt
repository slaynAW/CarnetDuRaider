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
import androidx.compose.material.icons.filled.Search
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
fun EcranAccueil(vm: CarnetViewModel, aller: (Onglet) -> Unit) {
    val donnees = vm.catalogue.donnees
    val liste = vm.listeDeCourses()
    val heure = LocalTime.now().hour
    val salutation = if (heure >= 18 || heure < 5) "Bonsoir, Raider" else "Bonjour, Raider"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(salutation, style = MaterialTheme.typography.headlineMedium)
                Text("Carnet du Raider", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
            }
            Bandes()
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
                legende = pluriel(donnees.conditions.size, "condition", "conditions"),
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
