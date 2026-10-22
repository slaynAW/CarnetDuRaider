package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Etabli
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Projet
import io.github.slaynaw.carnetduraider.core.Quantite
import io.github.slaynaw.carnetduraider.core.cle
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Ce que le joueur peut indiquer en cours, chacun avec sa liste complète. */
enum class ChoixEnCours(val bouton: String, val court: String, val titre: String) {
    QUETES("Indiquer mes quêtes en cours", "Mes quêtes", "Mes quêtes en cours"),
    ATELIERS("Indiquer mes ateliers en cours", "Mes ateliers", "Mes ateliers en cours"),
    PROJETS("Indiquer mes projets en cours", "Mes projets", "Mes projets en cours"),
    EXPEDITIONS("Indiquer mes expéditions en cours", "Mes expéditions", "Mes expéditions en cours"),
}

/** Nombre d'éléments indiqués en cours pour ce choix. */
private fun nombreEnCours(vm: CarnetViewModel, choix: ChoixEnCours): Int = when (choix) {
    ChoixEnCours.QUETES -> vm.suivi.quetesEnCours(vm.progression).size
    ChoixEnCours.ATELIERS -> vm.catalogue.donnees.etablis.count { vm.suivi.niveauEnCours(vm.progression, it) != null }
    ChoixEnCours.PROJETS -> vm.catalogue.donnees.projets.count { !it.expedition && vm.suivi.etapeEnCours(vm.progression, it) != null }
    ChoixEnCours.EXPEDITIONS -> vm.catalogue.donnees.projets.count { it.expedition && vm.suivi.etapeEnCours(vm.progression, it) != null }
}

/**
 * Onglet Objectifs : tout est considéré comme déjà fait, sauf ce que le joueur indique en cours (quatre
 * boutons, chacun vers la liste complète). Au départ, il n'y a que ces boutons. Ce qui est en cours s'affiche
 * dessous, avec ce qui a déjà été rapporté ; tout se valide tout seul au retour de raid, et la suite s'ajoute.
 */
@Composable
fun EcranObjectifs(vm: CarnetViewModel, choisir: (ChoixEnCours) -> Unit, voirRaid: () -> Unit) {
    val objectifs = vm.objectifs
    val liste = vm.listeDeCourses()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BandeauSection(
                    Onglet.OBJECTIFS,
                    if (objectifs.isEmpty()) "Rien en cours" else pluriel(objectifs.size, "objectif en cours", "objectifs en cours"),
                )
                Text(
                    "Tout ce que tu n'indiques pas est considéré comme déjà fait. Indique ce que tu as en cours\u00a0: " +
                        "ce que tu rapportes de raid s'y ajoute, et tout se valide tout seul.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
            }
        }
        // Les quatre boutons, deux par ligne.
        items(ChoixEnCours.entries.chunked(2), key = { "choix-${it.first().name}" }) { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { choix ->
                    BoutonEnCours(choix, nombreEnCours(vm, choix), onClick = { choisir(choix) }, modifier = Modifier.weight(1f))
                }
            }
        }

        if (objectifs.isNotEmpty()) {
            item {
                Surface(onClick = voirRaid, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${pluriel(objectifs.size, "objectif en cours", "objectifs en cours")} · ${pluriel(liste.objetsATrouver, "objet", "objets")} à trouver",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text("Raid ›", style = MaterialTheme.typography.labelLarge, color = Signal.Rouge)
                        }
                        Barre(liste.progression.toFloat(), Signal.Vert, epaisseur = 8.dp)
                    }
                }
            }
            item { Etiquette("En cours", Signal.Vert, Modifier.padding(top = 8.dp)) }
            items(objectifs, key = { "objectif-${it.cle}" }) { objectif -> CarteObjectif(vm, objectif) }
        }
    }
}

/** Un des quatre boutons : vers la liste complète, avec le nombre d'éléments en cours. */
@Composable
private fun BoutonEnCours(choix: ChoixEnCours, enCours: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = Signal.Releve,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = choix.bouton },
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(choix.court, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    if (enCours == 0) "Rien en cours" else "$enCours en cours",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enCours == 0) Signal.TexteDiscret else Signal.Vert,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Signal.Vert)
        }
    }
}

private fun detailCout(cout: List<Quantite>, catalogue: Catalogue): String =
    cout.joinToString(" · ") { "${it.quantite} × ${catalogue.nomObjet(it.id)}" }

/** Ce que demande l'objectif, avec ce qui a déjà été rapporté : « Composants plastiques 20/50 · Alliage ARC 6/6 ». */
private fun detailAvancement(vm: CarnetViewModel, objectif: Objectif): String {
    val apporte = vm.apports[objectif.cle].orEmpty()
    return vm.planificateur.besoins(objectif).joinToString(" · ") {
        "${vm.catalogue.nomObjet(it.id)} ${minOf(apporte[it.id] ?: 0, it.quantite)}/${it.quantite}"
    }
}

/**
 * Un objectif en cours : ce qu'il demande et ce qui a été rapporté. Il se termine tout seul quand tout est
 * rapporté ; « C'est fait » le valide s'il est fait autrement en jeu (ou s'il n'y a rien à rapporter).
 * La même carte dans l'onglet Objectifs et dans l'onglet Raid : ce sont les mêmes objectifs.
 */
@Composable
fun CarteObjectif(vm: CarnetViewModel, objectif: Objectif) {
    val libelle = vm.planificateur.libelle(objectif)
    val besoins = vm.planificateur.besoins(objectif)
    val autres = (objectif as? Objectif.EtapeDeProjet)
        ?.let { vm.catalogue.projet(it.projetId)?.etapes?.getOrNull(it.etape)?.autresObjectifs } ?: 0
    val commercant = (objectif as? Objectif.QueteATerminer)?.let { vm.catalogue.quete(it.queteId)?.commercant }
    val apporte = vm.apports[objectif.cle].orEmpty()
    val voulu = besoins.sumOf { it.quantite }
    val rapporte = besoins.sumOf { minOf(apporte[it.id] ?: 0, it.quantite) }
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(libelle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (voulu > 0) {
                    Text("$rapporte/$voulu", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Signal.Vert)
                }
            }
            commercant?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux) }
            if (voulu > 0) Barre(rapporte.toFloat() / voulu, Signal.Vert)
            Text(
                if (besoins.isEmpty()) "Rien à rapporter\u00a0: valide-le quand c'est fait en jeu." else detailAvancement(vm, objectif),
                style = MaterialTheme.typography.bodySmall,
                color = Signal.TexteDoux,
            )
            if (autres > 0) {
                Text("Et d'autres conditions à remplir en jeu.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            Button(
                onClick = { vm.valider(objectif) },
                colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.semantics { contentDescription = "C'est fait\u00a0: $libelle" },
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("C'est fait", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** En-tête des écrans de choix : retour, titre et explication. */
@Composable
private fun EnTeteChoix(titre: String, explication: String, onRetour: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Bandeau("Objectifs", Signal.Vert)
        }
        Text(titre, style = MaterialTheme.typography.headlineMedium)
        Text(explication, style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
    }
}

/** Une pastille de choix (niveau, étape, « Aucun »), mise en avant quand elle est choisie. */
@Composable
private fun Pastille(texte: String, choisie: Boolean, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (choisie) Signal.Vert else Signal.Releve,
        contentColor = if (choisie) Signal.SurCouleur else Signal.Texte,
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics {
            contentDescription = description
            selected = choisie
        },
    ) {
        Text(texte, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/**
 * Mes quêtes en cours : le joueur coche celles qui sont en cours dans son journal de quêtes. Toutes les
 * autres sont faites d'office, sauf ce qui vient après elles (la suite s'ajoutera toute seule).
 */
@Composable
fun EcranQuetesEnCours(vm: CarnetViewModel, onRetour: () -> Unit) {
    val histoire = vm.catalogue.donnees.quetes.filter { it.histoire }
    var choix by rememberSaveable { mutableStateOf(vm.suivi.quetesEnCours(vm.progression).map { it.id }.toSet()) }
    var recherche by rememberSaveable { mutableStateOf("") }
    val cle = Catalogue.normaliser(recherche)
    val affichees = histoire
        .filter { cle.isBlank() || Catalogue.normaliser(it.nom).contains(cle) || Catalogue.normaliser(it.commercant.orEmpty()).contains(cle) }
        .sortedWith(compareBy({ it.id !in choix }, { it.commercant.orEmpty() }, { it.nom }))

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EnTeteChoix(
                    ChoixEnCours.QUETES.titre,
                    "Coche les quêtes en cours dans ton journal de quêtes. Toutes les autres sont considérées comme faites\u00a0; " +
                        "celles qui viennent après s'ajouteront toutes seules.",
                    onRetour,
                )
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    label = { Text("Chercher une quête ou un commerçant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        vm.indiquerQuetesEnCours(choix)
                        onRetour()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur),
                ) { Text("Valider (${pluriel(choix.size, "quête", "quêtes")})") }
            }
        }
        items(affichees, key = { it.id }) { quete ->
            val coche = quete.id in choix
            Surface(
                onClick = { choix = if (coche) choix - quete.id else choix + quete.id },
                color = Signal.Surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = coche,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Signal.Vert, checkmarkColor = Signal.SurCouleur),
                    )
                    Box(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(quete.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val detail = listOfNotNull(quete.commercant, detailCout(quete.objets, vm.catalogue).takeIf { it.isNotEmpty() })
                        if (detail.isNotEmpty()) {
                            Text(detail.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mes ateliers en cours : pour chaque établi de l'Atelier, le niveau en cours d'amélioration (les précédents
 * sont faits), ou « Aucun ». Chaque choix s'applique tout de suite.
 */
@Composable
fun EcranAteliersEnCours(vm: CarnetViewModel, onRetour: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            EnTeteChoix(
                ChoixEnCours.ATELIERS.titre,
                "Pour chaque établi de l'Atelier, choisis le niveau que tu es en train d'améliorer. Une fois terminé, le suivant s'ajoute tout seul.",
                onRetour,
            )
        }
        items(vm.catalogue.donnees.etablis.sortedBy { it.nom }, key = { it.id }) { etabli -> ChoixEtabli(vm, etabli) }
    }
}

@Composable
private fun ChoixEtabli(vm: CarnetViewModel, etabli: Etabli) {
    val enCours = vm.suivi.niveauEnCours(vm.progression, etabli)
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(etabli.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pastille("Aucun", enCours == null, "${etabli.nom}\u00a0: rien en cours") { vm.indiquerNiveauEnCours(etabli, null) }
                etabli.niveaux.sortedBy { it.niveau }.forEach { n ->
                    Pastille("Niveau ${n.niveau}", enCours == n.niveau, "${etabli.nom}\u00a0: niveau ${n.niveau} en cours") {
                        vm.indiquerNiveauEnCours(etabli, n.niveau)
                    }
                }
            }
            enCours?.let { niveau ->
                val cout = etabli.niveaux.firstOrNull { it.niveau == niveau }?.cout.orEmpty()
                Text(
                    "Niveau $niveau\u00a0: ${detailAvancement(vm, Objectif.NiveauDEtabli(etabli.id, niveau)).ifEmpty { detailCout(cout, vm.catalogue) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.Or,
                )
            }
        }
    }
}

/**
 * Mes projets (ou mes Expéditions) en cours : pour chacun, l'étape en cours (les précédentes sont faites),
 * ou « Aucune ». Chaque choix s'applique tout de suite.
 */
@Composable
fun EcranProjetsEnCours(vm: CarnetViewModel, expeditions: Boolean, onRetour: () -> Unit) {
    val choix = if (expeditions) ChoixEnCours.EXPEDITIONS else ChoixEnCours.PROJETS
    val projets = vm.catalogue.donnees.projets.filter { it.expedition == expeditions }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            EnTeteChoix(
                choix.titre,
                "Choisis l'étape en cours de chacun. Une fois terminée, la suivante s'ajoute toute seule.",
                onRetour,
            )
        }
        items(projets, key = { it.id }) { projet -> ChoixProjet(vm, projet) }
    }
}

@Composable
private fun ChoixProjet(vm: CarnetViewModel, projet: Projet) {
    val enCours = vm.suivi.etapeEnCours(vm.progression, projet)
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(projet.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pastille("Aucune", enCours == null, "${projet.nom}\u00a0: pas en cours") { vm.indiquerEtapeEnCours(projet, null) }
                projet.etapes.indices.forEach { etape ->
                    Pastille(
                        "Étape ${etape + 1}",
                        enCours == etape,
                        "${projet.nom}\u00a0: ${vm.planificateur.nomEtape(projet, etape)} en cours",
                    ) { vm.indiquerEtapeEnCours(projet, etape) }
                }
            }
            enCours?.let { etape ->
                val objectif = Objectif.EtapeDeProjet(projet.id, etape)
                Text(vm.planificateur.nomEtape(projet, etape), style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
                val detail = detailAvancement(vm, objectif)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Signal.Or)
                if (projet.etapes[etape].autresObjectifs > 0) {
                    Text("Et d'autres conditions à remplir en jeu.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                }
            }
        }
    }
}
