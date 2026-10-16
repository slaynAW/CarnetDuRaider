package io.github.slaynaw.carnetduraider.alertes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.slaynaw.carnetduraider.carnet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Réveil d'une alerte, redémarrage du téléphone ou mise à jour de l'appli : affiche les alertes
 * dues et programme la suivante (Android oublie les réveils au redémarrage).
 */
class RecepteurAlertes : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val fin = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                context.carnet.alertes.traiter()
            } finally {
                fin.finish()
            }
        }
    }

    companion object {
        const val REVEIL = "io.github.slaynaw.carnetduraider.ALERTE"
        private val ACTIONS = setOf(REVEIL, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
