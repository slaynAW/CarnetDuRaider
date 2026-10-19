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
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.ObjetATrouver
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/**
 * Retour de raid : saisie rapide de ce qu'on rapporte. En tête, ce qu'on cherche (onglet Raid), qui se
 * décompte à chaque objet ajouté ; la recherche ajoute n'importe quel autre objet. « Valider » montre les
 * objets qui ont atteint le nombre demandé ; « Annuler » remet tout comme à l'ouverture de l'écran.
 */
@Composable
fun EcranRetourRaid(vm: CarnetViewModel, onRetour: () -> Unit) {
    // État à l'ouverture, pour le bilan et l'annulation. La liste est figée : un objet qui atteint son
    // nombre ne disparaît pas sous le doigt.
    val ouvert = remember { vm.ouvrirRetourDeRaid() }
    val depart = remember(ouvert) { ouvert.aTrouver.filter { !it.trouve }.associateBy { it.objetId } }
    var recherche by rememberSaveable { mutableStateOf("") }
    val ids = if (recherche.isBlank()) depart.keys.toList() else vm.catalogue.rechercher(recherche, limite = 50).map { it.id }
    val actuels = vm.aTrouver().associateBy { it.objetId }
    // Un objet des objectifs quitte la liste quand il est complet : il est alors trouvé.
    fun etat(id: String): ObjetATrouver? = actuels[id] ?: depart[id]?.let { it.copy(trouves = maxOf(vm.possede(id), it.voulu)) }
    val trouves = depart.keys.count { etat(it)?.trouve == true }
    val ajoutes = vm.reserve.entries.sumOf { (id, n) -> (n - (ouvert.reserve[id] ?: 0)).coerceAtLeast(0) }

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
                    "Ajoute ce que tu rapportes\u00a0: ce que tu cherches est en tête et se décompte. Valide à la fin pour voir ce que tu as trouvé.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    placeholder = { Text("Chercher un autre objet…") },
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
                            if (ajoutes == 0) "Rien d'ajouté pour l'instant." else "${pluriel(ajoutes, "objet ajouté", "objets ajoutés")} à ta réserve.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ajoutes == 0) Signal.TexteDoux else Signal.Or,
                        )
                    }
                    if (vm.retourModifie) {
                        TextButton(onClick = vm::annulerRetourDeRaid) { Text("Annuler", color = Signal.TexteDoux) }
                    }
                    Button(onClick = onRetour, colors = ButtonDefaults.buttonColors(containerColor = Signal.Or, contentColor = Signal.SurCouleur)) {
                        Text("Valider")
                    }
                }
                if (recherche.isBlank() && ids.isEmpty()) {
                    Text(
                        "Rien à trouver pour l'instant\u00a0: cherche les objets rapportés.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Signal.TexteDoux,
                    )
                }
            }
        }
        items(ids, key = { it }) { id -> LigneSaisie(vm, id, etat(id), vm.possede(id) - (ouvert.reserve[id] ?: 0)) }
    }
}

/** Un objet rapporté : son décompte s'il est cherché ([cherche]), sinon ce qu'il y a en réserve. */
@Composable
private fun LigneSaisie(vm: CarnetViewModel, objetId: String, cherche: ObjetATrouver?, ecart: Int) {
    val nom = vm.catalogue.nomObjet(objetId)
    val possede = vm.possede(objetId)
    val trouve = cherche?.trouve == true
    Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(
                                when {
                                    cherche == null -> "$possede en réserve"
                                    trouve -> "Trouvé\u00a0! ${cherche.voulu}/${cherche.voulu}"
                                    else -> "Encore ${cherche.reste} à trouver · ${cherche.trouves}/${cherche.voulu}"
                                },
                            )
                            if (ecart != 0) append(if (ecart > 0) "  (+$ecart)" else "  ($ecart)")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            trouve -> Signal.Vert
                            ecart > 0 -> Signal.Or
                            else -> Signal.TexteDoux
                        },
                    )
                }
                if (trouve) Icon(Icons.Filled.CheckCircle, contentDescription = "Trouvé", tint = Signal.Vert, modifier = Modifier.size(28.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                FilledIconButton(
                    onClick = { vm.rapporter(objetId, -1) },
                    enabled = possede > 0,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "En retirer un\u00a0: $nom" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Texte),
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                FilledIconButton(
                    onClick = { vm.rapporter(objetId, +5) },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "En ajouter cinq\u00a0: $nom" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Releve, contentColor = Signal.Or),
                ) { Text("+5", style = MaterialTheme.typography.titleMedium) }
                FilledIconButton(
                    onClick = { vm.rapporter(objetId, +1) },
                    modifier = Modifier.size(width = 72.dp, height = 48.dp).semantics { contentDescription = "En ajouter un\u00a0: $nom" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Signal.Or, contentColor = Signal.SurCouleur),
                ) { Text("+1", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
