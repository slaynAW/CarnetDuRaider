package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.CarteConseillee
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.ConseilRaid
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Cartes détaillées (avec leurs zones) ; les suivantes sont seulement citées. */
private const val CARTES_DETAILLEES = 3

/**
 * Préparer mon raid : la liste de ce qu'on cherche (ajout en un geste de la liste de courses, ou objet
 * par objet), puis les cartes où aller et les zones où chercher pour avoir le plus de chances de tout
 * trouver, d'après le butin des fichiers du jeu ([io.github.slaynaw.carnetduraider.core.PreparationRaid]).
 */
@Composable
fun EcranPreparerRaid(
    vm: CarnetViewModel,
    onRetour: () -> Unit,
    ouvrir: (String) -> Unit,
    ouvrirZone: (carteId: String, zone: String) -> Unit,
) {
    var recherche by rememberSaveable { mutableStateOf("") }
    val liste = vm.listeRaid
    val conseil = remember(liste.keys) { vm.conseilRaid() }
    val aAjouter = vm.listeDeCourses().lignes.count { it.manquant > 0 && (liste[it.objetId] ?: 0) < it.manquant }
    val resultats = if (recherche.isBlank()) emptyList() else vm.catalogue.rechercher(recherche, limite = 8)

    val etat by vm.calendrier.etat.collectAsStateWithLifecycle()
    val calendrier = remember(etat.donnees) { etat.donnees?.let(vm::calendrierOfficiel) }
    val enCours = calendrier?.enCours(vm.region, maintenant())?.groupBy { it.carteId }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Bandeau("Raid", Signal.Cyan)
                }
                Text("Préparer mon raid", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Ajoute ce que tu cherches\u00a0: l'appli te dit sur quelle carte aller et où chercher pour avoir le plus de chances de le trouver.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (aAjouter > 0) {
                        Button(
                            onClick = { vm.ajouterListeDeCoursesAuRaid() },
                            colors = ButtonDefaults.buttonColors(containerColor = Signal.Cyan, contentColor = Signal.SurCouleur),
                        ) { Text("Ajouter ma liste de courses ($aAjouter)") }
                    }
                    if (liste.isNotEmpty()) {
                        TextButton(onClick = vm::viderRaid) { Text("Tout retirer", color = Signal.TexteDoux) }
                    }
                }
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    placeholder = { Text("Ajouter un objet…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Cyan) },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Signal.Cyan,
                        unfocusedBorderColor = Signal.Trait,
                        focusedContainerColor = Signal.Surface,
                        unfocusedContainerColor = Signal.Surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(resultats, key = { "resultat-${it.id}" }) { o ->
            Surface(
                onClick = {
                    vm.ajouterAuRaid(o.id)
                    recherche = ""
                },
                color = Signal.Surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconeObjet(o.id, o.rarete, 36.dp)
                    Text(o.nom, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (o.id in liste) "× ${liste.getValue(o.id)}  +1" else "Ajouter", style = MaterialTheme.typography.labelLarge, color = Signal.Cyan)
                }
            }
        }

        if (liste.isEmpty()) {
            item {
                Text(
                    "Ta liste est vide. Ajoute ta liste de courses ou cherche un objet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@LazyColumn
        }

        item { TitreRaid("Ce que je cherche (${liste.size})") }
        items(liste.entries.toList(), key = { "cherche-${it.key}" }) { (id, quantite) ->
            LigneCherchee(vm, id, quantite, onClick = { ouvrir(id) })
        }

        if (conseil.cartes.isNotEmpty()) {
            item { TitreRaid("Où aller") }
            if (conseil.parcours.size > 1 || conseil.parcours.firstOrNull()?.carteId != conseil.cartes.first().carteId) {
                item { Parcours(vm, conseil, conseil.cartes.flatMap { it.objets }.toSet().size) }
            }
            items(conseil.cartes.take(CARTES_DETAILLEES), key = { "carte-${it.carteId}" }) { c ->
                CarteRaid(vm, c, liste.size, rang = conseil.cartes.indexOf(c), enCours[c.carteId].orEmpty(), ouvrirZone)
            }
            val autres = conseil.cartes.drop(CARTES_DETAILLEES)
            if (autres.isNotEmpty()) {
                item {
                    Text(
                        "Aussi\u00a0: " + autres.joinToString(" · ") { "${nomCarte(vm, it.carteId)} (${pluriel(it.objets.size, "objet", "objets")})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Signal.TexteDoux,
                    )
                }
            }
            item {
                Text(
                    "Classement d'après les tables de butin du jeu (condition normale). Les taux de drop exacts ne sont pas publiés\u00a0: le jeu tire le butin sur ses serveurs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
        }

        if (conseil.sansButin.isNotEmpty()) {
            item { TitreRaid("Pas dans le butin") }
            items(conseil.sansButin, key = { "sans-$it" }) { id ->
                val objet = vm.catalogue.objet(id)
                Surface(onClick = { ouvrir(id) }, color = Signal.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconeObjet(id, objet?.rarete, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(vm.catalogue.nomObjet(id), style = MaterialTheme.typography.bodyLarge)
                            Text(objet?.let { autreMoyen(vm.catalogue, it) } ?: "", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitreRaid(texte: String) {
    Text(texte, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

private fun nomCarte(vm: CarnetViewModel, carteId: String): String = vm.catalogue.carte(carteId)?.nom ?: Catalogue.NOM_A_VERIFIER

/** Un objet cherché : où le chercher (types de zones) et la quantité voulue. */
@Composable
private fun LigneCherchee(vm: CarnetViewModel, objetId: String, quantite: Int, onClick: () -> Unit) {
    val objet = vm.catalogue.objet(objetId)
    val nom = vm.catalogue.nomObjet(objetId)
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconeObjet(objetId, objet?.rarete, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val ou = objet?.lieux?.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                Text(
                    "${vm.possede(objetId)} en réserve" + (ou?.let { " · zones $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = Signal.TexteDoux,
                )
            }
            FilledIconButton(
                onClick = { vm.changerRaid(objetId, quantite - 1) },
                modifier = Modifier.size(36.dp).semantics { contentDescription = "En chercher un de moins\u00a0: $nom" },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Texte),
            ) { Text("−", style = MaterialTheme.typography.titleMedium) }
            Text("$quantite", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            FilledIconButton(
                onClick = { vm.changerRaid(objetId, quantite + 1) },
                modifier = Modifier.size(36.dp).semantics { contentDescription = "En chercher un de plus\u00a0: $nom" },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Cyan),
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** Les cartes à faire, dans l'ordre, pour trouver toute la liste (celles qui ont un butin connu). */
@Composable
private fun Parcours(vm: CarnetViewModel, conseil: ConseilRaid, aTrouver: Int) {
    Surface(color = Signal.Releve, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pour tout trouver", style = MaterialTheme.typography.titleSmall, color = Signal.Cyan)
            conseil.parcours.forEachIndexed { i, etape ->
                Text(
                    "${i + 1}. ${nomCarte(vm, etape.carteId)} · " +
                        (if (i == 0) pluriel(etape.objets.size, "objet", "objets") else "+ ${pluriel(etape.objets.size, "objet", "objets")}"),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            val couverts = conseil.parcours.sumOf { it.objets.size }
            if (couverts < aTrouver) {
                Text(
                    "${pluriel(aTrouver - couverts, "autre objet", "autres objets")} sur d'autres cartes (voir plus bas).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
        }
    }
}

/** Une carte conseillée : ce qu'on y trouve, les conditions en cours, et les zones où chercher. */
@Composable
private fun CarteRaid(
    vm: CarnetViewModel,
    c: CarteConseillee,
    total: Int,
    rang: Int,
    enCours: List<Creneau>,
    ouvrirZone: (String, String) -> Unit,
) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (rang == 0) Text("LE MIEUX", style = MaterialTheme.typography.labelSmall, color = Signal.Cyan)
                    Text(nomCarte(vm, c.carteId), style = MaterialTheme.typography.titleLarge)
                }
                Text("${c.objets.size}/$total", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Signal.Cyan)
            }
            Text(
                buildString {
                    append(pluriel(c.objets.size, "objet de ta liste", "objets de ta liste"))
                    if (c.meilleurePour.isNotEmpty()) append(", dont ${c.meilleurePour.size} à leur meilleur endroit")
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Signal.TexteDoux,
            )
            enCours.forEach { cr ->
                Text(
                    "En ce moment\u00a0: ${cr.condition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cr.majeure) Signal.Rouge else Signal.Cyan,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                c.objets.forEach { id -> IconeObjet(id, vm.catalogue.objet(id)?.rarete, 32.dp) }
            }
            if (c.zones.isNotEmpty()) {
                Text("Où chercher", style = MaterialTheme.typography.titleSmall, color = Signal.Or, modifier = Modifier.padding(top = 4.dp))
                c.zones.forEach { z ->
                    Surface(onClick = { ouvrirZone(c.carteId, z.zone) }, color = Signal.Releve, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(z.zone, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    z.objets.joinToString(" · ") { vm.catalogue.nomObjet(it) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Signal.TexteDoux,
                                )
                            }
                            Text("Plan ›", style = MaterialTheme.typography.labelMedium, color = Signal.Cyan)
                        }
                    }
                }
            }
        }
    }
}

/** Pour un objet absent du butin : le premier autre moyen de l'obtenir que donnent les données du jeu. */
private fun autreMoyen(catalogue: Catalogue, objet: Objet): String {
    val o = objet.obtention
    if ("ARC" in objet.lieux) return "Sur les ARC détruits"
    o.achats.firstOrNull()?.let { a ->
        val prix = when {
            a.pieces > 0 -> " (${a.pieces} pièces)"
            a.valeurObjets > 0 -> " (en échange d'objets)"
            else -> ""
        }
        return "Chez ${a.commercant}$prix"
    }
    o.fabrications.firstOrNull()?.let { f ->
        return "Fabrication\u00a0: " + (f.etabli?.let { e -> f.niveau?.let { "$e · niveau $it" } ?: e } ?: "pendant un raid")
    }
    catalogue.sourcesRecyclage(objet.id).firstOrNull()?.let { return "En recyclant\u00a0: ${catalogue.nomObjet(it.id)}" }
    o.recompenses.firstOrNull()?.let { return "En récompense\u00a0: $it" }
    return "Aucune source connue dans les données du jeu."
}
