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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Besoin
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.Sortie
import io.github.slaynaw.carnetduraider.core.UsageBuild
import io.github.slaynaw.carnetduraider.core.UsagesObjet
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
                        recherche.isBlank() -> "${pluriel(resultats.size, "objet", "objets")} par ordre alphabétique. Touche un objet pour savoir à quoi il sert et où le trouver."
                        resultats.isEmpty() -> "Aucun objet ne correspond."
                        else -> pluriel(resultats.size, "objet trouvé", "objets trouvés")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
            }
        }
        items(resultats, key = { it.id }) { objet ->
            LigneObjet(objet, vm.verdict(objet.id), onClick = { ouvrir(objet.id) })
        }
    }
}

@Composable
private fun LigneObjet(objet: Objet, verdict: Verdict, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconeObjet(objet.id, objet.rarete, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(objet.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val details = listOfNotNull(objet.rarete?.libelle, objet.categorie).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            PuceVerdict(verdict)
        }
    }
}

/** Le verdict en un mot, pour la liste : Garder (objectifs), Build, Vendre ou Recycler. */
@Composable
fun PuceVerdict(verdict: Verdict) {
    val (texte, fond, couleur) = when (verdict) {
        is Verdict.Garder -> Triple("Garder", Signal.Or, Signal.SurCouleur)
        is Verdict.PourBuild -> Triple("Build", Signal.Violet, Signal.SurCouleur)
        is Verdict.Inutile -> Triple(if (verdict.conseil == Sortie.VENDRE) "Vendre" else "Recycler", Signal.Releve, Signal.TexteDoux)
    }
    Puce(texte, fond = fond, couleur = couleur)
}

/**
 * Fiche d'un objet, dans l'ordre où le joueur se pose les questions : le verdict (garder, vendre ou
 * recycler), à quoi il peut servir, où le trouver, puis ce qu'il rapporte. [ouvrirZone] ouvre le plan sur une zone de butin.
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

        val usages = vm.usagesDe(objet.id)
        CarteVerdict(vm.verdict(objet.id), objet, usages, vm)
        SectionUsages(vm, usages, ouvrir)
        CommentLObtenir(vm, objet, ouvrir, ouvrirZone)
        Section("Vente et recyclage") {
            Text("Se vend ${objet.valeur} pièces l'unité.", style = MaterialTheme.typography.bodyLarge)
            if (objet.recyclage.isEmpty()) {
                Text("Ne se recycle pas.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
            } else {
                Text(
                    "Recyclage en (matériaux\u00a0: ${vm.catalogue.valeurRecyclage(objet)} pièces)\u00a0:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                objet.recyclage.forEach { LigneQuantite(vm, it.id, it.quantite) }
            }
        }
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
                o.zones.take(5).forEach { z ->
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

/** Ce que dit la carte du verdict : titre, explication, couleur, objectifs et builds concernés, note. */
private data class ContenuVerdict(
    val titre: String,
    val texte: String,
    val fond: Color,
    val pour: List<Besoin> = emptyList(),
    val builds: List<UsageBuild> = emptyList(),
    val note: String? = null,
)

@Composable
private fun CarteVerdict(verdict: Verdict, objet: Objet, usages: UsagesObjet, vm: CarnetViewModel) {
    val valeurRecyclage = vm.catalogue.valeurRecyclage(objet)
    fun sortie(sortie: Sortie): String = when {
        sortie == Sortie.RECYCLER -> "recycle-le\u00a0: ses matériaux valent $valeurRecyclage pièces, plus que l'objet (${objet.valeur})."
        objet.recyclage.isEmpty() -> "vends-le\u00a0: il vaut ${objet.valeur} pièces et ne se recycle pas."
        else -> "vends-le\u00a0: il vaut ${objet.valeur} pièces, plus que ses matériaux ($valeurRecyclage)."
    }
    val c = when (verdict) {
        is Verdict.Garder -> ContenuVerdict(
            "Garde-le",
            "Il en faut encore ${verdict.manquant} pour tes objectifs en cours.",
            Signal.Or,
            verdict.pour,
            verdict.builds,
        )
        is Verdict.PourBuild -> ContenuVerdict(
            "Garde-le",
            if (verdict.builds.all { it.direct }) "Il fait partie de tes builds." else "Il sert à fabriquer ce qu'il y a dans tes builds.",
            Signal.Violet,
            builds = verdict.builds,
        )
        is Verdict.Inutile -> ContenuVerdict(
            if (verdict.conseil == Sortie.VENDRE) "Vends-le" else "Recycle-le",
            "Aucun objectif en cours ni aucun build n'en a besoin\u00a0: ${sortie(verdict.conseil)}",
            Signal.Releve,
            note = if (usages.aucun) null else "Il peut quand même servir\u00a0: ${usages.resume().joinToString(", ")}. Le détail est plus bas.",
        )
    }
    val texte = if (c.fond == Signal.Releve) Signal.Texte else Signal.SurCouleur
    Surface(color = c.fond, contentColor = texte, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("VERDICT", style = MaterialTheme.typography.labelSmall)
            Text(c.titre, style = MaterialTheme.typography.headlineLarge)
            Text(c.texte, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (c.pour.isNotEmpty() || c.builds.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = texte.copy(alpha = 0.3f))
                c.pour.forEach { besoin ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(besoin.libelle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${minOf(besoin.apporte, besoin.quantite)}/${besoin.quantite}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                c.builds.forEach { b ->
                    Text(
                        if (b.direct) "Build «\u00a0${b.build}\u00a0»" else "Build «\u00a0${b.build}\u00a0»\u00a0: pour ${vm.catalogue.nomObjet(b.objetId)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** À quoi peut servir l'objet dans le jeu : description officielle, puis tout ce qui le demande. */
@Composable
private fun SectionUsages(vm: CarnetViewModel, u: UsagesObjet, ouvrir: (String) -> Unit) {
    val ut = u.utilisations
    Section("À quoi il peut servir") {
        u.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (u.aucun) {
            Text("Rien d'autre que la vente ou le recyclage, d'après les données du jeu.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
        }
        if (ut.cle) LigneUsage("Clé", "Elle ouvre une porte ou une salle\u00a0: voir la description.")
        if (ut.equipement) LigneUsage("Équipement", "Il s'utilise en raid.")
        if (u.ameliorations.isNotEmpty()) {
            LigneUsage("Établis", u.ameliorations.joinToString(" · ") { "${it.etabli.nom} niveau ${it.niveau} (${it.quantite})" })
        }
        if (u.quetes.isNotEmpty()) {
            LigneUsage("Quêtes", u.quetes.joinToString(" · ") { "«\u00a0${it.quete.nom}\u00a0» (${it.quantite})" })
        }
        if (u.projets.isNotEmpty()) {
            LigneUsage("Projets", u.projets.joinToString(" · ") { "${it.projet.nom}, ${vm.planificateur.nomEtape(it.projet, it.etape)} (${it.quantite})" })
        }
        if (ut.fabrication.isNotEmpty()) {
            SousTitreFiche("Sert à fabriquer")
            ut.fabrication.take(6).forEach { f ->
                Surface(onClick = { ouvrir(f.objet) }, color = Signal.Fond, shape = RoundedCornerShape(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconeObjet(f.objet, vm.catalogue.objet(f.objet)?.rarete, 32.dp)
                        Text(vm.catalogue.nomObjet(f.objet), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            f.etabli?.let { e -> f.niveau?.let { "$e · niv. $it" } ?: e } ?: "en raid",
                            style = MaterialTheme.typography.labelMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
            if (ut.fabrication.size > 6) {
                Text("Et ${pluriel(ut.fabrication.size - 6, "autre objet", "autres objets")}.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
        if (ut.amplification.isNotEmpty()) {
            LigneUsage("Amplification", ut.amplification.joinToString(" · ") { vm.catalogue.nomObjet(it) })
        }
        if (ut.aApprendre) {
            val quoi = ut.debloque.map { vm.catalogue.nomObjet(it) } + ut.meubles
            LigneUsage("À apprendre", if (quoi.isEmpty()) "Il débloque une fabrication." else "Il débloque\u00a0: ${quoi.joinToString(", ")}.")
        }
        if (ut.apparence) LigneUsage("Apparence", "Une apparence à débloquer.")
        if (ut.recherches.isNotEmpty()) LigneUsage("Station de recherche", abrege(ut.recherches))
        if (ut.avantPoste.isNotEmpty()) LigneUsage("Avant-poste", abrege(ut.avantPoste))
        if (ut.echanges.isNotEmpty()) {
            LigneUsage("Échanges", ut.echanges.take(4).joinToString(" · ") { "${it.commercant}\u00a0: ${it.donne} contre ${it.quantite} × ${vm.catalogue.nomObjet(it.objet)}" } +
                if (ut.echanges.size > 4) " · et ${ut.echanges.size - 4} autres" else "")
        }
        if (ut.echangesValeur.isNotEmpty()) {
            LigneUsage("Échanges", "${ut.echangesValeur.joinToString(", ")} l'accepte en échange, au prix de sa valeur.")
        }
    }
}

/** « A, B, C et 4 autres ». */
private fun abrege(noms: List<String>, nombre: Int = 3): String =
    noms.take(nombre).joinToString(", ") + if (noms.size > nombre) " et ${noms.size - nombre} autres" else ""

@Composable
private fun LigneUsage(titre: String, texte: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(titre, style = MaterialTheme.typography.titleSmall, color = Signal.Cyan)
        Text(texte, style = MaterialTheme.typography.bodyMedium)
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
