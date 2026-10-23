package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Build
import io.github.slaynaw.carnetduraider.core.Builds
import io.github.slaynaw.carnetduraider.core.CaseBuild
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Longueur maximale du nom d'un build. */
private const val LONGUEUR_NOM = 30

/** Au plus autant de matériaux nommés sous un build ; les autres sont comptés. */
private const val MATERIAUX_AFFICHES = 6

/**
 * Section « Mes builds » : jusqu'à six builds, chacun avec un nom et un objet par case (module, bouclier,
 * deux armes, soins, grenades et six « autres »). Les builds en puces sur une ligne, le préféré en premier
 * (étoile) et ouvert en arrivant ; ses cases groupées dessous (équipement, armes, consommables, « Autres »
 * repliées), puis ce qu'il faut pour le fabriquer. Les objets choisis et tout ce qu'il faut pour les
 * fabriquer sont à garder : l'onglet Objets ne les propose plus à la vente.
 */
@Composable
fun EcranBuilds(vm: CarnetViewModel, choisir: (rang: Int, case: CaseBuild) -> Unit) {
    var rang by rememberSaveable { mutableIntStateOf(Builds.ouvert(vm.builds)) }
    val build = vm.builds.getOrNull(rang) ?: return
    val remplis = vm.builds.count { !it.vide }
    EcranDeSection(Onglet.BUILDS, "${pluriel(remplis, "build", "builds")} sur ${vm.builds.size} ${if (remplis > 1) "remplis" else "rempli"}") { defilement, haut ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = defilement,
            contentPadding = PaddingValues(top = haut + 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Builds.ordre(vm.builds), key = { it }) { i ->
                        PuceBuild(vm, i, choisie = i == rang, onClick = { rang = i })
                    }
                }
            }
            item(key = "build-$rang") {
                Box(Modifier.padding(horizontal = 20.dp)) { CarteBuild(vm, rang, build, choisir = { choisir(rang, it) }) }
            }
        }
    }
}

/** Puce d'un build : étoile s'il est le préféré, son nom et ses cases remplies sur douze. */
@Composable
private fun PuceBuild(vm: CarnetViewModel, rang: Int, choisie: Boolean, onClick: () -> Unit) {
    val b = vm.builds[rang]
    val nom = vm.outilsBuilds.nom(b, rang)
    Surface(
        selected = choisie,
        onClick = onClick,
        color = if (choisie) Signal.Violet else Signal.Surface,
        contentColor = if (choisie) Signal.SurCouleur else Signal.Texte,
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 40.dp).semantics { if (b.prefere) stateDescription = "Build préféré" },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (b.prefere) Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(nom, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            if (!b.vide) Text("${b.objets.size}/${CaseBuild.entries.size}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteBuild(vm: CarnetViewModel, rang: Int, build: Build, choisir: (CaseBuild) -> Unit) {
    val materiaux = remember(build) { vm.outilsBuilds.aFabriquer(build) }
    var autresOuvertes by rememberSaveable(rang) { mutableStateOf(false) }
    var tousMateriaux by rememberSaveable(rang) { mutableStateOf(false) }
    var renommer by remember { mutableStateOf(false) }
    val nom = vm.outilsBuilds.nom(build, rang)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (build.prefere) Etiquette("Build préféré", Signal.Violet)
                Text(nom, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.preferer(rang, !build.prefere) }) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (build.prefere) "Ne plus en faire mon build préféré" else "En faire mon build préféré",
                    tint = if (build.prefere) Signal.Violet else Signal.TexteDiscret,
                )
            }
            IconButton(onClick = { renommer = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Renommer le build", tint = Signal.Violet)
            }
        }
        GROUPES.forEach { (titre, cases) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Etiquette(titre, Signal.Violet)
                GrilleCases(vm, build, cases, choisir)
            }
        }
        val autres = CaseBuild.entries.filter { it.autre }
        EnTeteAccordeon(
            "Autres",
            "${autres.count { build.objets[it] != null }} sur ${autres.size}",
            autresOuvertes,
            Signal.Violet,
            onClick = { autresOuvertes = !autresOuvertes },
        )
        if (autresOuvertes) GrilleCases(vm, build, autres, choisir)
        if (materiaux.isNotEmpty()) {
            Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Signal.Violet))
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Pour le fabriquer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(pluriel(materiaux.size, "matériau", "matériaux"), style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            (if (tousMateriaux) materiaux else materiaux.take(MATERIAUX_AFFICHES)).forEach { (id, n) ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(50)).background(Signal.Releve).padding(start = 3.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    IconeObjet(id, vm.catalogue.objet(id)?.rarete, 24.dp)
                                    Text("${vm.catalogue.nomObjet(id)} ×$n", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        if (materiaux.size > MATERIAUX_AFFICHES) {
                            TextButton(onClick = { tousMateriaux = !tousMateriaux }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (tousMateriaux) "Voir moins" else "Voir les ${materiaux.size} matériaux", color = Signal.Violet)
                            }
                        }
                        Text("Ces objets passent en «\u00a0Garder\u00a0» dans la liste des objets.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
                    }
                }
            }
        } else if (build.vide) {
            Text(
                "Touche une case pour choisir un objet. Ce qu'il faut pour le fabriquer s'affiche ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = Signal.TexteDoux,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Signal.Trait, RoundedCornerShape(6.dp))
                    .padding(14.dp),
            )
        }
    }
    if (renommer) {
        var texte by remember { mutableStateOf(build.nom) }
        AlertDialog(
            onDismissRequest = { renommer = false },
            containerColor = Signal.Surface,
            title = { Text("Renommer le build") },
            text = {
                OutlinedTextField(
                    value = texte,
                    onValueChange = { texte = it.take(LONGUEUR_NOM) },
                    placeholder = { Text(vm.outilsBuilds.nom(Build(), rang)) },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal.Violet, cursorColor = Signal.Violet),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.changerBuild(rang, build.copy(nom = texte.trim()))
                    renommer = false
                }) { Text("OK", color = Signal.Violet) }
            },
            dismissButton = { TextButton(onClick = { renommer = false }) { Text("Annuler", color = Signal.TexteDoux) } },
        )
    }
}

/** Les cases du build hors « Autres », par groupe. */
private val GROUPES = listOf(
    "Équipement" to listOf(CaseBuild.MODULE, CaseBuild.BOUCLIER),
    "Armes" to listOf(CaseBuild.ARME_1, CaseBuild.ARME_2),
    "Consommables" to listOf(CaseBuild.SOINS, CaseBuild.GRENADES),
)

/** Des cases du build, deux par ligne. */
@Composable
private fun GrilleCases(vm: CarnetViewModel, build: Build, cases: List<CaseBuild>, choisir: (CaseBuild) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cases.chunked(2).forEach { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { case ->
                    CaseDuBuild(vm, case, build.objets[case], onClick = { choisir(case) }, modifier = Modifier.weight(1f))
                }
                if (ligne.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Une case du build : son nom et l'objet choisi (ou « + Choisir », en pointillés). La toucher ouvre le choix
 * de l'objet, où l'on peut aussi voir sa fiche ou vider la case.
 */
@Composable
private fun CaseDuBuild(vm: CarnetViewModel, case: CaseBuild, objetId: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(Modifier.heightIn(min = 64.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (objetId == null) {
                Box(
                    Modifier.size(44.dp).drawBehind {
                        drawRoundRect(
                            Signal.Trait,
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, contentDescription = null, tint = Signal.TexteDiscret) }
            } else {
                IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 44.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(case.libelle.uppercase(), style = MaterialTheme.typography.labelSmall, color = Signal.TexteDiscret)
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
