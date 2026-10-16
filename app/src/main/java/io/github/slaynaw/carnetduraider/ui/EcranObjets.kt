package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.Sortie
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.ui.theme.Signal

@Composable
fun EcranObjets(vm: CarnetViewModel, recherche: String, onRecherche: (String) -> Unit, ouvrir: (String) -> Unit) {
    val resultats = remember(recherche) { vm.catalogue.rechercher(recherche, limite = 50) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Bandeau("Objets", Signal.Or)
                Text("Garder, recycler ou vendre\u00a0?", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = recherche,
                    onValueChange = onRecherche,
                    placeholder = { Text("Cherche un objet…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Or) },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Signal.Or,
                        unfocusedBorderColor = Signal.Trait,
                        focusedContainerColor = Signal.Surface,
                        unfocusedContainerColor = Signal.Surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    recherche.isBlank() -> Text(
                        "Tape quelques lettres du nom\u00a0: la fiche te dit s'il faut garder, recycler ou vendre l'objet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Signal.TexteDoux,
                    )
                    resultats.isEmpty() -> Text("Aucun objet ne correspond.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                }
            }
        }
        items(resultats, key = { it.id }) { objet ->
            LigneObjet(objet, vm.possede(objet.id), onClick = { ouvrir(objet.id) })
        }
    }
}

@Composable
private fun LigneObjet(objet: Objet, possede: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconeObjet(objet.id, objet.rarete, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(objet.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val details = listOfNotNull(objet.rarete?.libelle, objet.categorie).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            if (possede > 0) Text("× $possede", style = MaterialTheme.typography.titleSmall, color = Signal.Or)
        }
    }
}

/** Fiche d'un objet : réserve, verdict, valeur, recyclage et lieux. */
@Composable
fun EcranFiche(vm: CarnetViewModel, objetId: String, onRetour: () -> Unit) {
    val objet = vm.catalogue.objet(objetId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onRetour) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Bandeau("Objet", Signal.Or)
        }
        if (objet == null) {
            Text("Objet introuvable dans les données du jeu.", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconeObjet(objet.id, objet.rarete, 104.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(objet.nom, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PuceRarete(objet.rarete)
                }
                objet.categorie?.let { Puce(it, couleur = Signal.TexteDoux) }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Dans ta réserve", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Compteur(vm.possede(objet.id), objet.nom, onChange = { vm.changerQuantite(objet.id, it) })
        }

        CarteVerdict(vm.verdict(objet.id), objet, vm)

        Section("Valeur") {
            Text("${objet.valeur} pièces l'unité", style = MaterialTheme.typography.bodyLarge)
        }
        Section("Recyclage en") {
            if (objet.recyclage.isEmpty()) {
                Text("Ne se recycle pas.", style = MaterialTheme.typography.bodyLarge, color = Signal.TexteDoux)
            } else {
                objet.recyclage.forEach { LigneQuantite(vm, it.id, it.quantite) }
            }
        }
        if (objet.lieux.isNotEmpty()) {
            Section("Où le trouver") {
                Text(objet.lieux.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titre, style = MaterialTheme.typography.titleMedium)
        contenu()
    }
}

@Composable
private fun CarteVerdict(verdict: Verdict, objet: Objet, vm: CarnetViewModel) {
    val valeurRecyclage = vm.catalogue.valeurRecyclage(objet)
    fun raisonSortie(sortie: Sortie): String = when {
        sortie == Sortie.RECYCLER -> "Ses matériaux valent $valeurRecyclage pièces, plus que l'objet (${objet.valeur})."
        objet.recyclage.isEmpty() -> "Il vaut ${objet.valeur} pièces et ne se recycle pas."
        else -> "Il vaut ${objet.valeur} pièces, plus que ses matériaux ($valeurRecyclage)."
    }
    val (titre, texte, pour) = when (verdict) {
        is Verdict.Garder -> Triple("Garde-le", "Il t'en manque ${verdict.manquant} pour tes objectifs.", verdict.pour)
        is Verdict.Suffisant -> Triple(
            "Tu en as assez",
            if (verdict.surplus == 0) {
                "Objectifs couverts, pile ce qu'il faut."
            } else {
                val action = if (verdict.conseil == Sortie.VENDRE) "vends" else "recycle"
                "Objectifs couverts, avec ${verdict.surplus} en trop\u00a0: $action le surplus. ${raisonSortie(verdict.conseil)}"
            },
            verdict.pour,
        )
        is Verdict.Inutile -> Triple(
            if (verdict.conseil == Sortie.VENDRE) "Vends-le" else "Recycle-le",
            "Aucun objectif suivi n'en demande. ${raisonSortie(verdict.conseil)}",
            emptyList(),
        )
    }
    Surface(color = Signal.Or, contentColor = Signal.SurCouleur, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("VERDICT", style = MaterialTheme.typography.labelSmall)
            Text(titre, style = MaterialTheme.typography.headlineLarge)
            Text(texte, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (pour.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Signal.SurCouleur.copy(alpha = 0.3f))
                pour.forEach { besoin ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(besoin.libelle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("× ${besoin.quantite}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (vm.objectifs.isEmpty()) {
                Text(
                    "Suis tes objectifs dans l'onglet Progression pour un verdict sur mesure.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** « 3 × Pièces métalliques » avec l'icône de l'objet. */
@Composable
fun LigneQuantite(vm: CarnetViewModel, objetId: String, quantite: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 32.dp)
        Text("$quantite × ${vm.catalogue.nomObjet(objetId)}", style = MaterialTheme.typography.bodyLarge)
    }
}
