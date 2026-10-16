package io.github.slaynaw.carnetduraider.miseajour

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import io.github.slaynaw.carnetduraider.carnet

/**
 * Réponse de l'installateur d'Android à une mise à jour : confirmation à demander à l'utilisateur
 * (première mise à jour, ou version d'Android plus ancienne), ou échec / annulation à signaler.
 * En cas de succès, Android remplace l'appli : rien à faire ici.
 */
class RecepteurInstallation : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { confirmation ->
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> context.carnet.miseAJour.installationEchouee("Installation annulée.")
            else -> context.carnet.miseAJour.installationEchouee(
                "Android n'a pas pu installer la mise à jour" +
                    (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { " ($it)" } ?: "") + ".",
            )
        }
    }
}
