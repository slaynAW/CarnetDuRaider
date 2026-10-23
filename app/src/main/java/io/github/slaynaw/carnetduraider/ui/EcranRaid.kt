package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    var objectifsOuverts by rememberSaveable { mutableStateOf(false) }
    var cartesOuvertes by rememberSaveable { mutableStateOf(false) }

    val etat by vm.calendrier.etat.collectAsStateWithLifecycle()
    val calendrier = remember(etat.donnees) { etat.donnees?.let(vm::calendrierOfficiel) }
    val enCours = calendrier?.enCours(vm.region, maintenant())?.groupBy { it.carteId }.orEmpty()

    // Cartes détaillées : celles du parcours (les cartes à faire), sinon la meilleure.
    val detaillees = conseil.parcours.mapNotNull { e -> conseil.cartes.firstOrNull { it.carteId == e.carteId } }
        .ifEmpty { conseil.cartes.take(1) }
    val autres = conseil.cartes.filter { c -> detaillees.none { it.carteId == c.carteId } }
    val avecButin = conseil.cartes.flatMap { it.objets }.toSet().size

    val meilleure = conseil.cartes.firstOrNull()

    EcranDeSection(
        Onglet.RAID,
        when {
            restants.isNotEmpty() -> "${pluriel(restants.size, "objet", "objets")} à trouver"
            objectifs.isNotEmpty() -> "Rien à chercher en raid"
            else -> "Rien à chercher pour l'instant"
        },
    ) { defilement, haut ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = defilement,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = haut + 12.dp, bottom = if (restants.isNotEmpty()) PLACE_BOUTON_FIXE else 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (restants.isEmpty()) {
                    item {
                        Text(
                            if (objectifs.isNotEmpty()) "Tes objectifs en cours ne demandent aucun objet à rapporter." else "Rien à chercher pour l'instant.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }

                // La meilleure carte en tête, avec ses zones à un geste et la carte suivante du parcours.
                if (meilleure != null) {
                    item { CarteMeilleure(vm, meilleure, restants.size, conseil, ouvrirZone) }
                }

                if (aTrouver.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Etiquette("Ce qu'il te faut · ${restants.size}", Signal.Rouge, Modifier.weight(1f))
                            Text(
                                "${aTrouver.sumOf { it.trouves }} sur ${aTrouver.sumOf { it.voulu }} rapportés",
                                style = MaterialTheme.typography.bodySmall,
                                color = Signal.TexteDiscret,
                            )
                        }
                    }
                    item { ListeATrouver(vm, aTrouver, ouvrir) }
                }

                // Les mêmes objectifs que dans l'onglet Objectifs (même carte, « C'est fait »), repliés.
                if (objectifs.isNotEmpty()) {
                    item {
                        EnTeteAccordeon(
                            "Mes objectifs",
                            "${pluriel(objectifs.size, "en cours", "en cours")} · d'où vient chaque objet",
                            objectifsOuverts,
                            Signal.Vert,
                            onClick = { objectifsOuverts = !objectifsOuverts },
                        )
                    }
                    if (objectifsOuverts) {
                        items(objectifs, key = { "objectif-${it.cle}" }) { objectif -> CarteObjectif(vm, objectif) }
                        item {
                            TextButton(onClick = choisirObjectifs) { Text("Modifier mes objectifs", color = Signal.Vert) }
                        }
                    }
                } else {
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
                    Surface(onClick = ajouterButin, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ButinRecherche.TITRE, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (vm.butinRecherche.isEmpty()) "Des objets pour toi, sans objectif du jeu" else pluriel(vm.butinRecherche.size, "objet", "objets"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Signal.TexteDoux,
                                )
                            }
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Signal.Rouge, modifier = Modifier.size(18.dp))
                            Text("Ajouter", style = MaterialTheme.typography.titleSmall, color = Signal.Rouge)
                        }
                    }
                }
                items(vm.butinRecherche.entries.toList(), key = { "butin-${it.key}" }) { (id, voulu) ->
                    LigneButin(vm, id, voulu, ouvrir = { ouvrir(id) })
                }

                // Le détail de toutes les cartes conseillées, replié : la meilleure est déjà en tête.
                if (detaillees.isNotEmpty()) {
                    item {
                        EnTeteAccordeon(
                            "Où aller",
                            if (conseil.parcours.size > 1) "Parcours en ${conseil.parcours.size} cartes · zones où chercher" else "Zones où chercher",
                            cartesOuvertes,
                            Signal.Rouge,
                            onClick = { cartesOuvertes = !cartesOuvertes },
                        )
                    }
                    if (cartesOuvertes) {
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

            // De retour : saisir le butin, qui se décompte de la liste. Toujours à portée de pouce.
            if (restants.isNotEmpty()) {
                BoutonFixe("Retour de raid\u00a0: ajouter mon butin", Signal.Rouge, retourDeRaid, Modifier.align(Alignment.BottomCenter))
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
 * La meilleure carte, en tête de l'onglet : sur une capture en jeu de la carte, assombrie à gauche pour que
 * le texte reste lisible (« ombre latérale ») ; sans capture, sur le rouge du Raid. « Où chercher » déplie
 * ses zones (chacune ouvre le plan) et la carte suivante du parcours.
 */
@Composable
private fun CarteMeilleure(
    vm: CarnetViewModel,
    c: CarteConseillee,
    restants: Int,
    conseil: ConseilRaid,
    ouvrirZone: (String, String) -> Unit,
) {
    val image = rememberFondCarte(c.carteId)
    var zonesOuvertes by rememberSaveable { mutableStateOf(false) }
    val texte = if (image != null) Signal.Texte else Signal.SurCouleur
    val ensuite = conseil.parcours.takeIf { it.firstOrNull()?.carteId == c.carteId }?.getOrNull(1)
    Surface(
        color = if (image != null) Signal.Fond else Signal.Rouge,
        contentColor = texte,
        shape = FormeCoupee,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            if (image != null) FondEnJeu(image)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Etiquette("Meilleure carte", if (image != null) Signal.Rouge else Signal.SurCouleur)
                Text(nomCarte(vm, c.carteId), style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString {
                        append("${c.objets.size} de tes ${pluriel(restants, "objet", "objets")} à trouver")
                        c.zones.firstOrNull()?.let { append(" · surtout ${it.zone}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (c.zones.isNotEmpty()) {
                    Surface(
                        onClick = { zonesOuvertes = !zonesOuvertes },
                        color = texte.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(top = 8.dp).semantics { stateDescription = if (zonesOuvertes) "Ouvert" else "Fermé" },
                    ) {
                        Text(
                            if (zonesOuvertes) "Masquer les zones" else "Où chercher · ${pluriel(c.zones.size, "zone", "zones")}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                if (zonesOuvertes) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.zones.forEach { z ->
                            Surface(
                                onClick = { ouvrirZone(c.carteId, z.zone) },
                                color = Signal.Fond.copy(alpha = 0.72f),
                                contentColor = Signal.Texte,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(z.zone, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(pluriel(z.objets.size, "objet", "objets"), style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                                    Text("Plan ›", style = MaterialTheme.typography.labelMedium, color = Signal.Cyan)
                                }
                            }
                        }
                        if (ensuite != null) {
                            Text(
                                "Ensuite\u00a0: ${nomCarte(vm, ensuite.carteId)}, pour ${pluriel(ensuite.objets.size, "autre objet", "autres objets")}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (image != null) Signal.TexteDoux else Signal.SurCouleur,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ce qu'il te faut, en liste compacte (une ligne par objet) : on en voit une dizaine d'un coup d'œil.
 * Chaque ligne ouvre la fiche de l'objet.
 */
@Composable
private fun ListeATrouver(vm: CarnetViewModel, aTrouver: List<ObjetATrouver>, ouvrir: (String) -> Unit) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            aTrouver.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = Signal.Releve)
                LigneATrouver(vm, a, onClick = { ouvrir(a.objetId) })
            }
        }
    }
}

/**
 * Un objet à trouver, sur une ligne : combien il en reste (le décompte, mis à jour au retour de raid),
 * combien ont été rapportés sur combien il en faut, et pour quoi ; « ARC » s'il se trouve sur les ARC.
 */
@Composable
private fun LigneATrouver(vm: CarnetViewModel, a: ObjetATrouver, onClick: () -> Unit) {
    val objet = vm.catalogue.objet(a.objetId)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconeObjet(a.objetId, objet?.rarete, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(vm.catalogue.nomObjet(a.objetId), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${a.trouves}/${a.voulu} · " + a.pour.joinToString(" · ") { it.libelle },
                style = MaterialTheme.typography.labelMedium,
                color = if (a.trouve) Signal.Vert else Signal.TexteDoux,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (objet != null && "ARC" in objet.lieux) {
            Text(
                "ARC",
                style = MaterialTheme.typography.labelSmall,
                color = Signal.Rouge,
                modifier = Modifier.border(1.dp, Signal.Rouge, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (a.trouve) {
            Icon(Icons.Filled.Check, contentDescription = "Trouvé", tint = Signal.Vert, modifier = Modifier.size(24.dp))
        } else {
            Text(
                "${a.reste}",
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = Signal.Or,
                modifier = Modifier.widthIn(min = 28.dp).semantics { contentDescription = "${a.reste} à trouver" },
                textAlign = TextAlign.End,
            )
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
