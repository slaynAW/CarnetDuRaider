package io.github.slaynaw.carnetduraider.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.calendrier.ReglagesAlertes
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Demande (une fois) l'autorisation d'afficher des notifications, nécessaire depuis Android 13. */
@Composable
private fun demandeNotifications(): () -> Unit {
    val context = LocalContext.current
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            lanceur.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Cloche : être prévenu (ou non) avant le début d'une condition de la carte. */
@Composable
fun BoutonAlerte(vm: CarnetViewModel, conditionId: String, nom: String) {
    val reglages by vm.alertes.reglages.collectAsStateWithLifecycle()
    val suivie = conditionId in reglages.conditions
    val demander = demandeNotifications()
    IconButton(onClick = {
        if (!suivie) demander()
        vm.alertes.suivre(conditionId, !suivie)
    }) {
        Icon(
            if (suivie) Icons.Filled.Notifications else Icons.Outlined.Notifications,
            contentDescription = if (suivie) "Ne plus être prévenu\u00a0: $nom" else "Être prévenu avant\u00a0: $nom",
            tint = if (suivie) Signal.Cyan else Signal.TexteDiscret,
        )
    }
}

/** Réglages des alertes : conditions suivies, avance, état des autorisations d'Android. */
@Composable
fun SectionAlertes(vm: CarnetViewModel) {
    val context = LocalContext.current
    val reglages by vm.alertes.reglages.collectAsStateWithLifecycle()
    val demander = demandeNotifications()
    // Autorisations relues au retour dans l'appli (l'utilisateur a pu les changer dans Android).
    var verification by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        verification++
        vm.alertes.replanifier()
        onPauseOrDispose { }
    }
    val notifications = remember(verification) { vm.alertes.notificationsAutorisees() }
    val precis = remember(verification) { vm.alertes.reveilsPrecis() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Alertes des conditions de la carte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Une notification avant le début des conditions choisies, d'après le calendrier officiel (région\u00a0: ${vm.region.libelle}). " +
                "Elle arrive même appli fermée et sans connexion.",
            style = MaterialTheme.typography.bodySmall,
            color = Signal.TexteDoux,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReglagesAlertes.AVANCES.forEach { minutes ->
                FilterChip(
                    selected = reglages.avanceMinutes == minutes,
                    onClick = { vm.alertes.changerAvance(minutes) },
                    label = { Text("$minutes\u00a0min avant") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Signal.Cyan,
                        selectedLabelColor = Signal.SurCouleur,
                        labelColor = Signal.TexteDoux,
                    ),
                )
            }
        }
        if (reglages.conditions.isNotEmpty() && !notifications) {
            Avertissement("Android bloque les notifications de l'appli\u00a0: les alertes ne s'afficheront pas.", "Autoriser") {
                ouvrirReglagesNotifications(context)
            }
        }
        if (reglages.conditions.isNotEmpty() && !precis && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Avertissement("Alertes à quelques minutes près. Pour qu'elles arrivent à l'heure exacte, autorise les alarmes.", "Autoriser") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
        }
        Surface(color = Signal.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                vm.catalogue.donnees.conditions.sortedWith(compareBy({ !it.majeure }, { it.nom })).forEach { condition ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(condition.nom, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = condition.id in reglages.conditions,
                            onCheckedChange = { actif ->
                                if (actif) demander()
                                vm.alertes.suivre(condition.id, actif)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Signal.SurCouleur, checkedTrackColor = Signal.Cyan),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Avertissement(texte: String, action: String, onClick: () -> Unit) {
    Surface(color = Signal.Releve, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texte, style = MaterialTheme.typography.bodySmall, color = Signal.Texte, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(action, color = Signal.Cyan) }
        }
    }
}

private fun ouvrirReglagesNotifications(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}
