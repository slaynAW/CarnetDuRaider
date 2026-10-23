package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.CaseBuild
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Creneau
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.time.LocalTime

/**
 * Accueil : ce qui compte maintenant, en un coup d'œil. Garder ou vendre un objet, où aller au prochain
 * raid, les conditions de la carte en cours, et où en sont les objectifs. Chaque bloc mène à son onglet.
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
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (nouvelleVersion != null) {
                        Surface(onClick = afficherMiseAJour, color = Signal.Releve, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Signal.Or)
                                Column {
                                    Text("Mise à jour disponible", style = MaterialTheme.typography.titleSmall)
                                    Text("Version $nouvelleVersion\u00a0: touche pour voir les nouveautés", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                                }
                            }
                        }
                    }

                    // La question la plus fréquente en jeu.
                    Surface(
                        onClick = { aller(Onglet.OBJETS) },
                        color = Signal.Surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Or)
                            Column {
                                Text("Garder, recycler ou vendre\u00a0?", style = MaterialTheme.typography.titleSmall)
                                Text("Cherche un objet…", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                            }
                        }
                    }

                    ProchainRaid(vm, aller)

                    if (calendrier != null) BlocConditions(vm, calendrier, onClick = { aller(Onglet.CARTES) })

                    MesObjectifs(vm, onClick = { aller(Onglet.OBJECTIFS) })

                    MonBuild(vm, onClick = { aller(Onglet.BUILDS) })

                    Text(
                        "Données du jeu ${donnees.versionJeu}, noms français officiels uniquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.TexteDiscret,
                    )
                }
            }
        }
    }
}

/** Où aller au prochain raid pour trouver ce qui manque (ou, s'il ne manque rien, choisir ses objectifs). */
@Composable
private fun ProchainRaid(vm: CarnetViewModel, aller: (Onglet) -> Unit) {
    val restants = vm.aTrouver().filter { !it.trouve }
    val conseil = remember(restants.map { it.objetId }) { vm.conseilRaid() }
    val premiere = conseil.cartes.firstOrNull()
    // La carte où aller, sur sa capture en jeu (comme « Meilleure carte »), sinon sur le rouge du Raid.
    val fond = rememberFondCarte(premiere?.carteId)
    Surface(
        onClick = { aller(if (restants.isEmpty() && vm.objectifs.isEmpty()) Onglet.OBJECTIFS else Onglet.RAID) },
        color = when {
            premiere == null -> Signal.Surface
            fond != null -> Signal.Fond
            else -> Signal.Rouge
        },
        contentColor = if (premiere != null && fond == null) Signal.SurCouleur else Signal.Texte,
        shape = FormeCoupee,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            if (premiere != null && fond != null) FondEnJeu(fond)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Etiquette("Ton prochain raid", if (premiere != null && fond != null) Signal.Rouge else LocalContentColor.current)
                when {
                    premiere != null -> {
                        Text("Va sur ${vm.catalogue.carte(premiere.carteId)?.nom ?: Catalogue.NOM_A_VERIFIER}", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            buildString {
                                append("${premiere.objets.size} de tes ${pluriel(restants.size, "objet", "objets")} à trouver")
                                premiere.zones.firstOrNull()?.let { append(" · surtout ${it.zone}") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (conseil.parcours.size > 1) {
                            Text(
                                "Puis " + conseil.parcours.drop(1).joinToString(", ") { vm.catalogue.carte(it.carteId)?.nom ?: Catalogue.NOM_A_VERIFIER } + " pour tout trouver.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    restants.isNotEmpty() -> {
                        Text("Rien dans le butin des cartes", style = MaterialTheme.typography.titleMedium)
                        Text("Ce qu'il te faut s'obtient autrement\u00a0: regarde l'onglet Raid.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                    }
                    vm.objectifs.isNotEmpty() -> {
                        Text("Rien à chercher en raid", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tes objectifs en cours ne demandent aucun objet\u00a0: valide-les quand c'est fait en jeu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                    else -> {
                        Text("Rien à chercher pour l'instant", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Indique ce que tu as en cours (quêtes, ateliers, projets, expéditions)\u00a0: l'appli te dira où aller.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
        }
    }
}

/** Où en sont les objectifs en cours. */
@Composable
private fun MesObjectifs(vm: CarnetViewModel, onClick: () -> Unit) {
    val liste = vm.listeDeCourses()
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Objectifs", style = MaterialTheme.typography.titleSmall, color = Signal.Vert)
            if (vm.objectifs.isEmpty()) {
                Text("Rien en cours. Touche ici pour indiquer tes quêtes, ateliers, projets ou expéditions en cours.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
            } else {
                Text(
                    "${pluriel(vm.objectifs.size, "objectif en cours", "objectifs en cours")} · ${pluriel(liste.objetsATrouver, "objet", "objets")} à trouver",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Barre(liste.progression.toFloat(), Signal.Vert)
            }
        }
    }
}

/** Conditions de la carte en cours et à venir, avec leur minuteur à la seconde. */
@Composable
private fun BlocConditions(vm: CarnetViewModel, calendrier: Calendrier, onClick: () -> Unit) {
    val t = maintenant(1_000)
    val enCours = calendrier.enCours(vm.region, t)
    val suivantes = calendrier.aVenir(vm.region, t, 6 * Calendrier.UNE_HEURE).take(3 - enCours.size.coerceAtMost(2))
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Conditions de la carte", style = MaterialTheme.typography.titleSmall, color = Signal.Cyan)
            if (enCours.isEmpty() && suivantes.isEmpty()) {
                Text("Rien d'annoncé dans les prochaines heures.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
            enCours.forEach { LigneMinuteur(it, "encore ${chrono(it.fin - t)}", Signal.Cyan) }
            suivantes.forEach { LigneMinuteur(it, "dans ${chrono(it.debut - t)}", Signal.Or) }
        }
    }
}

@Composable
private fun LigneMinuteur(c: Creneau, minuteur: String, couleur: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.condition, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (c.majeure) Signal.Rouge else Signal.Texte)
            Text(c.carte, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
        }
        Text(minuteur, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = couleur)
    }
}

/** « 1 objet », « 3 objets » (le nombre est inclus). */
fun pluriel(n: Int, singulier: String, pluriel: String): String = "$n ${if (n > 1) pluriel else singulier}"

/**
 * Le build préféré (sinon le premier rempli), en un coup d'œil : son nom, ses objets et ce qu'il faut pour le
 * fabriquer ; sans build, une invitation à en préparer un.
 */
@Composable
private fun MonBuild(vm: CarnetViewModel, onClick: () -> Unit) {
    val rang = vm.builds.indexOfFirst { it.prefere && !it.vide }.takeIf { it >= 0 } ?: vm.builds.indexOfFirst { !it.vide }
    val build = vm.builds.getOrNull(rang)
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (build?.prefere == true) "Mon build préféré" else "Mes builds", style = MaterialTheme.typography.titleSmall, color = Signal.Violet)
            if (build == null) {
                Text("Aucun build. Touche ici pour préparer ton équipement\u00a0: ses objets passent en «\u00a0Garder\u00a0».", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
            } else {
                Text(vm.outilsBuilds.nom(build, rang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CaseBuild.entries.mapNotNull { build.objets[it] }.take(7).forEach { id -> IconeObjet(id, vm.catalogue.objet(id)?.rarete, 36.dp) }
                }
                val materiaux = vm.outilsBuilds.aFabriquer(build).size
                if (materiaux > 0) {
                    Text("Pour le fabriquer\u00a0: ${pluriel(materiaux, "matériau", "matériaux")} à garder", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                }
            }
        }
    }
}
