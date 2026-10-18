package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.Sortie
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.ui.theme.Signal

@Composable
fun EcranObjets(vm: CarnetViewModel, recherche: String, onRecherche: (String) -> Unit, ouvrir: (String) -> Unit) {
    // Sans recherche : tout le catalogue, par ordre alphabétique.
    val resultats = remember(recherche) {
        if (recherche.isBlank()) vm.catalogue.tous else vm.catalogue.rechercher(recherche, limite = 200)
    }

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
                Text(
                    when {
                        recherche.isBlank() -> "${pluriel(resultats.size, "objet", "objets")} par ordre alphabétique. Touche un objet\u00a0: sa fiche te dit s'il faut le garder, le recycler ou le vendre."
                        resultats.isEmpty() -> "Aucun objet ne correspond."
                        else -> pluriel(resultats.size, "objet trouvé", "objets trouvés")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
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

/**
 * Fiche d'un objet : réserve, verdict, valeur, recyclage, et comment l'obtenir (butin, commerçants,
 * fabrication, recyclage d'autres objets, récompenses). [ouvrirZone] ouvre le plan sur une zone de butin.
 */
@Composable
fun EcranFiche(
    vm: CarnetViewModel,
    objetId: String,
    onRetour: () -> Unit,
    ouvrir: (String) -> Unit = {},
    ouvrirZone: (carteId: String, zone: String) -> Unit = { _, _ -> },
) {
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
        CommentLObtenir(vm, objet, ouvrir, ouvrirZone)
        Spacer(Modifier.height(8.dp))
    }
}

/** Tout ce que les données du jeu disent sur la façon d'obtenir l'objet, du butin aux récompenses. */
@Composable
private fun CommentLObtenir(vm: CarnetViewModel, objet: Objet, ouvrir: (String) -> Unit, ouvrirZone: (String, String) -> Unit) {
    val o = objet.obtention
    val recyclage = vm.catalogue.sourcesRecyclage(objet.id)
    if (objet.lieux.isNotEmpty() || o.cartes.isNotEmpty() || o.zones.isNotEmpty()) {
        Section("Où le trouver") {
            if (objet.lieux.isNotEmpty()) {
                Text("Dans les zones\u00a0: ${objet.lieux.joinToString(" · ")}", style = MaterialTheme.typography.bodyLarge)
            }
            if (o.cartes.isNotEmpty()) {
                SousTitreFiche("Meilleures cartes")
                o.cartes.forEach { c -> LigneIndice(vm.catalogue.carte(c.carte)?.nom ?: Catalogue.NOM_A_VERIFIER, null, c.indice) }
            }
            if (o.zones.isNotEmpty()) {
                SousTitreFiche("Meilleures zones")
                o.zones.forEach { z ->
                    LigneIndice(z.zone, vm.catalogue.carte(z.carte)?.nom, z.indice, onClick = { ouvrirZone(z.carte, z.zone) })
                }
                Text(
                    "Classement d'après les tables de butin du jeu (condition normale), 100 pour le meilleur endroit. " +
                        "Les taux de drop exacts ne sont pas publiés\u00a0: le jeu tire le butin sur ses serveurs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
        }
    }
    if (o.achats.isNotEmpty()) {
        Section("Chez les commerçants") {
            o.achats.forEach { a ->
                Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val prix = buildList {
                        if (a.pieces > 0) add("${a.pieces} pièces")
                        if (a.valeurObjets > 0) add("des objets valant ${a.valeurObjets} pièces")
                    }.joinToString(" + ")
                    val pour = if (a.quantite > 1) "${a.quantite} pour " else ""
                    Text("${a.commercant}${if (prix.isNotEmpty()) "\u00a0: $pour$prix" else ""}", style = MaterialTheme.typography.bodyLarge)
                    a.objets.forEach { LigneQuantite(vm, it.id, it.quantite) }
                    a.limite?.let { n ->
                        Text(
                            if (a.parJour) "$n par jour au plus" else pluriel(n, "achat possible", "achats possibles"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
        }
    }
    if (o.fabrications.isNotEmpty()) {
        Section("Fabrication") {
            o.fabrications.forEach { f ->
                Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val ou = f.etabli?.let { e -> f.niveau?.let { "$e · niveau $it" } ?: e } ?: "Pendant un raid"
                    Text(if (f.quantite > 1) "$ou (en donne ${f.quantite})" else ou, style = MaterialTheme.typography.bodyLarge)
                    f.plan?.let { Text("Plan à débloquer\u00a0: $it", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux) }
                    f.ingredients.forEach { LigneQuantite(vm, it.id, it.quantite) }
                }
            }
        }
    }
    if (recyclage.isNotEmpty()) {
        Section("En recyclant") {
            recyclage.take(6).forEach { q ->
                Surface(onClick = { ouvrir(q.id) }, color = Signal.Fond, shape = RoundedCornerShape(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconeObjet(q.id, vm.catalogue.objet(q.id)?.rarete, 32.dp)
                        Text(vm.catalogue.nomObjet(q.id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("en donne ${q.quantite}", style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
                    }
                }
            }
            if (recyclage.size > 6) {
                Text("Et ${pluriel(recyclage.size - 6, "autre objet", "autres objets")}.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
    }
    if (o.recompenses.isNotEmpty()) {
        Section("En récompense") {
            o.recompenses.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun SousTitreFiche(texte: String) {
    Text(texte, style = MaterialTheme.typography.titleSmall, color = Signal.Or, modifier = Modifier.padding(top = 6.dp))
}

/** Un endroit et son indice (1 à 100), avec une barre ; cliquable pour ouvrir le plan. */
@Composable
private fun LigneIndice(nom: String, sousTitre: String?, indice: Int, onClick: (() -> Unit)? = null) {
    Surface(onClick = { onClick?.invoke() }, enabled = onClick != null, color = Signal.Fond, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nom, style = MaterialTheme.typography.bodyLarge)
                    sousTitre?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux) }
                }
                Text("$indice", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Signal.Or)
                if (onClick != null) Text("  Plan ›", style = MaterialTheme.typography.labelMedium, color = Signal.Cyan)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Signal.Releve)) {
                Box(Modifier.fillMaxWidth(indice / 100f).height(4.dp).background(Signal.Or))
            }
        }
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
