package io.github.slaynaw.carnetduraider.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Etabli
import io.github.slaynaw.carnetduraider.core.LigneDeCourses
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Quantite
import io.github.slaynaw.carnetduraider.core.Quete
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlin.math.roundToInt

/** Ce que le joueur vient de faire en jeu, à confirmer (et à déduire, ou non, de sa réserve). */
private sealed interface AConfirmer {
    data class Amelioration(val etabli: Etabli) : AConfirmer
    data class QueteTerminee(val quete: Quete) : AConfirmer
}

@Composable
fun EcranProgression(vm: CarnetViewModel, ajouterObjectif: () -> Unit, choisirQuetes: () -> Unit, ouvrir: (String) -> Unit) {
    val liste = vm.listeDeCourses()
    val enCours = vm.suivi.quetesEnCours(vm.progression)
    val autres = vm.objectifsManuels.filter { it !in vm.objectifsAutomatiques }
    var aConfirmer by remember { mutableStateOf<AConfirmer?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bandeau("Progression", Signal.Vert)
                Text("Liste de courses", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${pluriel(liste.objetsManquants, "objet", "objets")} à trouver pour le prochain niveau de tes établis et tes quêtes en cours. " +
                        "Quand tu améliores un établi ou termines une quête en jeu, coche-le ici\u00a0: la suite se met à jour toute seule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
            }
        }

        if (liste.lignes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${(liste.progression * 100).roundToInt()}\u00a0%", style = MaterialTheme.typography.headlineMedium, color = Signal.Or)
                        Text("des objets rassemblés", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Barre(liste.progression.toFloat(), Signal.Or, epaisseur = 8.dp)
                }
            }
        }

        if (!vm.quetesIndiquees) {
            item {
                Surface(color = Signal.Releve, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Où en es-tu dans les quêtes\u00a0?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Coche une seule fois les quêtes affichées en cours dans ton jeu\u00a0: l'appli en déduit toutes celles que tu as déjà terminées.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                        Button(onClick = choisirQuetes, colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur)) {
                            Text("Choisir mes quêtes en cours")
                        }
                    }
                }
            }
        }

        item { SousTitre("Atelier") }
        items(vm.catalogue.donnees.etablis.sortedBy { it.nom }, key = { "etabli-${it.id}" }) { etabli ->
            CarteEtabli(vm, etabli, ameliorer = { aConfirmer = AConfirmer.Amelioration(etabli) })
        }

        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quêtes en cours", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = choisirQuetes) { Text("Modifier", color = Signal.Vert) }
            }
        }
        if (enCours.isEmpty()) {
            item { Text("Aucune quête en cours.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret) }
        }
        items(enCours.sortedWith(compareBy({ it.objets.isEmpty() }, { it.nom })), key = { "quete-${it.id}" }) { quete ->
            CarteQuete(vm, quete, terminer = {
                if (quete.objets.isEmpty()) vm.terminerQuete(quete.id, retirerObjets = false) else aConfirmer = AConfirmer.QueteTerminee(quete)
            })
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Autres objectifs", style = MaterialTheme.typography.titleMedium)
                autres.forEach { objectif -> LigneObjectif(vm, objectif) }
                TextButton(onClick = ajouterObjectif) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Signal.Vert)
                    Text("Ajouter un objectif plus lointain", color = Signal.Vert, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        if (liste.lignes.isNotEmpty()) {
            item { SousTitre("Objets à trouver") }
        }
        items(liste.lignes, key = { it.objetId }) { ligne ->
            LigneCourses(vm, ligne, onClick = { ouvrir(ligne.objetId) })
        }
    }

    when (val action = aConfirmer) {
        is AConfirmer.Amelioration -> {
            val niveau = vm.suivi.prochainNiveau(vm.progression, action.etabli)
            if (niveau != null) {
                DialogueConsommation(
                    titre = "${action.etabli.nom} · niveau ${niveau.niveau}",
                    question = "Tu viens de construire cette amélioration\u00a0?",
                    cout = niveau.cout,
                    catalogue = vm.catalogue,
                    confirmer = { retirer ->
                        vm.ameliorer(action.etabli, retirer)
                        aConfirmer = null
                    },
                    annuler = { aConfirmer = null },
                )
            }
        }
        is AConfirmer.QueteTerminee -> DialogueConsommation(
            titre = action.quete.nom,
            question = "Tu viens de terminer cette quête\u00a0?",
            cout = action.quete.objets,
            catalogue = vm.catalogue,
            confirmer = { retirer ->
                vm.terminerQuete(action.quete.id, retirer)
                aConfirmer = null
            },
            annuler = { aConfirmer = null },
        )
        null -> Unit
    }
}

@Composable
private fun SousTitre(texte: String) {
    Text(texte, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

private fun detailCout(cout: List<Quantite>, catalogue: Catalogue): String =
    cout.joinToString(" · ") { "${it.quantite} × ${catalogue.nomObjet(it.id)}" }

@Composable
private fun CarteEtabli(vm: CarnetViewModel, etabli: Etabli, ameliorer: () -> Unit) {
    val niveau = vm.suivi.niveau(vm.progression, etabli)
    val max = vm.suivi.niveauMax(etabli)
    val prochain = vm.suivi.prochainNiveau(vm.progression, etabli)
    val suivi = etabli.id !in vm.progression.etablisIgnores
    Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(etabli.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Niveau $niveau sur $max", style = MaterialTheme.typography.labelMedium, color = if (prochain == null) Signal.Vert else Signal.TexteDoux)
                }
                FilledIconButton(
                    onClick = { vm.changerNiveau(etabli, niveau - 1) },
                    enabled = niveau > vm.suivi.niveauDeDepart(etabli),
                    modifier = Modifier.semantics { contentDescription = "Corriger\u00a0: ${etabli.nom} un niveau plus bas" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Texte),
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                FilledIconButton(
                    onClick = ameliorer,
                    enabled = prochain != null,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur),
                ) { Icon(Icons.Filled.Add, contentDescription = "Amélioration construite\u00a0: ${etabli.nom}") }
            }
            if (prochain != null) {
                Text(
                    "Niveau ${prochain.niveau}\u00a0: ${detailCout(prochain.cout, vm.catalogue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (suivi) Signal.TexteDoux else Signal.TexteDiscret,
                )
                TextButton(onClick = { vm.suivreEtabli(etabli.id, !suivi) }) {
                    Text(if (suivi) "Ne pas suivre cet établi" else "Suivre cet établi", color = if (suivi) Signal.TexteDiscret else Signal.Vert)
                }
            } else {
                Text("Niveau maximum atteint.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun CarteQuete(vm: CarnetViewModel, quete: Quete, terminer: () -> Unit) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(quete.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                quete.commercant?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux) }
                if (quete.objets.isNotEmpty()) {
                    Text(detailCout(quete.objets, vm.catalogue), style = MaterialTheme.typography.bodySmall, color = Signal.Or)
                }
            }
            FilledIconButton(
                onClick = terminer,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Vert),
            ) { Icon(Icons.Filled.Check, contentDescription = "Quête terminée\u00a0: ${quete.nom}") }
        }
    }
}

/** Confirmation d'une action faite en jeu, avec ce qu'elle a consommé dans la réserve. */
@Composable
private fun DialogueConsommation(
    titre: String,
    question: String,
    cout: List<Quantite>,
    catalogue: Catalogue,
    confirmer: (retirer: Boolean) -> Unit,
    annuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = annuler,
        containerColor = Signal.Surface,
        title = { Text(titre, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(question, style = MaterialTheme.typography.bodyMedium)
                Text("Retirer de ta réserve ce que le jeu a pris\u00a0:", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                cout.forEach { q ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconeObjet(q.id, catalogue.objet(q.id)?.rarete, 28.dp)
                        Text("${q.quantite} × ${catalogue.nomObjet(q.id)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { confirmer(true) }, colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur)) {
                Text("Oui, et retirer")
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmer(false) }) { Text("Oui, sans retirer", color = Signal.TexteDoux) }
        },
    )
}

@Composable
private fun LigneObjectif(vm: CarnetViewModel, objectif: Objectif) {
    val libelle = vm.planificateur.libelle(objectif)
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(libelle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.retirerObjectif(objectif) }) {
                Icon(Icons.Filled.Close, contentDescription = "Ne plus suivre\u00a0: $libelle", tint = Signal.TexteDoux)
            }
        }
    }
}

@Composable
private fun LigneCourses(vm: CarnetViewModel, ligne: LigneDeCourses, onClick: () -> Unit) {
    val rarete = vm.catalogue.objet(ligne.objetId)?.rarete
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconeObjet(ligne.objetId, rarete, 40.dp)
                    Text(ligne.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${ligne.possede}/${ligne.besoin}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (ligne.complet) Signal.Vert else Signal.Texte,
                    )
                }
                Barre(minOf(ligne.possede, ligne.besoin).toFloat() / ligne.besoin, if (ligne.complet) Signal.Vert else Signal.Or)
                Text(
                    ligne.pour.joinToString(" · ") { "${it.libelle} (${it.quantite})" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
            Compteur(ligne.possede, ligne.nom, onChange = { vm.changerQuantite(ligne.objetId, it) }, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * Mise en place des quêtes : le joueur coche celles qui sont en cours dans son jeu (journal de
 * quêtes). Celles qui y mènent sont marquées terminées.
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Bandeau("Progression", Signal.Vert)
                }
                Text("Mes quêtes en cours", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Coche les quêtes affichées en cours dans ton journal de quêtes. Toutes celles qui y mènent seront marquées terminées.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    label = { Text("Chercher une quête ou un commerçant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.indiquerQuetesEnCours(choix); onRetour() },
                    colors = ButtonDefaults.buttonColors(containerColor = Signal.Vert, contentColor = Signal.SurCouleur),
                ) { Text("Valider (${pluriel(choix.size, "quête", "quêtes")})") }
            }
        }
        items(affichees, key = { it.id }) { quete ->
            val coche = quete.id in choix
            val terminee = quete.id in vm.progression.quetesTerminees
            Surface(
                onClick = { choix = if (coche) choix - quete.id else choix + quete.id },
                color = Signal.Surface,
                shape = RoundedCornerShape(16.dp),
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
                        Text(
                            listOfNotNull(quete.commercant, "terminée".takeIf { terminee && !coche }).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
        }
    }
}

/** Choix d'un objectif plus lointain : un niveau d'établi au-delà du prochain, ou une quête à venir. */
@Composable
fun EcranAjoutObjectif(vm: CarnetViewModel, onRetour: () -> Unit) {
    val donnees = vm.catalogue.donnees
    val quetes = donnees.quetes.filter { it.objets.isNotEmpty() && it.id !in vm.progression.quetesTerminees }.sortedBy { it.nom }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Bandeau("Progression", Signal.Vert)
                }
                Text("Ajouter un objectif", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Le prochain niveau de tes établis et tes quêtes en cours sont déjà suivis. Ajoute ici un objectif plus lointain\u00a0: ses objets s'ajoutent à ta liste de courses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                Text("Atelier", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
            }
        }
        donnees.etablis.forEach { etabli ->
            val atteint = vm.suivi.niveau(vm.progression, etabli)
            items(etabli.niveaux.filter { it.niveau > atteint }, key = { "etabli-${etabli.id}-${it.niveau}" }) { niveau ->
                val objectif = Objectif.NiveauDEtabli(etabli.id, niveau.niveau)
                ChoixObjectif(
                    titre = vm.planificateur.libelle(objectif),
                    detail = detailCout(niveau.cout, vm.catalogue),
                    suivi = vm.suit(objectif),
                    onClick = { vm.ajouterObjectif(objectif); onRetour() },
                )
            }
        }
        item { Text("Quêtes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp)) }
        items(quetes, key = { "quete-${it.id}" }) { quete ->
            val objectif = Objectif.QueteATerminer(quete.id)
            ChoixObjectif(
                titre = quete.nom,
                detail = listOfNotNull(quete.commercant, detailCout(quete.objets, vm.catalogue)).joinToString(" — "),
                suivi = vm.suit(objectif),
                onClick = { vm.ajouterObjectif(objectif); onRetour() },
            )
        }
    }
}

@Composable
private fun ChoixObjectif(titre: String, detail: String, suivi: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !suivi, color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            if (suivi) Text("Déjà suivi", style = MaterialTheme.typography.labelMedium, color = Signal.Vert)
        }
    }
}
