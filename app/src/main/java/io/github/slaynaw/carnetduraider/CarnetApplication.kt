package io.github.slaynaw.carnetduraider

import android.app.Application
import android.content.Context
import io.github.slaynaw.carnetduraider.alertes.GestionnaireAlertes
import io.github.slaynaw.carnetduraider.calendrier.GestionnaireCalendrier
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.DonneesJeu
import io.github.slaynaw.carnetduraider.miseajour.GestionnaireMiseAJour
import io.github.slaynaw.carnetduraider.sauvegarde.GestionnaireSauvegarde
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Ce qui vit aussi longtemps que l'appli : données du jeu, mises à jour (un téléchargement survit à
 * l'écran), calendrier et alertes des conditions de la carte, sauvegarde chiffrée.
 */
class CarnetApplication : Application() {
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val miseAJour: GestionnaireMiseAJour by lazy { GestionnaireMiseAJour(this, portee) }
    val calendrier: GestionnaireCalendrier by lazy { GestionnaireCalendrier(this, portee) }
    val catalogue: Catalogue by lazy { Catalogue(DonneesJeu.embarquees()) }
    val alertes: GestionnaireAlertes by lazy { GestionnaireAlertes(this, portee, calendrier) { catalogue } }

    /** Créée au démarrage : elle doit voir chaque changement de données pour la sauvegarde automatique. */
    lateinit var sauvegarde: GestionnaireSauvegarde
        private set

    override fun onCreate() {
        super.onCreate()
        sauvegarde = GestionnaireSauvegarde(this, portee)
        // Calendrier relu : le prochain réveil des alertes est recalculé.
        portee.launch {
            calendrier.etat.map { it.donnees?.luLe }.distinctUntilChanged().collect { alertes.traiter() }
        }
    }
}

val Context.carnet: CarnetApplication get() = applicationContext as CarnetApplication
