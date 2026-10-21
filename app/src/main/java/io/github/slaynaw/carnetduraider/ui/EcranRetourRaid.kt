package io.github.slaynaw.carnetduraider.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.ObjetATrouver
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/**
 * Retour de raid, le seul endroit où le joueur note des objets : ce qu'il lui faut, et rien d'autre (il n'y
 * a pas de réserve). Le butin va aux objectifs qui le demandent et se décompte ; on ne peut pas noter plus
 * que ce qui manque. « Valider » termine ce qui est complet et montre le bilan ; « Annuler » remet tout
 * comme à l'ouverture de l'écran.
 */
@Composable
fun EcranRetourRaid(vm: CarnetViewModel, onRetour: () -> Unit) {
    // Liste à l'ouverture, figée : un objet qui atteint son nombre ne disparaît pas sous le doigt.
    val ouvert = remember { vm.ouvrirRetourDeRaid() }
    val depart = remember(ouvert) { ouvert.liste.filter { !it.trouve } }
    val actuels = vm.listeDuRaid().associateBy { it.objetId }
    val trouves = depart.count { actuels[it.objetId]?.trouve != false }
    val notes = vm.saisis.values.sum()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Bandeau("Raid", Signal.Rouge)
                }
                Text("Retour de raid", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Note ce que tu as rapporté\u00a0: chaque objet va aux objectifs qui en ont besoin. " +
                        "À la validation, tout ce qui est complet se termine tout seul.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (depart.isNotEmpty()) {
                            Text(
                                "$trouves sur ${depart.size} trouvé${if (trouves > 1) "s" else ""}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (trouves > 0) Signal.Vert else Signal.TexteDoux,
                            )
                        }
                        Text(
                            if (notes == 0) "Rien de noté pour l'instant." else "${pluriel(notes, "objet noté", "objets notés")}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (notes == 0) Signal.TexteDoux else Signal.Or,
                        )
                    }
                    if (notes > 0) {
                        TextButton(onClick = vm::annulerRetourDeRaid) { Text("Annuler", color = Signal.TexteDoux) }
                    }
                    Button(onClick = onRetour, colors = ButtonDefaults.buttonColors(containerColor = Signal.Or, contentColor = Signal.SurCouleur)) {
                        Text("Valider")
                    }
                }
                if (depart.isEmpty()) {
                    Text(
                        "Rien à trouver pour l'instant\u00a0: suis un objectif ou ajoute un objet à chercher dans l'onglet Raid.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Signal.TexteDoux,
                    )
                }
            }
        }
        items(depart, key = { it.objetId }) { a -> LigneSaisie(vm, actuels[a.objetId] ?: a, vm.saisis[a.objetId] ?: 0) }
    }
}

/** Un objet à rapporter : son décompte, pour quoi il est demandé, et ce qui a été noté ([note]). */
@Composable
private fun LigneSaisie(vm: CarnetViewModel, a: ObjetATrouver, note: Int) {
    val nom = vm.catalogue.nomObjet(a.objetId)
    val pour = a.pour.map { it.libelle } + listOfNotNull("ajouté par toi".takeIf { a.ajouteParLeJoueur })
    Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconeObjet(a.objetId, vm.catalogue.objet(a.objetId)?.rarete, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(if (a.trouve) "Trouvé\u00a0! ${a.voulu}/${a.voulu}" else "Encore ${a.reste} à trouver · ${a.trouves}/${a.voulu}")
                            if (note > 0) append("  (+$note)")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            a.trouve -> Signal.Vert
                            note > 0 -> Signal.Or
                            else -> Signal.TexteDoux
                        },
                    )
                    Text(
                        pour.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.TexteDiscret,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (a.trouve) Icon(Icons.Filled.CheckCircle, contentDescription = "Trouvé", tint = Signal.Vert, modifier = Modifier.size(28.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                FilledIconButton(
                    onClick = { vm.noter(a.objetId, -1) },
                    enabled = note > 0,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "En retirer un\u00a0: $nom" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Texte),
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                FilledIconButton(
                    onClick = { vm.noter(a.objetId, +5) },
                    enabled = !a.trouve,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "En ajouter cinq\u00a0: $nom" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Or),
                ) { Text("+5", style = MaterialTheme.typography.titleMedium) }
                FilledIconButton(
                    onClick = { vm.noter(a.objetId, +1) },
                    enabled = !a.trouve,
                    modifier = Modifier.size(width = 72.dp, height = 48.dp).semantics { contentDescription = "En ajouter un\u00a0: $nom" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Or, contentColor = Signal.SurCouleur),
                ) { Text("+1", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
