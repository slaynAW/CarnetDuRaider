package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableIntStateOf
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
 * deux armes, soins, grenades et six « autres »). Une puce par build, en haut (deux lignes de trois) ; ses
 * cases en grille, dessous. Les objets choisis et tout ce qu'il faut pour les fabriquer sont à garder :
 * l'onglet Objets ne les propose plus à la vente.
 */
@Composable
fun EcranBuilds(vm: CarnetViewModel, choisir: (rang: Int, case: CaseBuild) -> Unit) {
    var rang by rememberSaveable { mutableIntStateOf(0) }
    val build = vm.builds.getOrNull(rang) ?: return
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BandeauSection(Onglet.BUILDS, "Mes ${vm.builds.size} builds")
                Text(
                    "Leurs objets, et tout ce qu'il faut pour les fabriquer, ne sont plus proposés à la vente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                // Les six builds en deux lignes de trois.
                vm.builds.withIndex().chunked(3).forEach { ligne ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ligne.forEach { (i, b) ->
                            PuceFiltre(
                                vm.outilsBuilds.nom(b, i),
                                choisie = i == rang,
                                couleur = Signal.Violet,
                                onClick = { rang = i },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - ligne.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item(key = "build-$rang") { CarteBuild(vm, rang, build, choisir = { choisir(rang, it) }) }
    }
}

@Composable
private fun CarteBuild(vm: CarnetViewModel, rang: Int, build: Build, choisir: (CaseBuild) -> Unit) {
    val materiaux = remember(build) {
        vm.outilsBuilds.aGarder(listOf(build)).filterValues { usages -> usages.none { it.direct } }.keys.toList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = build.nom,
            onValueChange = { vm.changerBuild(rang, build.copy(nom = it.take(LONGUEUR_NOM))) },
            label = { Text("Nom du build") },
            placeholder = { Text(vm.outilsBuilds.nom(Build(), rang)) },
            singleLine = true,
            shape = RoundedCornerShape(6.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal.Violet, focusedLabelColor = Signal.Violet, cursorColor = Signal.Violet),
            modifier = Modifier.fillMaxWidth(),
        )
        // Les cases, deux par ligne : l'équipement, puis les six « Autres ».
        val (autres, equipement) = CaseBuild.entries.partition { it.autre }
        GrilleCases(vm, build, equipement, choisir)
        Etiquette("Autres", Signal.Violet, Modifier.padding(top = 6.dp))
        GrilleCases(vm, build, autres, choisir)
        if (materiaux.isNotEmpty()) {
            Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Etiquette("Pour le fabriquer", Signal.Violet)
                    Text(
                        materiaux.take(MATERIAUX_AFFICHES).joinToString(" · ") { vm.catalogue.nomObjet(it) } +
                            if (materiaux.size > MATERIAUX_AFFICHES) " et ${pluriel(materiaux.size - MATERIAUX_AFFICHES, "autre", "autres")}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("À garder\u00a0: ils ne sont plus proposés à la vente.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                }
            }
        }
    }
}

/** Des cases du build, deux par ligne. */
@Composable
private fun GrilleCases(vm: CarnetViewModel, build: Build, cases: List<CaseBuild>, choisir: (CaseBuild) -> Unit) {
    cases.chunked(2).forEach { ligne ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ligne.forEach { case ->
                CaseDuBuild(vm, case, build.objets[case], onClick = { choisir(case) }, modifier = Modifier.weight(1f))
            }
            if (ligne.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Une case du build : son nom et l'objet choisi (ou « Choisir »). La toucher ouvre le choix de l'objet, où
 * l'on peut aussi voir sa fiche ou vider la case.
 */
@Composable
private fun CaseDuBuild(vm: CarnetViewModel, case: CaseBuild, objetId: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (objetId == null) {
                Box(Modifier.size(44.dp).border(1.5.dp, Signal.Trait, RoundedCornerShape(4.dp)))
            } else {
                IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 44.dp)
            }
            Column(Modifier.weight(1f)) {
                Etiquette(case.libelle, Signal.Violet)
                Text(
                    objetId?.let { vm.catalogue.nomObjet(it) } ?: "Choisir",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (objetId == null) Signal.TexteDiscret else Signal.Texte,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Choix de l'objet d'une case : seulement les objets de son emplacement, avec une recherche. */
@Composable
fun EcranChoixObjetBuild(vm: CarnetViewModel, rang: Int, case: CaseBuild, onRetour: () -> Unit, ouvrir: (String) -> Unit) {
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
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal.Violet, cursorColor = Signal.Violet),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (actuel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = { ouvrir(actuel) }, color = Signal.Releve, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = Signal.Violet)
                                Text("Voir la fiche", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Surface(onClick = { choisir(null) }, color = Signal.Releve, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = Signal.TexteDoux)
                                Text("Vider la case", style = MaterialTheme.typography.labelLarge, color = Signal.TexteDoux)
                            }
                        }
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
                shape = RoundedCornerShape(6.dp),
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
