package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.core.Rarete
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Les quatre bandes inclinées (cyan, vert, or, rouge) : la signature visuelle de l'appli. */
@Composable
fun Bandes(
    modifier: Modifier = Modifier,
    hauteur: Dp = 28.dp,
    largeurBande: Dp = 7.dp,
    couleurs: List<Color> = listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge),
) {
    Canvas(modifier.size(width = largeurBande * 7, height = hauteur).clearAndSetSemantics { }) {
        val l = largeurBande.toPx()
        val decalage = size.height * 0.36f
        couleurs.forEachIndexed { i, couleur ->
            val x = i * l * 1.5f
            val chemin = Path().apply {
                moveTo(x, size.height)
                lineTo(x + l, size.height)
                lineTo(x + l + decalage, 0f)
                lineTo(x + decalage, 0f)
                close()
            }
            drawPath(chemin, couleur)
        }
    }
}

/** Petite étiquette arrondie (rareté, catégorie, domaine). */
@Composable
fun Puce(texte: String, modifier: Modifier = Modifier, fond: Color = Signal.Surface, couleur: Color = Signal.Texte, point: Color? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(fond)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (point != null) Box(Modifier.size(8.dp).clip(CircleShape).background(point))
        Text(texte, style = MaterialTheme.typography.labelMedium, color = couleur)
    }
}

/** Puce de filtre ou de choix : pleine de [couleur] quand elle est choisie. Un texte trop long est coupé (« … »). */
@Composable
fun PuceFiltre(texte: String, choisie: Boolean, couleur: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = if (choisie) couleur else Signal.Releve,
        contentColor = if (choisie) Signal.SurCouleur else Signal.Texte,
        shape = RoundedCornerShape(50),
        modifier = modifier.semantics { selected = choisie },
    ) {
        Text(
            texte,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun PuceRarete(rarete: Rarete?, modifier: Modifier = Modifier) {
    if (rarete != null) Puce(rarete.libelle, modifier, point = Signal.rarete(rarete))
}

/** Étiquette en capitales sur fond de couleur, en tête d'écran (« OBJET », « CARTES »…). */
@Composable
fun Bandeau(texte: String, couleur: Color) {
    Text(
        texte.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Signal.SurCouleur,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(couleur)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Barre de progression fine, aux couleurs de l'appli. */
@Composable
fun Barre(progression: Float, couleur: Color, modifier: Modifier = Modifier, epaisseur: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(epaisseur).clip(RoundedCornerShape(50)).background(Signal.Trait)) {
        Box(
            Modifier
                .fillMaxWidth(progression.coerceIn(0f, 1f))
                .height(epaisseur)
                .clip(RoundedCornerShape(50))
                .background(couleur),
        )
    }
}

/** En-tête de toutes les sections : logo (les quatre bandes), nom de l'appli et réglages. */
@Composable
fun EnTete(reglagesOuverts: Boolean, ouvrirReglages: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Signal.Fond)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Bandes(hauteur = 20.dp, largeurBande = 5.dp)
        Text("Carnet du Raider", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = ouvrirReglages) {
            Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = if (reglagesOuverts) Signal.Or else Signal.TexteDoux)
        }
    }
}
