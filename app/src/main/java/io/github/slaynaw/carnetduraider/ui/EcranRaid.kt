package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.ButinRecherche
import io.github.slaynaw.carnetduraider.core.CarteConseillee
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.ConseilRaid
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.ObjetATrouver
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.core.cle
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/**
 * Onglet Raid, « Préparer mon raid » : ce qu'il te faut et où aller, sans rien préparer. La liste se
 * remplit toute seule avec ce qui manque pour tes objectifs en cours ;
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
    ajouterButin: () -> Unit,
) {
    val objectifs = vm.objectifs
    val aTrouver = vm.aTrouver()
    val restants = aTrouver.filter { !it.trouve }
    val conseil = remember(restants.map { it.objetId }) { vm.conseilRaid() }

    val etat by vm.calendrier.etat.collectAsStateWithLifecycle()
    val calendrier = remember(etat.donnees) { etat.donnees?.let(vm::calendrierOfficiel) }
    val enCours = calendrier?.enCours(vm.region, maintenant())?.groupBy { it.carteId }.orEmpty()

    // Cartes détaillées : celles du parcours (les cartes à faire), sinon la meilleure.
    val detaillees = conseil.parcours.mapNotNull { e -> conseil.cartes.firstOrNull { it.carteId == e.carteId } }
        .ifEmpty { conseil.cartes.take(1) }
    val autres = conseil.cartes.filter { c -> detaillees.none { it.carteId == c.carteId } }
    val avecButin = conseil.cartes.flatMap { it.objets }.toSet().size

    val meilleure = conseil.cartes.firstOrNull()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BandeauSection(
                    Onglet.RAID,
                    when {
                        restants.isNotEmpty() -> "${pluriel(restants.size, "objet", "objets")} à trouver"
                        objectifs.isNotEmpty() -> "Rien à chercher en raid"
                        else -> "Rien à chercher pour l'instant"
                    },
                )
                if (restants.isEmpty()) {
                    Text(
                        if (objectifs.isNotEmpty()) {
                            "Tes objectifs en cours ne demandent aucun objet à rapporter."
                        } else {
                            "Rien à chercher pour l'instant."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Signal.TexteDoux,
                    )
                }
            }
        }

        // La meilleure carte, en un coup d'œil (le détail est dans « Où aller »).
        if (meilleure != null) {
            item {
                Surface(color = Signal.Rouge, contentColor = Signal.SurCouleur, shape = FormeCoupee, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Etiquette("Meilleure carte", Signal.SurCouleur)
                        Text(nomCarte(vm, meilleure.carteId), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            buildString {
                                append("${meilleure.objets.size} de tes ${pluriel(restants.size, "objet", "objets")} à trouver")
                                meilleure.zones.firstOrNull()?.let { append(" · surtout ${it.zone}") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (aTrouver.isNotEmpty()) {
            item { TitreRaid("Ce qu'il te faut") }
            items(aTrouver, key = { "atrouver-${it.objetId}" }) { a -> LigneATrouver(vm, a, onClick = { ouvrir(a.objetId) }) }
        }

        // De retour : saisir le butin, qui se décompte de la liste.
        if (restants.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = retourDeRaid,
                    border = BorderStroke(2.dp, Signal.Rouge),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Signal.Rouge),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text("Retour de raid\u00a0: ajouter mon butin", style = MaterialTheme.typography.titleMedium) }
            }
        }

        // Les mêmes objectifs que dans l'onglet Objectifs, avec leur avancement et « C'est fait ».
        if (objectifs.isNotEmpty()) {
            item {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Etiquette("Mes objectifs", Signal.Vert, Modifier.weight(1f))
                    TextButton(onClick = choisirObjectifs) { Text("Modifier", color = Signal.Vert) }
                }
            }
            items(objectifs, key = { "objectif-${it.cle}" }) { objectif -> CarteObjectif(vm, objectif) }
        }

        if (objectifs.isEmpty()) {
            item {
                Surface(color = Signal.Releve, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Indique ce que tu as en cours (quêtes, ateliers, projets, Expéditions)\u00a0: " +
                                "l'appli te dira sur quelle carte aller et où chercher.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                        Button(
                            onClick = choisirObjectifs,
                            colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur),
                        ) { Text("Indiquer mes objectifs") }
                    }
                }
            }
        }

        // Butin recherché : des objets à rapporter pour soi, sans quête ni projet.
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Etiquette(ButinRecherche.TITRE, Signal.Rouge, Modifier.weight(1f))
                TextButton(onClick = ajouterButin) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Signal.Rouge, modifier = Modifier.size(18.dp))
                    Text("Ajouter un objet", color = Signal.Rouge, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (vm.butinRecherche.isEmpty()) {
            item {
                Text(
                    "Des objets à rapporter pour toi, sans quête ni projet\u00a0: ils s'ajoutent à ce qu'il te faut, " +
                        "se décomptent au retour de raid et quittent la liste une fois tous rapportés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
        }
        items(vm.butinRecherche.entries.toList(), key = { "butin-${it.key}" }) { (id, voulu) ->
            LigneButin(vm, id, voulu, ouvrir = { ouvrir(id) })
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

        if (conseil.sansButin.isNotEmpty()) {
            item { TitreRaid("Pas dans le butin") }
            items(conseil.sansButin, key = { "sans-$it" }) { id ->
                val objet = vm.catalogue.objet(id)
                Surface(onClick = { ouvrir(id) }, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
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

/**
 * Un objet du butin recherché : combien ont été rapportés sur combien il en faut, de quoi changer la
 * quantité voulue (jamais moins d'un de plus que ce qui a été rapporté) et de quoi le retirer.
 */
@Composable
private fun LigneButin(vm: CarnetViewModel, objetId: String, voulu: Int, ouvrir: () -> Unit) {
    val nom = vm.catalogue.nomObjet(objetId)
    val rapportes = ButinRecherche.rapportes(vm.apports, objetId)
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = ouvrir, color = Signal.Surface, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 40.dp)
                    Column {
                        Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text("$rapportes/$voulu rapportés", style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
                    }
                }
            }
            BoutonQuantite("−", "Un de moins\u00a0: $nom", enabled = voulu > rapportes + 1) { vm.changerQuantiteButin(objetId, voulu - 1) }
            Text("$voulu", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Signal.Rouge)
            BoutonQuantite("+", "Un de plus\u00a0: $nom", enabled = voulu < ButinRecherche.MAXIMUM) { vm.changerQuantiteButin(objetId, voulu + 1) }
            IconButton(onClick = { vm.changerQuantiteButin(objetId, 0) }) {
                Icon(Icons.Filled.Close, contentDescription = "Retirer du butin recherché\u00a0: $nom", tint = Signal.TexteDoux)
            }
        }
    }
}

@Composable
private fun BoutonQuantite(texte: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Signal.Releve,
        contentColor = if (enabled) Signal.Texte else Signal.TexteDiscret,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.size(40.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Text(texte, style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun TitreRaid(texte: String) {
    Etiquette(texte, Signal.Rouge, Modifier.padding(top = 12.dp, bottom = 2.dp))
}

private fun nomCarte(vm: CarnetViewModel, carteId: String): String = vm.catalogue.carte(carteId)?.nom ?: Catalogue.NOM_A_VERIFIER

/**
 * Un objet à trouver : combien il en reste (le décompte, mis à jour au retour de raid), combien ont été
 * rapportés sur combien il en faut, et pour quoi.
 */
@Composable
private fun LigneATrouver(vm: CarnetViewModel, a: ObjetATrouver, onClick: () -> Unit) {
    val nom = vm.catalogue.nomObjet(a.objetId)
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconeObjet(a.objetId, vm.catalogue.objet(a.objetId)?.rarete, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${a.trouves}/${a.voulu} · " + a.pour.joinToString(" · ") { it.libelle },
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
        }
    }
}

/** Les cartes à faire, dans l'ordre, pour trouver toute la liste (celles qui ont un butin connu). */
@Composable
private fun Parcours(vm: CarnetViewModel, conseil: ConseilRaid, aTrouver: Int) {
    Surface(color = Signal.Releve, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
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
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
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
                    Surface(onClick = { ouvrirZone(c.carteId, z.zone) }, color = Signal.Releve, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
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
