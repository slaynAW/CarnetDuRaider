package io.github.slaynaw.carnetduraider.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlin.math.abs

/** Durée du glissement de la pastille d'un onglet à l'autre. */
private const val GLISSEMENT_MS = 280

private val EasingGlissement = CubicBezierEasing(.3f, 0f, .2f, 1f)
private val HauteurBarre = 80.dp
private val LargeurPastille = 64.dp

/**
 * Les six onglets en bas de l'écran. Une seule pastille, de la couleur de l'onglet actif, glisse jusqu'à
 * l'onglet touché en prenant sa couleur (rien ne bouge avec « Supprimer les animations »).
 */
@Composable
fun BarreOnglets(onglet: Onglet, aller: (Onglet) -> Unit) {
    val anime = animationsActives()
    Surface(color = Signal.Surface) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .height(HauteurBarre),
        ) {
            val largeurOnglet = maxWidth / Onglet.entries.size
            val largeurPastille = minOf(LargeurPastille, largeurOnglet - 6.dp)
            val cible = largeurOnglet * onglet.ordinal + (largeurOnglet - largeurPastille) / 2
            val x by animateDpAsState(
                cible,
                animationSpec = if (anime) tween(GLISSEMENT_MS, easing = EasingGlissement) else snap(),
                label = "pastille",
            )
            val couleur by animateColorAsState(
                onglet.couleur,
                animationSpec = if (anime) tween(GLISSEMENT_MS, easing = EasingGlissement) else snap(),
                label = "couleur de la pastille",
            )
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .offset(x = x)
                    .size(width = largeurPastille, height = 32.dp)
                    .background(couleur, RoundedCornerShape(50)),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                Onglet.entries.forEach { o ->
                    val choisi = o == onglet
                    // L'icône passe en sombre quand la pastille est arrivée sous elle ; en chemin, elle prend la couleur de l'onglet.
                    val sousLaPastille = choisi && abs((x - cible).value) < 8f
                    Column(
                        Modifier
                            .width(largeurOnglet)
                            .fillMaxHeight()
                            .selectable(selected = choisi, role = Role.Tab, onClick = { aller(o) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.padding(top = 12.dp).height(32.dp), contentAlignment = Alignment.Center) {
                            Icon(o.icone, contentDescription = null, tint = when {
                                sousLaPastille -> Signal.SurCouleur
                                choisi -> o.couleur
                                else -> Signal.TexteDoux
                            }, modifier = Modifier.size(24.dp))
                        }
                        // Six onglets : libellés sur une ligne, un peu plus serrés.
                        Text(
                            o.titre,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = if (choisi) o.couleur else Signal.TexteDoux,
                        )
                    }
                }
            }
        }
    }
}
