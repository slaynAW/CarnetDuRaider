package io.github.slaynaw.carnetduraider.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.R
import io.github.slaynaw.carnetduraider.core.Rarete

/**
 * Style « Signal » : fond sombre, et les quatre bandes du jeu comme code couleur de l'appli.
 * Chaque domaine a sa couleur : or pour les objets, cyan pour les cartes, vert pour les objectifs,
 * rouge pour le raid et les ARC, violet pour les builds. Chaque onglet s'ouvre sur un bandeau de sa
 * couleur, avec son motif ([io.github.slaynaw.carnetduraider.ui.BandeauSection]).
 */
object Signal {
    val Fond = Color(0xFF141417)
    val Surface = Color(0xFF1E1E23)
    val Releve = Color(0xFF28282F)
    val Trait = Color(0xFF34343C)
    val Texte = Color(0xFFF3EEE4)
    val TexteDoux = Color(0xFFABA69C)
    val TexteDiscret = Color(0xFF8A867E)

    val Or = Color(0xFFF2B233)
    val Cyan = Color(0xFF3CC4D8)
    val Vert = Color(0xFF6CCB5F)
    val Rouge = Color(0xFFF0544A)
    val Violet = Color(0xFFB48CF2)

    /** Texte posé sur une des quatre couleurs. */
    val SurCouleur = Color(0xFF141417)

    fun rarete(rarete: Rarete?): Color = when (rarete) {
        Rarete.COMMUN -> Color(0xFFB4B4B4)
        Rarete.INHABITUEL -> Vert
        Rarete.RARE -> Color(0xFF4FA3F7)
        Rarete.EPIQUE -> Color(0xFFD46BE0)
        Rarete.LEGENDAIRE -> Or
        null -> TexteDiscret
    }
}

/** Prompt pour les titres et les chiffres, Barlow pour le texte (polices libres, OFL). */
val Prompt = FontFamily(
    Font(R.font.prompt_semibold, FontWeight.SemiBold),
    Font(R.font.prompt_bold, FontWeight.Bold),
    Font(R.font.prompt_extrabold, FontWeight.ExtraBold),
)

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

private val typographie = Typography(
    headlineLarge = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp),
)

private val couleurs = darkColorScheme(
    primary = Signal.Or,
    onPrimary = Signal.SurCouleur,
    secondary = Signal.Cyan,
    onSecondary = Signal.SurCouleur,
    tertiary = Signal.Vert,
    onTertiary = Signal.SurCouleur,
    error = Signal.Rouge,
    onError = Signal.SurCouleur,
    background = Signal.Fond,
    onBackground = Signal.Texte,
    surface = Signal.Fond,
    onSurface = Signal.Texte,
    surfaceVariant = Signal.Surface,
    onSurfaceVariant = Signal.TexteDoux,
    surfaceContainer = Signal.Surface,
    surfaceContainerHigh = Signal.Releve,
    outline = Signal.Trait,
    outlineVariant = Signal.Trait,
)

@Composable
fun CarnetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = couleurs, typography = typographie, content = content)
}
