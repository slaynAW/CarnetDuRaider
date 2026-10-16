package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.LigneDeCourses
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlin.math.roundToInt

@Composable
fun EcranProgression(vm: CarnetViewModel, ajouterObjectif: () -> Unit, ouvrir: (String) -> Unit) {
    val liste = vm.listeDeCourses()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bandeau("Progression", Signal.Vert)
                Text("Liste de courses", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (vm.objectifs.isEmpty()) {
                        "Choisis ce que tu veux construire ou terminer\u00a0: l'appli calcule les objets à trouver."
                    } else {
                        "${pluriel(liste.objetsManquants, "objet", "objets")} à trouver · ${pluriel(vm.objectifs.size, "objectif", "objectifs")}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
            }
        }

        if (liste.lignes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${(liste.progression * 100).roundToInt()}\u00a0%", style = MaterialTheme.typography.headlineMedium, color = Signal.Or)
                        Text("des objets rassemblés", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Barre(liste.progression.toFloat(), Signal.Or, epaisseur = 8.dp)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Objectifs suivis", style = MaterialTheme.typography.titleMedium)
                vm.objectifs.forEach { objectif -> LigneObjectif(vm, objectif) }
                Button(
                    onClick = ajouterObjectif,
                    colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Ajouter un objectif", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        if (liste.lignes.isNotEmpty()) {
            item { Text("Objets à trouver", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        }
        items(liste.lignes, key = { it.objetId }) { ligne ->
            LigneCourses(vm, ligne, onClick = { ouvrir(ligne.objetId) })
        }
    }
}

@Composable
private fun LigneObjectif(vm: CarnetViewModel, objectif: Objectif) {
    val libelle = vm.planificateur.libelle(objectif)
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(libelle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.retirerObjectif(objectif) }) {
                Icon(Icons.Filled.Close, contentDescription = "Ne plus suivre\u00a0: $libelle", tint = Signal.TexteDoux)
            }
        }
    }
}

@Composable
private fun LigneCourses(vm: CarnetViewModel, ligne: LigneDeCourses, onClick: () -> Unit) {
    val rarete = vm.catalogue.objet(ligne.objetId)?.rarete
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Signal.rarete(rarete)))
                    Text(ligne.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${ligne.possede}/${ligne.besoin}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (ligne.complet) Signal.Vert else Signal.Texte,
                    )
                }
                Barre(minOf(ligne.possede, ligne.besoin).toFloat() / ligne.besoin, if (ligne.complet) Signal.Vert else Signal.Or)
                Text(
                    ligne.pour.joinToString(" · ") { "${it.libelle} (${it.quantite})" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
            Compteur(ligne.possede, ligne.nom, onChange = { vm.changerQuantite(ligne.objetId, it) }, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Choix d'un niveau d'établi ou d'une quête à suivre. */
@Composable
fun EcranAjoutObjectif(vm: CarnetViewModel, onRetour: () -> Unit) {
    val donnees = vm.catalogue.donnees
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Bandeau("Progression", Signal.Vert)
                }
                Text("Ajouter un objectif", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Ses besoins en objets s'ajoutent à ta liste de courses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                Text("Atelier", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
            }
        }
        donnees.etablis.forEach { etabli ->
            items(etabli.niveaux, key = { "etabli-${etabli.id}-${it.niveau}" }) { niveau ->
                val objectif = Objectif.NiveauDEtabli(etabli.id, niveau.niveau)
                ChoixObjectif(
                    titre = vm.planificateur.libelle(objectif),
                    detail = niveau.cout.joinToString(" · ") { "${it.quantite} × ${vm.catalogue.nomObjet(it.id)}" },
                    suivi = vm.suit(objectif),
                    onClick = { vm.ajouterObjectif(objectif); onRetour() },
                )
            }
        }
        item { Text("Quêtes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp)) }
        items(donnees.quetes.sortedBy { it.nom }, key = { "quete-${it.id}" }) { quete ->
            val objectif = Objectif.QueteATerminer(quete.id)
            val objets = quete.objets.joinToString(" · ") { "${it.quantite} × ${vm.catalogue.nomObjet(it.id)}" }
            ChoixObjectif(
                titre = quete.nom,
                detail = listOfNotNull(quete.commercant, objets).joinToString(" — "),
                suivi = vm.suit(objectif),
                onClick = { vm.ajouterObjectif(objectif); onRetour() },
            )
        }
    }
}

@Composable
private fun ChoixObjectif(titre: String, detail: String, suivi: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !suivi, color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            if (suivi) Text("Déjà suivi", style = MaterialTheme.typography.labelMedium, color = Signal.Vert)
        }
    }
}
