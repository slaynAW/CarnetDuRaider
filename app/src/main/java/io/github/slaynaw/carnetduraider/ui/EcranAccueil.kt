package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.CaseBuild
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.ui.theme.Prompt
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.time.LocalTime

/**
 * Accueil : les cinq sections d'un coup d'œil, sans faire défiler. En tête, le prochain raid (la carte, ses
 * deux meilleures zones), puis la frise des conditions de la carte (en cours et à venir), et trois tuiles :
 * objets à garder, objectifs en cours, build préféré. Chaque bloc mène à son onglet.
 */
@Composable
fun EcranAccueil(
    vm: CarnetViewModel,
    aller: (Onglet) -> Unit,
    nouvelleVersion: String?,
    /** Calendrier officiel des conditions de la carte, ou `null` s'il n'a pas encore été lu. */
    calendrier: Calendrier?,
    afficherMiseAJour: () -> Unit,
) {
    val donnees = vm.catalogue.donnees
    val heure = LocalTime.now().hour
    val salutation = if (heure >= 18 || heure < 5) "Bonsoir, Raider" else "Bonjour, Raider"

    // Le bandeau se replie quand on fait défiler, comme dans les autres onglets.
    EcranDeSection(Onglet.ACCUEIL, salutation) { defilement, haut ->
        LazyColumn(Modifier.fillMaxSize(), state = defilement, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = haut + 14.dp, bottom = 16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (nouvelleVersion != null) {
                        Surface(onClick = afficherMiseAJour, color = Signal.Releve, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Signal.Or)
                                Column {
                                    Text("Mise à jour disponible", style = MaterialTheme.typography.titleSmall)
                                    Text("Version $nouvelleVersion : touche pour voir les nouveautés", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                                }
                            }
                        }
                    }

                    ProchainRaid(vm, aller)

                    // Quatre tuiles, deux par ligne et de même hauteur : objets, cartes, objectifs, build.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val tuile = Modifier.weight(1f).fillMaxHeight()
                        TuileObjets(vm, tuile, onClick = { aller(Onglet.OBJETS) })
                        TuileCartes(vm, calendrier, tuile, onClick = { aller(Onglet.CARTES) })
                    }
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val tuile = Modifier.weight(1f).fillMaxHeight()
                        TuileObjectifs(vm, tuile, onClick = { aller(Onglet.OBJECTIFS) })
                        TuileBuild(vm, tuile, onClick = { aller(Onglet.BUILDS) })
                    }

                    Text(
                        "Données du jeu ${donnees.versionJeu}, noms français officiels uniquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.TexteDiscret,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Où aller au prochain raid pour trouver ce qui manque : la carte, combien d'objets on y trouve (et combien
 * tombent des ARC), puis ses deux meilleures zones avec leur nombre d'objets bien en vue.
 */
@Composable
private fun ProchainRaid(vm: CarnetViewModel, aller: (Onglet) -> Unit) {
    val restants = vm.aTrouver().filter { !it.trouve }
    val conseil = remember(restants.map { it.objetId }) { vm.conseilRaid() }
    val premiere = conseil.cartes.firstOrNull()
    // La carte où aller, sur sa capture en jeu (comme « Meilleure carte »), sinon sur le rouge du Raid.
    val fond = rememberFondCarte(premiere?.carteId)
    val surRouge = premiere != null && fond == null
    Surface(
        onClick = { aller(if (restants.isEmpty() && vm.objectifs.isEmpty()) Onglet.OBJECTIFS else Onglet.RAID) },
        color = when {
            premiere == null -> Signal.Surface
            fond != null -> Signal.Fond
            else -> Signal.Rouge
        },
        contentColor = if (surRouge) Signal.SurCouleur else Signal.Texte,
        // Sur toute la largeur de l'écran, par-delà les marges de la liste.
        modifier = Modifier.pleineLargeur(20.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (premiere != null && fond != null) FondEnJeuAnime(fond)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Etiquette("Ton prochain raid", if (premiere != null && fond != null) Signal.Rouge else LocalContentColor.current)
                when {
                    premiere != null -> {
                        Text("Va sur ${vm.catalogue.carte(premiere.carteId)?.nom ?: Catalogue.NOM_A_VERIFIER}", style = MaterialTheme.typography.headlineSmall)
                        val surLesArc = restants.count { "ARC" in (vm.catalogue.objet(it.objetId)?.lieux ?: emptyList()) }
                        Text(
                            buildString {
                                append("${premiere.objets.size} de tes ${pluriel(restants.size, "objet", "objets")} à trouver")
                                if (surLesArc > 0) append(" · $surLesArc sur les ARC détruits")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val zones = premiere.zones.take(2)
                        if (zones.isNotEmpty()) {
                            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(LocalContentColor.current.copy(alpha = 0.16f)))
                            zones.forEach { z ->
                                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(z.zone, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    PastilleNombre(pluriel(z.objets.size, "objet", "objets"), surRouge)
                                }
                            }
                        }
                    }
                    restants.isNotEmpty() -> {
                        Text("Rien dans le butin des cartes", style = MaterialTheme.typography.titleMedium)
                        Text("Ce qu'il te faut s'obtient autrement : regarde l'onglet Raid.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                    }
                    vm.objectifs.isNotEmpty() -> {
                        Text("Rien à chercher en raid", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tes objectifs en cours ne demandent aucun objet : valide-les quand c'est fait en jeu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                    else -> {
                        Text("Rien à chercher pour l'instant", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Indique ce que tu as en cours (quêtes, ateliers, projets, expéditions) : l'appli te dira où aller.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
            // Les quatre bandes du logo en pied.
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)) {
                listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge).forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
            }
        }
    }
}

/** Le nombre d'objets d'une zone, en pastille rouge bien lisible sur la capture (ou sombre sur le rouge). */
@Composable
private fun PastilleNombre(texte: String, surRouge: Boolean) {
    val forme = RoundedCornerShape(50)
    Text(
        texte,
        style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
        color = if (surRouge) Signal.SurCouleur else Signal.Texte,
        maxLines = 1,
        modifier = Modifier
            .background(if (surRouge) Signal.SurCouleur.copy(alpha = 0.14f) else Signal.Rouge.copy(alpha = 0.30f), forme)
            .border(1.dp, if (surRouge) Signal.SurCouleur.copy(alpha = 0.35f) else Signal.Rouge, forme)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Ce qui est à garder (objectifs et builds), l'objet le plus demandé encore à trouver et ceux qui tombent des ARC. */
@Composable
private fun TuileObjets(vm: CarnetViewModel, modifier: Modifier, onClick: () -> Unit) {
    val restants = vm.aTrouver().filter { !it.trouve }.sortedByDescending { it.reste }
    val aGarder = remember(restants, vm.builds) { vm.catalogue.tous.count { vm.verdict(it.id) !is Verdict.Inutile } }
    val surLesArc = restants.count { "ARC" in (vm.catalogue.objet(it.objetId)?.lieux ?: emptyList()) }
    TuileSection(Onglet.OBJETS, modifier, onClick) {
        Chiffre("$aGarder", "à garder")
        val premier = restants.firstOrNull()
        if (premier == null) {
            Text("Garder, recycler ou vendre ?", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.padding(top = 10.dp))
        } else {
            SousTitre("Le plus demandé")
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                IconeObjet(premier.objetId, vm.catalogue.objet(premier.objetId)?.rarete, 24.dp)
                Text(vm.catalogue.nomObjet(premier.objetId), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("×${premier.reste}", style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 13.sp), color = Signal.Or)
            }
            if (surLesArc > 0) LignePuce("${pluriel(surLesArc, "objet", "objets")} sur les ARC détruits", Signal.Or)
        }
    }
}

/**
 * Les conditions de la carte : celle en cours (et ce qu'il lui reste, en barre), puis la suivante, avec une
 * cloche si elle a une alerte. Avant la lecture du calendrier officiel, la tuile le dit.
 */
@Composable
private fun TuileCartes(vm: CarnetViewModel, calendrier: Calendrier?, modifier: Modifier, onClick: () -> Unit) {
    val t = maintenant(30_000)
    val reglages by vm.alertes.reglages.collectAsState()
    TuileSection(Onglet.CARTES, modifier, onClick) {
        if (calendrier == null) {
            Text("Conditions de la carte : lecture du calendrier officiel…", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.padding(top = 10.dp))
            return@TuileSection
        }
        val enCours = calendrier.enCours(vm.region, t).firstOrNull()
        val suivante = calendrier.aVenir(vm.region, t, 6 * Calendrier.UNE_HEURE).firstOrNull()
        if (enCours == null && suivante == null) {
            Text("Rien d'annoncé dans les prochaines heures.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.padding(top = 10.dp))
            return@TuileSection
        }
        if (enCours != null) {
            SousTitre("Maintenant")
            LigneCondition(enCours, "encore ${duree(enCours.fin - t)}", suivie = enCours.conditionId in reglages.conditions)
            Barre(((t - enCours.debut).toFloat() / (enCours.fin - enCours.debut).coerceAtLeast(1)), Signal.Cyan, Modifier.padding(top = 6.dp), epaisseur = 3.dp)
        }
        if (suivante != null) {
            SousTitre("Ensuite")
            LigneCondition(suivante, "dans ${duree(suivante.debut - t)}", suivie = suivante.conditionId in reglages.conditions)
        }
    }
}

@Composable
private fun LigneCondition(c: Creneau, quand: String, suivie: Boolean) {
    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            c.condition,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = if (c.majeure) Signal.Rouge else Signal.Texte,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (suivie) Icon(Icons.Filled.Notifications, contentDescription = "Alerte", tint = Signal.Cyan, modifier = Modifier.size(12.dp))
    }
    Text("${c.carte} · $quand", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Les objectifs en cours par sorte (quêtes, ateliers, projets, expéditions), et celui qui est le plus près d'être fini. */
@Composable
private fun TuileObjectifs(vm: CarnetViewModel, modifier: Modifier, onClick: () -> Unit) {
    val objectifs = vm.objectifs
    val liste = vm.listeDeCourses()
    TuileSection(Onglet.OBJECTIFS, modifier, onClick) {
        Chiffre("${objectifs.size}", "en cours")
        if (objectifs.isEmpty()) {
            Text("Touche pour indiquer tes quêtes, ateliers, projets ou expéditions", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.padding(top = 10.dp))
            return@TuileSection
        }
        val quetes = objectifs.count { it is Objectif.QueteATerminer }
        val ateliers = objectifs.count { it is Objectif.NiveauDEtabli }
        val projets = objectifs.filterIsInstance<Objectif.EtapeDeProjet>()
        val expeditions = projets.count { vm.catalogue.projet(it.projetId)?.expedition == true }
        Text(
            listOfNotNull(
                quetes.takeIf { it > 0 }?.let { pluriel(it, "quête", "quêtes") },
                ateliers.takeIf { it > 0 }?.let { pluriel(it, "atelier", "ateliers") },
                (projets.size - expeditions).takeIf { it > 0 }?.let { pluriel(it, "projet", "projets") },
                expeditions.takeIf { it > 0 }?.let { pluriel(it, "expédition", "expéditions") },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = Signal.TexteDoux,
            modifier = Modifier.padding(top = 3.dp),
        )
        // Le plus près d'être fini : la plus grande part des objets demandés déjà rapportés.
        val proche = liste.lignes.flatMap { it.pour }.groupBy { it.libelle }
            .map { (libelle, besoins) -> Triple(libelle, besoins.sumOf { minOf(it.apporte, it.quantite) }, besoins.sumOf { it.quantite }) }
            .filter { it.third > 0 && it.second < it.third }
            // À égalité, celui à qui il manque le moins d'objets.
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second.toDouble() / it.third }.thenBy { it.third - it.second })
            .firstOrNull()
        if (proche != null) {
            SousTitre("Le plus proche")
            Text(proche.first, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Barre(proche.second.toFloat() / proche.third, Signal.Vert, Modifier.weight(1f), epaisseur = 3.dp)
                Text("${proche.second}/${proche.third}", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
    }
}

/** Le build préféré (sinon le premier rempli) : son nom, ses cases remplies et les matériaux pour le fabriquer. */
@Composable
private fun TuileBuild(vm: CarnetViewModel, modifier: Modifier, onClick: () -> Unit) {
    val rang = vm.builds.indexOfFirst { it.prefere && !it.vide }.takeIf { it >= 0 } ?: vm.builds.indexOfFirst { !it.vide }
    val build = vm.builds.getOrNull(rang)
    TuileSection(Onglet.BUILDS, modifier, onClick) {
        if (build == null) {
            Text("Aucun build", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
            Text("Touche pour préparer ton équipement : ses objets passent en « Garder ».", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.padding(top = 4.dp))
            return@TuileSection
        }
        Text(
            vm.outilsBuilds.nom(build, rang) + if (build.prefere) " ★" else "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text("${build.objets.size} cases sur ${CaseBuild.entries.size}", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
        val materiaux = remember(build) { vm.outilsBuilds.aFabriquer(build) }
        if (materiaux.isNotEmpty()) {
            SousTitre("Pour le fabriquer")
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                materiaux.take(3).forEach { (id, _) -> IconeObjet(id, vm.catalogue.objet(id)?.rarete, 24.dp) }
            }
            Text("${pluriel(materiaux.size, "matériau", "matériaux")} à garder", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

/** Petit titre en capitales discrètes à l'intérieur d'une tuile (« LE PLUS DEMANDÉ », « ENSUITE »…). */
@Composable
private fun SousTitre(texte: String) {
    PetiteEtiquette(texte, Signal.TexteDiscret, Modifier.padding(top = 10.dp))
}

/** Une ligne précédée d'un petit carré de la couleur de la section. */
@Composable
private fun LignePuce(texte: String, couleur: Color) {
    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(4.dp).background(couleur, RoundedCornerShape(1.dp)))
        Text(texte, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}

/** Une tuile de l'accueil : fin trait de la couleur de la section en haut, son icône et son nom en capitales. */
@Composable
private fun TuileSection(
    onglet: Onglet,
    modifier: Modifier,
    onClick: () -> Unit,
    droite: @Composable () -> Unit = {},
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Box {
            Box(Modifier.fillMaxWidth().height(2.dp).background(onglet.couleur))
            Column(Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(onglet.icone, contentDescription = null, tint = onglet.couleur, modifier = Modifier.size(14.dp))
                    PetiteEtiquette(onglet.titre, onglet.couleur, Modifier.weight(1f))
                    droite()
                }
                contenu()
            }
        }
    }
}

/** Étiquette en capitales plus petite que [Etiquette], pour les tuiles étroites. */
@Composable
private fun PetiteEtiquette(texte: String, couleur: Color, modifier: Modifier = Modifier) {
    Text(
        texte.uppercase(),
        style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, lineHeight = 12.sp, letterSpacing = 1.1.sp),
        color = couleur,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Un grand chiffre et son unité (« 7 à garder »). */
@Composable
private fun Chiffre(nombre: String, unite: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(nombre, style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp), modifier = Modifier.alignByBaseline())
        Text(unite, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.alignByBaseline())
    }
}

/** « 1 objet », « 3 objets » (le nombre est inclus). */
fun pluriel(n: Int, singulier: String, pluriel: String): String = "$n ${if (n > 1) pluriel else singulier}"
