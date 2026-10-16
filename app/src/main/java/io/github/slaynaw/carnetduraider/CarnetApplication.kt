package io.github.slaynaw.carnetduraider

import android.app.Application
import android.content.Context
import io.github.slaynaw.carnetduraider.calendrier.GestionnaireCalendrier
import io.github.slaynaw.carnetduraider.miseajour.GestionnaireMiseAJour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ce qui vit aussi longtemps que l'appli : mises à jour (un téléchargement survit à l'écran), calendrier. */
class CarnetApplication : Application() {
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val miseAJour: GestionnaireMiseAJour by lazy { GestionnaireMiseAJour(this, portee) }
    val calendrier: GestionnaireCalendrier by lazy { GestionnaireCalendrier(this, portee) }
}

val Context.carnet: CarnetApplication get() = applicationContext as CarnetApplication
