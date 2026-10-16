package io.github.slaynaw.carnetduraider

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.slaynaw.carnetduraider.ui.CarnetApp
import io.github.slaynaw.carnetduraider.ui.Onglet
import io.github.slaynaw.carnetduraider.ui.theme.CarnetTheme

class MainActivity : ComponentActivity() {
    /** Onglet demandé par une notification (alerte des conditions de la carte). */
    private var ongletDemande by mutableStateOf<Onglet?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Thème toujours sombre : icônes claires dans les barres système.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) lireDemande(intent)
        setContent {
            CarnetTheme {
                CarnetApp(ongletDemande = ongletDemande, demandeTraitee = { ongletDemande = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lireDemande(intent)
    }

    private fun lireDemande(intent: Intent?) {
        ongletDemande = Onglet.entries.firstOrNull { it.name == intent?.getStringExtra(ONGLET) }
    }

    companion object {
        const val ONGLET = "onglet"
    }
}
