package io.github.slaynaw.carnetduraider

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Conseiller
import io.github.slaynaw.carnetduraider.core.DonneesJeu
import io.github.slaynaw.carnetduraider.core.Etabli
import io.github.slaynaw.carnetduraider.core.ListeDeCourses
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Planificateur
import io.github.slaynaw.carnetduraider.core.Progression
import io.github.slaynaw.carnetduraider.core.SuiviProgression
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.DonneesCalendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Region

/**
 * État de l'appli : données du jeu (embarquées), réserve et progression du joueur. Les objectifs
 * suivis sont choisis par l'appli d'après la progression (prochain niveau de chaque établi, quêtes en
 * cours), plus ceux que le joueur ajoute lui-même.
 */
class CarnetViewModel(application: Application) : AndroidViewModel(application) {
    val catalogue = Catalogue(DonneesJeu.embarquees())
    val planificateur = Planificateur(catalogue)
    val suivi = SuiviProgression(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur)
    private val memoire = Memoire(application)

    /** Mises à jour intégrées et calendrier : vivent avec l'appli, pas avec l'écran. */
    val miseAJour = (application as CarnetApplication).miseAJour
    val calendrier = application.carnet.calendrier

    var reserve by mutableStateOf(memoire.lireReserve())
        private set
    var progression by mutableStateOf(memoire.lireProgression())
        private set
    var objectifsManuels by mutableStateOf(memoire.lireObjectifs())
        private set
    var quetesIndiquees by mutableStateOf(memoire.quetesIndiquees)
        private set
    var region by mutableStateOf(memoire.region)
        private set

    /** Objectifs choisis par l'appli d'après la progression. */
    val objectifsAutomatiques: List<Objectif> get() = suivi.objectifsAutomatiques(progression)

    /** Tous les objectifs suivis : automatiques, puis ajoutés à la main. */
    val objectifs: List<Objectif> get() = (objectifsAutomatiques + objectifsManuels).distinct()

    fun possede(objetId: String): Int = reserve[objetId] ?: 0

    fun changerQuantite(objetId: String, delta: Int) {
        val nouvelle = (possede(objetId) + delta).coerceIn(0, MAX_QUANTITE)
        changerReserve(if (nouvelle == 0) reserve - objetId else reserve + (objetId to nouvelle))
    }

    fun suit(objectif: Objectif): Boolean = objectif in objectifs

    fun ajouterObjectif(objectif: Objectif) {
        if (objectif in objectifs) return
        objectifsManuels = objectifsManuels + objectif
        memoire.ecrireObjectifs(objectifsManuels)
    }

    fun retirerObjectif(objectif: Objectif) {
        objectifsManuels = objectifsManuels - objectif
        memoire.ecrireObjectifs(objectifsManuels)
    }

    /** Amélioration construite en jeu (avec [retirerCout], le coût est déduit de la réserve). */
    fun ameliorer(etabli: Etabli, retirerCout: Boolean) {
        val niveau = suivi.prochainNiveau(progression, etabli) ?: return
        if (retirerCout) changerReserve(suivi.retirer(reserve, niveau.cout))
        changerProgression(suivi.changerNiveau(progression, etabli, niveau.niveau))
        retirerAtteints()
    }

    /** Correction du niveau, sans toucher à la réserve. */
    fun changerNiveau(etabli: Etabli, niveau: Int) {
        changerProgression(suivi.changerNiveau(progression, etabli, niveau))
        retirerAtteints()
    }

    fun suivreEtabli(etabliId: String, suivre: Boolean) = changerProgression(suivi.suivreEtabli(progression, etabliId, suivre))

    /** Quête terminée en jeu (avec [retirerObjets], les objets demandés sont déduits de la réserve). */
    fun terminerQuete(queteId: String, retirerObjets: Boolean) {
        if (retirerObjets) changerReserve(suivi.retirer(reserve, catalogue.quete(queteId)?.objets.orEmpty()))
        changerProgression(suivi.terminerQuete(progression, queteId))
        retirerAtteints()
    }

    fun annulerQuete(queteId: String) = changerProgression(suivi.annulerQuete(progression, queteId))

    /** Mise en place : les quêtes affichées en cours dans le jeu. */
    fun indiquerQuetesEnCours(enCours: Collection<String>) {
        changerProgression(suivi.indiquerQuetesEnCours(progression, enCours))
        quetesIndiquees = true
        memoire.quetesIndiquees = true
        retirerAtteints()
    }

    fun changerRegion(nouvelle: Region) {
        region = nouvelle
        memoire.region = nouvelle
    }

    fun calendrierOfficiel(donnees: DonneesCalendrier): Calendrier = Calendrier(donnees, catalogue)

    fun listeDeCourses(): ListeDeCourses = planificateur.listeDeCourses(objectifs, reserve)

    fun verdict(objetId: String): Verdict = conseiller.verdict(objetId, possede(objetId), objectifs)

    private fun changerReserve(nouvelle: Map<String, Int>) {
        reserve = nouvelle
        memoire.ecrireReserve(nouvelle)
    }

    private fun changerProgression(nouvelle: Progression) {
        progression = nouvelle
        memoire.ecrireProgression(nouvelle)
    }

    /** Un objectif ajouté à la main et déjà atteint (niveau construit, quête terminée) n'est plus suivi. */
    private fun retirerAtteints() {
        val restants = objectifsManuels.filterNot { o ->
            when (o) {
                is Objectif.NiveauDEtabli -> catalogue.etabli(o.etabliId)?.let { suivi.niveau(progression, it) >= o.niveau } ?: false
                is Objectif.QueteATerminer -> o.queteId in progression.quetesTerminees
            }
        }
        if (restants != objectifsManuels) {
            objectifsManuels = restants
            memoire.ecrireObjectifs(restants)
        }
    }

    private companion object {
        const val MAX_QUANTITE = 9999
    }
}
