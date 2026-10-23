package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val lignes = depart.map { actuels[it.objetId] ?: it }
    val trouves = lignes.count { it.trouve }
    val notes = vm.saisis.values.sum()
    val voulus = lignes.sumOf { it.voulu }
    val rapportes = lignes.sumOf { minOf(it.trouves, it.voulu) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 8.dp, end = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Text("Retour de raid", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (notes > 0) {
                        TextButton(onClick = vm::annulerRetourDeRaid) { Text("Annuler", color = Signal.TexteDoux) }
                    } else {
                        Bandeau("Raid", Signal.Rouge)
                    }
                }
                if (depart.isNotEmpty()) {
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (notes == 0) "Rien de noté pour l'instant" else "${pluriel(notes, "objet noté", "objets notés")}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (notes == 0) Signal.Texte else Signal.Or,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "$trouves sur ${depart.size} complet${if (trouves > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (trouves > 0) Signal.Vert else Signal.TexteDoux,
                            )
                        }
                        Barre(if (voulus == 0) 0f else rapportes.toFloat() / voulus, Signal.Or)
                        Text(
                            "Touche l'icône d'un objet pour en ajouter 1 (appui long\u00a0: +5). Le butin va aux objectifs qui le demandent, puis à ton butin recherché.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = PLACE_BOUTON_FIXE),
            ) {
                if (depart.isEmpty()) {
                    item {
                        Text(
                            "Rien à trouver pour l'instant\u00a0: indique ce que tu as en cours dans l'onglet Objectifs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }
                itemsIndexed(lignes, key = { _, a -> a.objetId }) { i, a ->
                    LigneSaisie(vm, a, vm.saisis[a.objetId] ?: 0, premiere = i == 0, derniere = i == lignes.lastIndex)
                }
            }
        }
        // Valider : termine ce qui est complet et montre le bilan. Toujours à portée de pouce.
        BoutonFixe(
            if (notes == 0) "Valider" else "Valider · ${pluriel(notes, "objet", "objets")}",
            Signal.Or,
            onClick = onRetour,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Un objet à rapporter, sur une ligne : son icône (la toucher en ajoute un, appui long : cinq, avec ce qui
 * a été noté en pastille), pour quoi il est demandé, et « − n/voulu + ». Complet, la ligne passe au vert.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneSaisie(vm: CarnetViewModel, a: ObjetATrouver, note: Int, premiere: Boolean, derniere: Boolean) {
    val nom = vm.catalogue.nomObjet(a.objetId)
    val forme = RoundedCornerShape(
        topStart = if (premiere) 6.dp else 0.dp,
        topEnd = if (premiere) 6.dp else 0.dp,
        bottomStart = if (derniere) 6.dp else 0.dp,
        bottomEnd = if (derniere) 6.dp else 0.dp,
    )
    Surface(color = if (a.trouve) Signal.SurfaceVerte else Signal.Surface, shape = forme, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (!premiere) HorizontalDivider(color = Signal.Releve)
            Row(
                Modifier.heightIn(min = 62.dp).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .combinedClickable(
                            enabled = !a.trouve,
                            onClickLabel = "En ajouter un",
                            onLongClickLabel = "En ajouter cinq",
                            onLongClick = { vm.noter(a.objetId, +5) },
                            onClick = { vm.noter(a.objetId, +1) },
                        )
                        .semantics { contentDescription = "En ajouter un\u00a0: $nom" },
                ) {
                    IconeObjet(a.objetId, vm.catalogue.objet(a.objetId)?.rarete, 46.dp)
                    if (note > 0) {
                        Text(
                            "+$note",
                            style = MaterialTheme.typography.labelSmall,
                            color = Signal.SurCouleur,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 7.dp, y = (-7).dp)
                                .background(Signal.Or, RoundedCornerShape(50))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (a.trouve) "Complet\u00a0!" else a.pour.joinToString(" · ") { it.libelle },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (a.trouve) Signal.Vert else Signal.TexteDoux,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BoutonPas("−", "En retirer un\u00a0: $nom", enabled = note > 0, couleur = Signal.Texte) { vm.noter(a.objetId, -1) }
                Text(
                    buildAnnotatedString {
                        append("${a.trouves}")
                        withStyle(SpanStyle(color = Signal.TexteDiscret, fontSize = 12.sp)) { append("/${a.voulu}") }
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (a.trouve) Signal.Vert else Signal.Texte,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
                BoutonPas("+", "En ajouter un\u00a0: $nom", enabled = !a.trouve, couleur = Signal.Or) { vm.noter(a.objetId, +1) }
            }
        }
    }
}

/** Petit bouton « − » ou « + » d'une ligne du retour de raid (cible de 44 dp). */
@Composable
private fun BoutonPas(texte: String, description: String, enabled: Boolean, couleur: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 36.dp, height = 44.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(texte, style = MaterialTheme.typography.titleLarge, color = if (enabled) couleur else Signal.Trait)
    }
}
