package io.github.slaynaw.carnetduraider

import android.app.Application
import android.content.Context
import io.github.slaynaw.carnetduraider.miseajour.GestionnaireMiseAJour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ce qui vit aussi longtemps que l'appli : les mises à jour (un téléchargement survit à l'écran). */
class CarnetApplication : Application() {
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val miseAJour: GestionnaireMiseAJour by lazy { GestionnaireMiseAJour(this, portee) }
}

val Context.carnet: CarnetApplication get() = applicationContext as CarnetApplication
