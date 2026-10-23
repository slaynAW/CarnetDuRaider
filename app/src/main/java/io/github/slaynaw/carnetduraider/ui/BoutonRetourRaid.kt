package io.github.slaynaw.carnetduraider.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Une boucle complète : « RETOUR DE RAID », puis « INDIQUER MON BUTIN », puis retour. */
private const val BOUCLE_MS = 8000

/** Durée de chaque bascule du texte, à la fin de chaque moitié de la boucle. */
private const val BASCULE_MS = 480

/** Les bandes s'allument une fois par moitié de boucle, juste avant la bascule. */
private const val BANDES_MS = BOUCLE_MS / 2

private val EasingBascule = CubicBezierEasing(.7f, 0f, .3f, 1f)
private val HauteurLigne = 16.dp
private val CouleursBandes = listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge)

/** Verre rouge sombre, opaque à 84 % : la liste se devine dessous quand elle défile. */
/** Hauteur à laisser sous la liste du Raid pour que [BoutonRetourRaid] ne cache pas sa fin. */
val PLACE_BOUTON_RETOUR = 72.dp

private val FondVerre = Brush.verticalGradient(listOf(Color(0xD6422224), Color(0xD62C191B)))

/**
 * Le bouton « Retour de raid » de l'onglet Raid : une petite pastille en verre rouge, en bas à droite, posée
 * sur la liste (pas de fond dessous : la liste passe derrière, en filigrane). En boucle, lentement, les quatre
 * bandes du logo s'allument l'une après l'autre, puis le texte bascule de « RETOUR DE RAID » à « INDIQUER MON
 * BUTIN » et revient ; rien ne bouge avec « Supprimer les animations ».
 */
@Composable
fun BoutonRetourRaid(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val temps = if (animationsActives()) {
        rememberInfiniteTransition(label = "retour de raid").animateFloat(
            initialValue = 0f,
            targetValue = BOUCLE_MS.toFloat(),
            animationSpec = infiniteRepeatable(tween(BOUCLE_MS, easing = LinearEasing)),
            label = "temps",
        )
    } else {
        null
    }
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = Signal.Texte,
        shape = CircleShape,
        modifier = modifier
            .padding(end = 16.dp, bottom = 14.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .semantics { contentDescription = "Retour de raid : indiquer mon butin" },
    ) {
        Row(
            Modifier
                .background(FondVerre)
                .border(1.dp, Signal.Rouge.copy(alpha = 0.28f), CircleShape)
                .heightIn(min = 42.dp)
                .padding(start = 14.dp, end = 16.dp)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BandesQuiSAllument(temps)
            TexteQuiBascule(temps)
        }
    }
}

/** Les bandes du logo ; en boucle, chacune s'allume un instant, un peu après la précédente. */
@Composable
private fun BandesQuiSAllument(temps: State<Float>?) {
    Canvas(Modifier.size(width = 24.dp, height = 14.dp)) {
        val l = size.width * 5f / 35f
        val decalage = size.width * 7.2f / 35f
        CouleursBandes.forEachIndexed { i, couleur ->
            val x = i * size.width * 7.5f / 35f
            val chemin = Path().apply {
                moveTo(x, size.height)
                lineTo(x + l, size.height)
                lineTo(x + l + decalage, 0f)
                lineTo(x + decalage, 0f)
                close()
            }
            drawPath(chemin, couleur, alpha = temps?.let { eclat(it.value - i * 100f) } ?: 1f)
        }
    }
}

/** Opacité d'une bande à l'instant [t] : éteinte (0,28), elle s'allume vers les deux tiers de sa boucle. */
private fun eclat(t: Float): Float {
    val p = (((t % BANDES_MS) + BANDES_MS) % BANDES_MS) / BANDES_MS
    val eteinte = 0.28f
    return when {
        p < 0.66f -> eteinte
        p < 0.76f -> eteinte + (1f - eteinte) * (p - 0.66f) / 0.10f
        p < 0.90f -> 1f
        else -> 1f - (1f - eteinte) * (p - 0.90f) / 0.10f
    }
}

/** Les deux textes l'un sous l'autre, dans une fenêtre d'une ligne qui glisse de l'un à l'autre. */
@Composable
private fun TexteQuiBascule(temps: State<Float>?) {
    Box(Modifier.height(HauteurLigne).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -(temps?.let { lignesPassees(it.value) } ?: 0f) * HauteurLigne.toPx() },
        ) {
            Ligne("RETOUR DE RAID", Color(0xFFF7E9E3))
            Ligne("INDIQUER MON BUTIN", Signal.Or)
            Ligne("RETOUR DE RAID", Color(0xFFF7E9E3))
        }
    }
}

/** Combien de lignes ont défilé à l'instant [t] (de 0 à 2) : une bascule à la fin de chaque moitié. */
private fun lignesPassees(t: Float): Float {
    val moitie = BOUCLE_MS / 2f
    val dansMoitie = t % moitie
    val avance = ((dansMoitie - (moitie - BASCULE_MS)) / BASCULE_MS).coerceIn(0f, 1f)
    return (if (t < moitie) 0f else 1f) + EasingBascule.transform(avance)
}

@Composable
private fun Ligne(texte: String, couleur: Color) {
    Box(Modifier.height(HauteurLigne), contentAlignment = Alignment.CenterStart) {
        Text(
            texte,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
            color = couleur,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
