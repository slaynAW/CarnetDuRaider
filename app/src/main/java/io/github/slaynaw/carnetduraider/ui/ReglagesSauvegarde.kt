package io.github.slaynaw.carnetduraider.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlinx.coroutines.launch

/** Longueur minimale du mot de passe de sauvegarde. */
private const val MOT_DE_PASSE_MINIMUM = 8

/** Nom proposé pour le fichier de sauvegarde. */
private const val NOM_FICHIER = "carnet-du-raider.sauvegarde"

/** Ce que le joueur est en train de faire dans la section. */
private sealed interface Etape {
    data object Rien : Etape
    data object NouveauMotDePasse : Etape
    data class MotDePassePourRestaurer(val uri: Uri) : Etape
}

/**
 * Sauvegarde chiffrée : activer (mot de passe, puis choix du fichier), état de la sauvegarde
 * automatique, restauration (choix du fichier, puis mot de passe).
 */
@Composable
fun SectionSauvegarde(vm: CarnetViewModel) {
    val etat by vm.sauvegarde.etat.collectAsStateWithLifecycle()
    val portee = rememberCoroutineScope()
    var etape by remember { mutableStateOf<Etape>(Etape.Rien) }
    var motDePasse by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val creer = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val mdp = motDePasse.toCharArray()
        motDePasse = ""
        if (uri != null && mdp.isEmpty()) {
            message = "Mot de passe perdu en route\u00a0: recommence."
        } else if (uri != null) {
            portee.launch {
                message = vm.sauvegarde.activer(uri, mdp) ?: "Sauvegarde activée\u00a0: elle se met à jour à chaque changement."
            }
        }
    }
    val ouvrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = null
            etape = Etape.MotDePassePourRestaurer(uri)
        }
    }

    Text("Sauvegarde", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (etat.active) {
                Text("Sauvegarde automatique activée", style = MaterialTheme.typography.titleSmall, color = Signal.Vert)
                Text(
                    buildString {
                        append(etat.fichier ?: NOM_FICHIER)
                        append(" · ")
                        append(
                            when {
                                etat.enCours -> "sauvegarde en cours…"
                                etat.derniere > 0 -> "dernière sauvegarde ${depuis(etat.derniere)}"
                                else -> "pas encore sauvegardée"
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
                etat.erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal.Rouge) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm.sauvegarde::sauvegarderMaintenant) { Text("Sauvegarder maintenant", color = Signal.Vert) }
                    TextButton(onClick = vm.sauvegarde::arreter) { Text("Arrêter", color = Signal.TexteDiscret) }
                }
            } else {
                Text("Retrouve tes données après une réinstallation", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Ta progression, tes objectifs et ce que tu as rapporté sont sauvegardés automatiquement dans un fichier chiffré " +
                        "avec ton mot de passe. Range-le dans Google Drive pour le retrouver sur un autre téléphone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
                TextButton(onClick = { etape = Etape.NouveauMotDePasse }) { Text("Activer la sauvegarde", color = Signal.Vert) }
            }
            TextButton(onClick = { ouvrir.launch(arrayOf("*/*")) }) { Text("Restaurer une sauvegarde", color = Signal.Or) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux) }
        }
    }
    Text(
        "Le fichier est chiffré sur le téléphone (AES-256, clé tirée de ton mot de passe)\u00a0: il n'est lisible " +
            "qu'avec ce mot de passe, que l'appli ne garde pas. Personne ne peut le retrouver pour toi. " +
            "L'appli n'envoie rien elle-même\u00a0: c'est le service choisi (Google Drive…) qui range le fichier.",
        style = MaterialTheme.typography.bodySmall,
        color = Signal.TexteDiscret,
    )

    when (val e = etape) {
        Etape.Rien -> {}
        Etape.NouveauMotDePasse -> DialogueNouveauMotDePasse(
            onAnnuler = { etape = Etape.Rien },
            onValider = { mdp ->
                motDePasse = mdp
                etape = Etape.Rien
                creer.launch(NOM_FICHIER)
            },
        )
        is Etape.MotDePassePourRestaurer -> DialogueRestaurer(
            enCours = etat.enCours,
            onAnnuler = { etape = Etape.Rien },
            onValider = { mdp ->
                portee.launch {
                    val erreur = vm.restaurerSauvegarde(e.uri, mdp.toCharArray())
                    if (erreur == null) {
                        etape = Etape.Rien
                        message = "Données restaurées. La sauvegarde automatique continue dans ce fichier."
                    } else {
                        message = erreur
                    }
                }
            },
            erreur = message.takeIf { e is Etape.MotDePassePourRestaurer },
        )
    }
}

@Composable
private fun ChampMotDePasse(valeur: String, onChange: (String) -> Unit, libelle: String) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DialogueNouveauMotDePasse(onAnnuler: () -> Unit, onValider: (String) -> Unit) {
    var mdp by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val probleme = when {
        mdp.length < MOT_DE_PASSE_MINIMUM -> "Au moins $MOT_DE_PASSE_MINIMUM caractères."
        mdp != confirmation -> "Les deux mots de passe sont différents."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onAnnuler,
        containerColor = Signal.Surface,
        title = { Text("Mot de passe de la sauvegarde") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Note-le bien\u00a0: sans lui, la sauvegarde ne peut pas être lue, et personne ne peut le retrouver. " +
                        "Ensuite, choisis où ranger le fichier (Google Drive pour le retrouver sur un autre téléphone).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
                ChampMotDePasse(mdp, { mdp = it }, "Mot de passe")
                ChampMotDePasse(confirmation, { confirmation = it }, "Encore une fois")
                if (mdp.isNotEmpty()) probleme?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal.Rouge) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(mdp) }, enabled = probleme == null) { Text("Choisir le fichier", color = Signal.Vert) }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler", color = Signal.TexteDoux) } },
    )
}

@Composable
private fun DialogueRestaurer(enCours: Boolean, erreur: String?, onAnnuler: () -> Unit, onValider: (String) -> Unit) {
    var mdp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!enCours) onAnnuler() },
        containerColor = Signal.Surface,
        title = { Text("Restaurer la sauvegarde") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Toutes les données actuelles de l'appli seront remplacées par celles de la sauvegarde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
                ChampMotDePasse(mdp, { mdp = it }, "Mot de passe")
                when {
                    enCours -> Text("Déchiffrement…", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                    erreur != null -> Text(erreur, style = MaterialTheme.typography.bodySmall, color = Signal.Rouge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(mdp) }, enabled = mdp.isNotEmpty() && !enCours) { Text("Restaurer", color = Signal.Or) }
        },
        dismissButton = { TextButton(onClick = onAnnuler, enabled = !enCours) { Text("Annuler", color = Signal.TexteDoux) } },
    )
}
