package io.github.slaynaw.carnetduraider

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Conseiller
import io.github.slaynaw.carnetduraider.core.DonneesJeu
import io.github.slaynaw.carnetduraider.core.ListeDeCourses
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Planificateur
import io.github.slaynaw.carnetduraider.core.Verdict

/** État de l'appli : données du jeu (embarquées), réserve et objectifs du joueur. */
class CarnetViewModel(application: Application) : AndroidViewModel(application) {
    val catalogue = Catalogue(DonneesJeu.embarquees())
    val planificateur = Planificateur(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur)
    private val memoire = Memoire(application)

    var reserve by mutableStateOf(memoire.lireReserve())
        private set
    var objectifs by mutableStateOf(memoire.lireObjectifs())
        private set

    fun possede(objetId: String): Int = reserve[objetId] ?: 0

    fun changerQuantite(objetId: String, delta: Int) {
        val nouvelle = (possede(objetId) + delta).coerceIn(0, MAX_QUANTITE)
        reserve = if (nouvelle == 0) reserve - objetId else reserve + (objetId to nouvelle)
        memoire.ecrireReserve(reserve)
    }

    fun suit(objectif: Objectif): Boolean = objectif in objectifs

    fun ajouterObjectif(objectif: Objectif) {
        if (objectif in objectifs) return
        objectifs = objectifs + objectif
        memoire.ecrireObjectifs(objectifs)
    }

    fun retirerObjectif(objectif: Objectif) {
        objectifs = objectifs - objectif
        memoire.ecrireObjectifs(objectifs)
    }

    fun listeDeCourses(): ListeDeCourses = planificateur.listeDeCourses(objectifs, reserve)

    fun verdict(objetId: String): Verdict = conseiller.verdict(objetId, possede(objetId), objectifs)

    private companion object {
        const val MAX_QUANTITE = 9999
    }
}
