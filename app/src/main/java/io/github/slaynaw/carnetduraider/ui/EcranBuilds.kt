package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Build
import io.github.slaynaw.carnetduraider.core.CaseBuild
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Longueur maximale du nom d'un build. */
private const val LONGUEUR_NOM = 30

/** Au plus autant de matériaux nommés sous un build ; les autres sont comptés. */
private const val MATERIAUX_AFFICHES = 6

/**
 * Section « Mes builds » : jusqu'à six builds, chacun avec un nom et un objet par case (module, bouclier,
 * deux armes, soins, grenades, divers). Les objets choisis et tout ce qu'il faut pour les fabriquer sont à
 * garder : l'onglet Objets ne les propose plus à la vente.
 */
@Composable
fun EcranBuilds(vm: CarnetViewModel, choisir: (rang: Int, case: CaseBuild) -> Unit, ouvrir: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bandeau("Builds", Signal.Violet)
                Text("Mes builds", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Prépare jusqu'à six builds. Leurs objets, et tout ce qu'il faut pour les fabriquer, ne sont plus proposés à la vente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
            }
        }
        items(vm.builds.indices.toList(), key = { "build-$it" }) { rang ->
            CarteBuild(vm, rang, vm.builds[rang], choisir = { choisir(rang, it) }, ouvrir = ouvrir)
        }
    }
}

@Composable
private fun CarteBuild(vm: CarnetViewModel, rang: Int, build: Build, choisir: (CaseBuild) -> Unit, ouvrir: (String) -> Unit) {
    val materiaux = remember(build) {
        vm.outilsBuilds.aGarder(listOf(build)).filterValues { usages -> usages.none { it.direct } }.keys.toList()
    }
    Surface(color = Signal.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = build.nom,
                onValueChange = { vm.changerBuild(rang, build.copy(nom = it.take(LONGUEUR_NOM))) },
                label = { Text("Nom du build") },
                placeholder = { Text(vm.outilsBuilds.nom(Build(), rang)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal.Violet, focusedLabelColor = Signal.Violet, cursorColor = Signal.Violet),
                modifier = Modifier.fillMaxWidth(),
            )
            CaseBuild.entries.forEach { case ->
                LigneCase(vm, case, build.objets[case], onClick = { choisir(case) }, ouvrir = ouvrir, vider = {
                    vm.changerBuild(rang, build.copy(objets = build.objets - case))
                })
            }
            if (materiaux.isNotEmpty()) {
                Text(
                    "À garder pour fabriquer ce build\u00a0: " + materiaux.take(MATERIAUX_AFFICHES).joinToString(", ") { vm.catalogue.nomObjet(it) } +
                        if (materiaux.size > MATERIAUX_AFFICHES) " et ${pluriel(materiaux.size - MATERIAUX_AFFICHES, "autre", "autres")}." else ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.Violet,
                )
            }
        }
    }
}

/**
 * Une case du build : son nom, l'objet choisi (ou « Choisir »), sa fiche (où le trouver, comment le fabriquer)
 * et de quoi la vider. Toucher la case change l'objet.
 */
@Composable
private fun LigneCase(vm: CarnetViewModel, case: CaseBuild, objetId: String?, onClick: () -> Unit, ouvrir: (String) -> Unit, vider: () -> Unit) {
    Surface(onClick = onClick, color = Signal.Releve, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(case.libelle, style = MaterialTheme.typography.labelLarge, color = Signal.Violet, modifier = Modifier.width(76.dp))
            if (objetId == null) {
                Text("Choisir…", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDiscret, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            } else {
                IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 36.dp)
                Text(
                    vm.catalogue.nomObjet(objetId),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { ouvrir(objetId) }) {
                    Icon(Icons.Filled.Info, contentDescription = "Fiche\u00a0: ${vm.catalogue.nomObjet(objetId)}", tint = Signal.Violet)
                }
                IconButton(onClick = vider) {
                    Icon(Icons.Filled.Close, contentDescription = "Vider la case ${case.libelle}", tint = Signal.TexteDoux)
                }
            }
        }
    }
}

/** Choix de l'objet d'une case : seulement les objets de son emplacement, avec une recherche. */
@Composable
fun EcranChoixObjetBuild(vm: CarnetViewModel, rang: Int, case: CaseBuild, onRetour: () -> Unit) {
    val build = vm.builds.getOrNull(rang) ?: return
    var recherche by rememberSaveable { mutableStateOf("") }
    val tous = remember(case) { vm.outilsBuilds.choix(case) }
    val cle = Catalogue.normaliser(recherche)
    val choix = if (cle.isBlank()) tous else tous.filter { Catalogue.normaliser(it.nom).contains(cle) }
    val actuel = build.objets[case]

    fun choisir(id: String?) {
        vm.changerBuild(rang, build.copy(objets = if (id == null) build.objets - case else build.objets + (case to id)))
        onRetour()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Bandeau("Builds", Signal.Violet)
                }
                Text("${vm.outilsBuilds.nom(build, rang)} · ${case.libelle}", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    placeholder = { Text("Chercher…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Violet) },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal.Violet, cursorColor = Signal.Violet),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (actuel != null) {
                    Surface(onClick = { choisir(null) }, color = Signal.Releve, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Vider la case", style = MaterialTheme.typography.labelLarge, color = Signal.TexteDoux, modifier = Modifier.padding(14.dp))
                    }
                }
                if (choix.isEmpty()) {
                    Text("Aucun objet ne correspond.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                }
            }
        }
        items(choix, key = { it.id }) { objet ->
            val choisi = objet.id == actuel
            Surface(
                onClick = { choisir(objet.id) },
                color = if (choisi) Signal.Releve else Signal.Surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconeObjet(objet.id, objet.rarete, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(objet.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val details = listOfNotNull(objet.rarete?.libelle, objet.categorie).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                    }
                    if (choisi) Icon(Icons.Filled.Check, contentDescription = "Choisi", tint = Signal.Violet, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
