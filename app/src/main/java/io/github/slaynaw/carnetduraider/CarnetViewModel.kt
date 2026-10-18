package io.github.slaynaw.carnetduraider

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.ConseilRaid
import io.github.slaynaw.carnetduraider.core.Conseiller
import io.github.slaynaw.carnetduraider.core.Etabli
import io.github.slaynaw.carnetduraider.core.ListeDeCourses
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Planificateur
import io.github.slaynaw.carnetduraider.core.PreparationRaid
import io.github.slaynaw.carnetduraider.core.Progression
import io.github.slaynaw.carnetduraider.core.Projet
import io.github.slaynaw.carnetduraider.core.Quantite
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
    val catalogue: Catalogue = application.carnet.catalogue
    val planificateur = Planificateur(catalogue)
    val suivi = SuiviProgression(catalogue)
    val preparationRaid = PreparationRaid(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur)
    private val memoire = Memoire(application)

    /** Mises à jour intégrées et calendrier : vivent avec l'appli, pas avec l'écran. */
    val miseAJour = (application as CarnetApplication).miseAJour
    val calendrier = application.carnet.calendrier
    val alertes = application.carnet.alertes
    val sauvegarde = application.carnet.sauvegarde

    var reserve by mutableStateOf(memoire.lireReserve())
        private set
    var progression by mutableStateOf(memoire.lireProgression())
        private set
    var objectifsManuels by mutableStateOf(memoire.lireObjectifs())
        private set
    /** Vrai une fois que le joueur a indiqué ses quêtes en cours (sinon, l'appli le lui propose). */
    val quetesIndiquees: Boolean get() = progression.quetesIndiquees
    var region by mutableStateOf(memoire.region)
        private set

    /** Objets cherchés pour le prochain raid, avec la quantité voulue. */
    var listeRaid by mutableStateOf(memoire.lireRaid())
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

    /**
     * Restaure une sauvegarde chiffrée : toutes les données de l'appli sont remplacées, puis relues.
     * Renvoie un message d'erreur, ou `null` si tout s'est bien passé.
     */
    suspend fun restaurerSauvegarde(uri: Uri, motDePasse: CharArray): String? {
        val erreur = sauvegarde.restaurer(uri, motDePasse)
        if (erreur == null) recharger()
        return erreur
    }

    /** Relit tout ce qui est enregistré (après une restauration). */
    private fun recharger() {
        reserve = memoire.lireReserve()
        progression = memoire.lireProgression()
        objectifsManuels = memoire.lireObjectifs()
        region = memoire.region
        listeRaid = memoire.lireRaid()
        derniereValidation = null
        alertes.recharger()
    }

    fun changerRaid(objetId: String, quantite: Int) {
        val n = quantite.coerceIn(0, MAX_QUANTITE)
        listeRaid = if (n == 0) listeRaid - objetId else listeRaid + (objetId to n)
        memoire.ecrireRaid(listeRaid)
    }

    fun ajouterAuRaid(objetId: String) = changerRaid(objetId, (listeRaid[objetId] ?: 0) + 1)

    /** Ajoute au raid tout ce qui manque à la liste de courses ; renvoie le nombre d'objets ajoutés. */
    fun ajouterListeDeCoursesAuRaid(): Int {
        val manquants = listeDeCourses().lignes.filter { it.manquant > 0 && (listeRaid[it.objetId] ?: 0) < it.manquant }
        if (manquants.isEmpty()) return 0
        listeRaid = listeRaid + manquants.map { it.objetId to it.manquant }
        memoire.ecrireRaid(listeRaid)
        return manquants.size
    }

    fun viderRaid() {
        listeRaid = emptyMap()
        memoire.ecrireRaid(listeRaid)
    }

    fun conseilRaid(): ConseilRaid = preparationRaid.conseiller(listeRaid.keys)

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

    /** Dernière validation, annulable quelques secondes (bandeau « Annuler »). */
    var derniereValidation by mutableStateOf<Validation?>(null)
        private set

    /**
     * Amélioration construite en jeu, validée en une touche : le niveau suivant devient l'objectif, et
     * ce que le jeu a pris est retiré de la réserve (dans la limite de ce qui y est noté).
     */
    fun validerEtabli(etabli: Etabli) {
        val niveau = suivi.prochainNiveau(progression, etabli) ?: return
        valider("${etabli.nom} · niveau ${niveau.niveau}", niveau.cout) {
            changerProgression(suivi.changerNiveau(progression, etabli, niveau.niveau))
        }
    }

    /** Correction du niveau, sans toucher à la réserve. */
    fun changerNiveau(etabli: Etabli, niveau: Int) {
        changerProgression(suivi.changerNiveau(progression, etabli, niveau))
        retirerAtteints()
    }

    fun suivreEtabli(etabliId: String, suivre: Boolean) = changerProgression(suivi.suivreEtabli(progression, etabliId, suivre))

    fun suivreTousLesEtablis(suivre: Boolean) = changerProgression(suivi.suivreTousLesEtablis(progression, suivre))

    /** Quête terminée en jeu, validée en une touche (objets demandés retirés de la réserve). */
    fun validerQuete(queteId: String) {
        val quete = catalogue.quete(queteId) ?: return
        valider(quete.nom, quete.objets) { changerProgression(suivi.terminerQuete(progression, queteId)) }
    }

    /** Étape de projet terminée en jeu, validée en une touche. */
    fun validerEtape(projet: Projet) {
        val etape = suivi.prochaineEtape(progression, projet) ?: return
        valider("${projet.nom} · ${planificateur.nomEtape(projet, etape)}", projet.etapes[etape].cout) {
            changerProgression(suivi.terminerEtape(progression, projet))
        }
    }

    fun changerEtapes(projet: Projet, faites: Int) {
        changerProgression(suivi.changerEtapes(progression, projet, faites))
        retirerAtteints()
    }

    fun suivreProjet(projetId: String, suivre: Boolean) = changerProgression(suivi.suivreProjet(progression, projetId, suivre))

    /** Annule la dernière validation : progression, réserve et objectifs reviennent comme avant. */
    fun annulerValidation() {
        val v = derniereValidation ?: return
        changerReserve(v.reserve)
        changerProgression(v.progression)
        objectifsManuels = v.objectifsManuels
        memoire.ecrireObjectifs(v.objectifsManuels)
        derniereValidation = null
    }

    fun oublierValidation() {
        derniereValidation = null
    }

    private fun valider(titre: String, cout: List<Quantite>, avancer: () -> Unit) {
        val avant = Validation(titre, 0, reserve, progression, objectifsManuels)
        val retires = cout.sumOf { minOf(possede(it.id), it.quantite) }
        changerReserve(suivi.retirer(reserve, cout))
        avancer()
        retirerAtteints()
        derniereValidation = avant.copy(objetsRetires = retires)
    }

    fun annulerQuete(queteId: String) = changerProgression(suivi.annulerQuete(progression, queteId))

    /** Mise en place : les quêtes affichées en cours dans le jeu. */
    fun indiquerQuetesEnCours(enCours: Collection<String>) {
        changerProgression(suivi.indiquerQuetesEnCours(progression, enCours))
        retirerAtteints()
    }

    fun changerRegion(nouvelle: Region) {
        region = nouvelle
        memoire.region = nouvelle
        alertes.replanifier()
    }

    /** Remet la réserve telle qu'elle était (annulation d'une saisie de retour de raid). */
    fun restaurerReserve(sauvegarde: Map<String, Int>) = changerReserve(sauvegarde)

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
                is Objectif.EtapeDeProjet -> (progression.etapesProjets[o.projetId] ?: 0) > o.etape
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

/** Une validation (établi, quête, étape de projet), avec l'état d'avant pour pouvoir l'annuler. */
data class Validation(
    val titre: String,
    val objetsRetires: Int,
    val reserve: Map<String, Int>,
    val progression: Progression,
    val objectifsManuels: List<Objectif>,
)
