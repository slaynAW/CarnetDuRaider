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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.CarteConseillee
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.ConseilRaid
import io.github.slaynaw.carnetduraider.core.ObjetATrouver
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/**
 * Onglet Raid, « Préparer mon raid » : ce qu'il te faut et où aller, sans rien préparer. La liste se
 * remplit toute seule avec ce qui manque pour tes objectifs suivis, plus les objets que tu ajoutes ;
 * l'appli en déduit les cartes et les zones où tu as le plus de chances de tout trouver
 * ([io.github.slaynaw.carnetduraider.core.PreparationRaid]). Le retour de raid décompte ce que tu
 * rapportes.
 */
@Composable
fun EcranRaid(
    vm: CarnetViewModel,
    ouvrir: (String) -> Unit,
    ouvrirZone: (carteId: String, zone: String) -> Unit,
    retourDeRaid: () -> Unit,
    choisirObjectifs: () -> Unit,
) {
    var recherche by rememberSaveable { mutableStateOf("") }
    val aTrouver = vm.aTrouver()
    val restants = aTrouver.filter { !it.trouve }
    val conseil = remember(restants.map { it.objetId }) { vm.conseilRaid() }
    val resultats = if (recherche.isBlank()) emptyList() else vm.catalogue.rechercher(recherche, limite = 8)

    val etat by vm.calendrier.etat.collectAsStateWithLifecycle()
    val calendrier = remember(etat.donnees) { etat.donnees?.let(vm::calendrierOfficiel) }
    val enCours = calendrier?.enCours(vm.region, maintenant())?.groupBy { it.carteId }.orEmpty()

    // Cartes détaillées : celles du parcours (les cartes à faire), sinon la meilleure.
    val detaillees = conseil.parcours.mapNotNull { e -> conseil.cartes.firstOrNull { it.carteId == e.carteId } }
        .ifEmpty { conseil.cartes.take(1) }
    val autres = conseil.cartes.filter { c -> detaillees.none { it.carteId == c.carteId } }
    val avecButin = conseil.cartes.flatMap { it.objets }.toSet().size

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bandeau("Raid", Signal.Rouge)
                Text("Préparer mon raid", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (restants.isEmpty()) {
                        "Rien à chercher pour l'instant."
                    } else {
                        "${pluriel(restants.size, "objet", "objets")} à trouver pour tes objectifs et ta liste. Voici où aller."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
            }
        }

        // De retour : saisir le butin, qui se décompte de la liste.
        if (restants.isNotEmpty()) {
            item {
                Button(
                    onClick = retourDeRaid,
                    colors = ButtonDefaults.buttonColors(containerColor = Signal.Or, contentColor = Signal.SurCouleur),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Retour de raid\u00a0: ajouter mon butin", style = MaterialTheme.typography.titleMedium) }
            }
        }

        if (restants.isEmpty()) {
            item {
                Surface(color = Signal.Releve, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Suis un objectif (un établi à améliorer, une quête, un projet) ou ajoute un objet ci-dessous\u00a0: " +
                                "l'appli te dira sur quelle carte aller et où chercher.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                        Button(
                            onClick = choisirObjectifs,
                            colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur),
                        ) { Text("Choisir mes objectifs") }
                    }
                }
            }
        }

        if (detaillees.isNotEmpty()) {
            item { TitreRaid("Où aller") }
            if (conseil.parcours.size > 1) {
                item { Parcours(vm, conseil, avecButin) }
            }
            items(detaillees, key = { "carte-${it.carteId}" }) { c ->
                CarteRaid(vm, c, avecButin, meilleure = c.carteId == conseil.cartes.first().carteId, enCours[c.carteId].orEmpty(), ouvrirZone)
            }
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
                    color = Signal.TexteDiscret,
                )
            }
        }

        if (aTrouver.isNotEmpty()) {
            item {
                Text("Ce qu'il te faut", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            items(aTrouver, key = { "atrouver-${it.objetId}" }) { a -> LigneATrouver(vm, a, onClick = { ouvrir(a.objetId) }) }
        }

        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chercher autre chose", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    placeholder = { Text("Ajouter un objet à chercher…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Rouge) },
                    trailingIcon = {
                        if (recherche.isNotEmpty()) {
                            IconButton(onClick = { recherche = "" }) { Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Signal.Rouge,
                        unfocusedBorderColor = Signal.Trait,
                        focusedContainerColor = Signal.Surface,
                        unfocusedContainerColor = Signal.Surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(resultats, key = { "resultat-${it.id}" }) { o ->
            val voulu = vm.listeRaid[o.id]
            Surface(
                onClick = { vm.ajouterAuRaid(o.id) },
                color = Signal.Surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconeObjet(o.id, o.rarete, 36.dp)
                    Text(o.nom, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (voulu == null) "Ajouter" else "× $voulu  +1", style = MaterialTheme.typography.labelLarge, color = Signal.Rouge)
                }
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

/**
 * Un objet à trouver : combien il en reste (le décompte, mis à jour au retour de raid), combien on en a
 * sur combien il en faut, et pour quoi. Un objet ajouté à la main se retire d'un geste.
 */
@Composable
private fun LigneATrouver(vm: CarnetViewModel, a: ObjetATrouver, onClick: () -> Unit) {
    val nom = vm.catalogue.nomObjet(a.objetId)
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconeObjet(a.objetId, vm.catalogue.objet(a.objetId)?.rarete, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${a.trouves}/${a.voulu} · " + if (a.ajouteParLeJoueur) "ajouté par toi" else a.pour.joinToString(" · ") { it.libelle },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (a.trouve) Signal.Vert else Signal.TexteDoux,
                    maxLines = 2,
                )
            }
            if (a.trouve) {
                Icon(Icons.Filled.Check, contentDescription = "Trouvé", tint = Signal.Vert, modifier = Modifier.size(24.dp))
            } else {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
                    Text("${a.reste}", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Signal.Or)
                    Text("à trouver", style = MaterialTheme.typography.labelSmall, color = Signal.TexteDoux)
                }
            }
            if (a.ajouteParLeJoueur) {
                IconButton(onClick = { vm.changerRaid(a.objetId, 0) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Ne plus chercher\u00a0: $nom", tint = Signal.TexteDoux)
                }
            }
        }
    }
}

/** Les cartes à faire, dans l'ordre, pour trouver toute la liste (celles qui ont un butin connu). */
@Composable
private fun Parcours(vm: CarnetViewModel, conseil: ConseilRaid, aTrouver: Int) {
    Surface(color = Signal.Releve, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pour tout trouver", style = MaterialTheme.typography.titleSmall, color = Signal.Rouge)
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
                    "${pluriel(aTrouver - couverts, "autre objet", "autres objets")} sur d'autres cartes.",
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
    meilleure: Boolean,
    enCours: List<Creneau>,
    ouvrirZone: (String, String) -> Unit,
) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (meilleure) Text("LE MIEUX", style = MaterialTheme.typography.labelSmall, color = Signal.Rouge)
                    Text(nomCarte(vm, c.carteId), style = MaterialTheme.typography.titleLarge)
                }
                Text("${c.objets.size}/$total", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Signal.Rouge)
            }
            Text(
                buildString {
                    append(pluriel(c.objets.size, "objet à trouver", "objets à trouver"))
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
