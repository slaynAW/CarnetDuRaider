package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.slaynaw.carnetduraider.core.miseajour.Manifeste
import io.github.slaynaw.carnetduraider.core.miseajour.MisesAJour
import io.github.slaynaw.carnetduraider.miseajour.EtatMiseAJour
import io.github.slaynaw.carnetduraider.miseajour.Etape
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** « Mise à jour disponible » : nouveautés, puis téléchargement et installation en une touche. */
@Composable
fun DialogueMiseAJour(etat: EtatMiseAJour, plusTard: () -> Unit, installer: () -> Unit) {
    val manifeste = etat.nouvelle ?: return
    val occupe = etat.etape != Etape.AUCUNE
    AlertDialog(
        onDismissRequest = { if (!occupe) plusTard() },
        properties = DialogProperties(dismissOnClickOutside = !occupe, dismissOnBackPress = !occupe),
        containerColor = Signal.Surface,
        icon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = Signal.Or) },
        title = { Text("Mise à jour disponible", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description(manifeste), style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
                Nouveautes(manifeste.nouveautes)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Signal.Vert, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Version officielle vérifiée. Tes objectifs et ce que tu as rapporté sont conservés.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.TexteDoux,
                    )
                }
                if (occupe) {
                    LinearProgressIndicator(
                        progress = { etat.progression },
                        modifier = Modifier.fillMaxWidth(),
                        color = Signal.Or,
                        trackColor = Signal.Trait,
                    )
                    Text(
                        if (etat.etape == Etape.TELECHARGEMENT) {
                            "Téléchargement… ${(etat.progression * 100).toInt()}\u00a0%"
                        } else {
                            "Installation par Android…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.TexteDoux,
                    )
                } else if (etat.erreur != null) {
                    Text(etat.erreur, style = MaterialTheme.typography.bodySmall, color = Signal.Rouge)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = installer,
                enabled = !occupe,
                colors = ButtonDefaults.buttonColors(containerColor = Signal.Or, contentColor = Signal.SurCouleur),
            ) {
                Text(if (etat.erreur != null) "Réessayer" else "Mettre à jour")
            }
        },
        dismissButton = {
            TextButton(onClick = plusTard, enabled = !occupe) { Text("Plus tard", color = Signal.TexteDoux) }
        },
    )
}

/** « Version 1.2.0 · 2 novembre 2026 · 8,4 Mo ». */
private fun description(manifeste: Manifeste): String {
    val taille = manifeste.fichier(MisesAJour.PLATEFORME)?.taille ?: 0
    val date = try {
        LocalDate.parse(manifeste.date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
    } catch (e: DateTimeParseException) {
        ""
    }
    return listOf(
        "Version ${manifeste.version}",
        date,
        String.format(Locale.FRENCH, "%.1f\u00a0Mo", taille / 1_048_576.0),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Nouveautés (NOUVEAUTES.md) : titres « ### », listes « - », « **gras** » (texte uniquement). */
@Composable
private fun Nouveautes(texte: String) {
    val forme = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .background(Signal.Fond, forme)
            .border(1.dp, Signal.Trait, forme)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (brute in texte.lines()) {
            val ligne = brute.trim()
            when {
                ligne.isEmpty() -> Unit
                ligne.startsWith("### ") -> Text(
                    ligne.removePrefix("### ").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Signal.TexteDiscret,
                    modifier = Modifier.padding(top = 4.dp),
                )
                ligne.startsWith("- ") || ligne.startsWith("* ") -> Row {
                    Text("•", color = Signal.Or, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(gras(ligne.drop(2)), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(gras(ligne), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun gras(texte: String): AnnotatedString = buildAnnotatedString {
    texte.split("**").forEachIndexed { i, morceau ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(morceau) } else append(morceau)
    }
}
