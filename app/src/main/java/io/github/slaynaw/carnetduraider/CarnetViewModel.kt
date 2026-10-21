package io.github.slaynaw.carnetduraider

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.slaynaw.carnetduraider.core.Apports
import io.github.slaynaw.carnetduraider.core.BilanRaid
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.ConseilRaid
import io.github.slaynaw.carnetduraider.core.Conseiller
import io.github.slaynaw.carnetduraider.core.Etabli
import io.github.slaynaw.carnetduraider.core.ListeDeCourses
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.ObjetATrouver
import io.github.slaynaw.carnetduraider.core.Planificateur
import io.github.slaynaw.carnetduraider.core.PreparationRaid
import io.github.slaynaw.carnetduraider.core.Progression
import io.github.slaynaw.carnetduraider.core.Projet
import io.github.slaynaw.carnetduraider.core.Rapporte
import io.github.slaynaw.carnetduraider.core.RetourDeRaid
import io.github.slaynaw.carnetduraider.core.SuiviProgression
import io.github.slaynaw.carnetduraider.core.Usages
import io.github.slaynaw.carnetduraider.core.UsagesObjet
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.DonneesCalendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Region

/**
 * État de l'appli : données du jeu (embarquées) et progression du joueur. Les objectifs suivis sont
 * choisis par l'appli d'après la progression (prochain niveau de chaque établi, quêtes en cours), plus
 * ceux que le joueur ajoute lui-même. Il n'y a pas de réserve : le joueur note seulement ce qu'il rapporte
 * de raid, qui va aux objectifs qui le demandent ; un objectif complet se termine tout seul.
 */
class CarnetViewModel(application: Application) : AndroidViewModel(application) {
    val catalogue: Catalogue = application.carnet.catalogue
    val planificateur = Planificateur(catalogue)
    val suivi = SuiviProgression(catalogue)
    val preparationRaid = PreparationRaid(catalogue)
    val usages = Usages(catalogue, suivi)
    private val conseiller = Conseiller(catalogue, planificateur, usages)
    private val memoire = Memoire(application)

    /** Mises à jour intégrées et calendrier : vivent avec l'appli, pas avec l'écran. */
    val miseAJour = (application as CarnetApplication).miseAJour
    val calendrier = application.carnet.calendrier
    val alertes = application.carnet.alertes
    val sauvegarde = application.carnet.sauvegarde

    var progression by mutableStateOf(memoire.lireProgression())
        private set
    var objectifsManuels by mutableStateOf(memoire.lireObjectifs())
        private set
    /** Vrai une fois que le joueur a indiqué ses quêtes en cours (sinon, l'appli le lui propose). */
    val quetesIndiquees: Boolean get() = progression.quetesIndiquees
    var region by mutableStateOf(memoire.region)
        private set

    /** Ce qui a été rapporté de raid pour chaque objectif (clé de l'objectif → objets rapportés). */
    var apports by mutableStateOf(memoire.lireApports())
        private set

    /** Objets cherchés pour le prochain raid, avec la quantité voulue. */
    var listeRaid by mutableStateOf(memoire.lireRaid())
        private set

    /** Pour chaque objet de [listeRaid] : combien le joueur en a rapporté depuis (le décompte). */
    var trouvesRaid by mutableStateOf(memoire.lireTrouvesRaid())
        private set

    init {
        // Plus de réserve : ce qui y était noté est oublié, on repart de zéro.
        memoire.oublierReserve()
    }

    /** Objectifs choisis par l'appli d'après la progression. */
    val objectifsAutomatiques: List<Objectif> get() = suivi.objectifsAutomatiques(progression)

    /** Tous les objectifs suivis : automatiques, puis ajoutés à la main. */
    val objectifs: List<Objectif> get() = (objectifsAutomatiques + objectifsManuels).distinct()

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
        memoire.oublierReserve()
        progression = memoire.lireProgression()
        objectifsManuels = memoire.lireObjectifs()
        region = memoire.region
        apports = memoire.lireApports()
        listeRaid = memoire.lireRaid()
        trouvesRaid = memoire.lireTrouvesRaid()
        retourOuvert = null
        saisis = emptyMap()
        derniereValidation = null
        alertes.recharger()
    }

    fun changerRaid(objetId: String, quantite: Int) {
        val n = quantite.coerceIn(0, MAX_QUANTITE)
        listeRaid = if (n == 0) listeRaid - objetId else listeRaid + (objetId to n)
        memoire.ecrireRaid(listeRaid)
        // Retiré de la liste : son décompte repart de zéro s'il y revient.
        if (n == 0) changerTrouvesRaid(trouvesRaid - objetId)
    }

    fun ajouterAuRaid(objetId: String) = changerRaid(objetId, (listeRaid[objetId] ?: 0) + 1)

    private fun changerTrouvesRaid(nouveaux: Map<String, Int>) {
        if (nouveaux == trouvesRaid) return
        trouvesRaid = nouveaux
        memoire.ecrireTrouvesRaid(nouveaux)
    }

    private fun changerApports(nouveaux: Apports) {
        if (nouveaux == apports) return
        apports = nouveaux
        memoire.ecrireApports(nouveaux)
    }

    /** Toute la liste du raid, objets déjà trouvés compris : objectifs suivis et objets ajoutés à la main. */
    fun listeDuRaid(): List<ObjetATrouver> = planificateur.listeDuRaid(objectifs, apports, listeRaid, trouvesRaid)

    /** Ce qu'il reste à trouver : ce qui manque pour les objectifs suivis et les objets ajoutés à la main. */
    fun aTrouver(): List<ObjetATrouver> = planificateur.aTrouver(objectifs, apports, listeRaid, trouvesRaid)

    /** Retour de raid en cours : ce qui était rapporté et la liste du raid à l'ouverture (bilan, annulation). */
    class RetourOuvert(val avant: Rapporte, val liste: List<ObjetATrouver>)

    private var retourOuvert: RetourOuvert? = null

    /** Butin noté pendant le retour de raid en cours (identifiant → quantité). */
    var saisis by mutableStateOf(emptyMap<String, Int>())
        private set

    /** Ouvre le retour de raid, ou reprend celui qui est en cours. */
    fun ouvrirRetourDeRaid(): RetourOuvert =
        retourOuvert ?: RetourOuvert(Rapporte(apports, trouvesRaid), listeDuRaid()).also { retourOuvert = it }

    /**
     * Retour de raid : [delta] objets rapportés de plus (ou de moins), sans dépasser ce qui manquait à
     * l'ouverture. Le butin va aussitôt aux objectifs qui le demandent ([Planificateur.repartir]).
     */
    fun noter(objetId: String, delta: Int) {
        val r = retourOuvert ?: return
        val max = r.liste.firstOrNull { it.objetId == objetId }?.reste ?: 0
        val n = ((saisis[objetId] ?: 0) + delta).coerceIn(0, max)
        saisis = if (n == 0) saisis - objetId else saisis + (objetId to n)
        val rapporte = planificateur.repartir(objectifs, listeRaid, r.avant, saisis)
        changerApports(rapporte.apports)
        changerTrouvesRaid(rapporte.trouvesRaid)
    }

    /** Remet ce qui était rapporté comme à l'ouverture du retour de raid. */
    fun annulerRetourDeRaid() {
        val r = retourOuvert ?: return
        saisis = emptyMap()
        changerApports(r.avant.apports)
        changerTrouvesRaid(r.avant.trouvesRaid)
    }

    /** Ce que l'animation de fin de retour de raid montre : objets trouvés, objectifs terminés, raid terminé. */
    var celebration by mutableStateOf<BilanRaid?>(null)
        private set

    /**
     * Retour de raid validé : les objectifs dont tout a été rapporté se terminent d'eux-mêmes (la suite
     * apparaît), les objets ajoutés à la main et trouvés quittent la liste, et l'animation montre le bilan.
     * « Annuler » remet tout comme avant le retour de raid.
     */
    fun validerRetourDeRaid() {
        val r = retourOuvert ?: return
        retourOuvert = null
        val notes = saisis.values.sum()
        saisis = emptyMap()
        val avant = Validation("Retour de raid", progression, objectifsManuels, r.avant.apports, listeRaid, r.avant.trouvesRaid)
        val apres = listeDuRaid()
        // Même sans rien de noté (appli fermée par Android en plein retour), ce qui est complet se termine.
        val termines = terminerLesComplets()
        val (raid, trouves) = RetourDeRaid.sansLesTrouves(listeRaid, trouvesRaid)
        if (raid != listeRaid) {
            listeRaid = raid
            memoire.ecrireRaid(raid)
        }
        changerTrouvesRaid(trouves)
        val bilan = RetourDeRaid.bilan(r.liste, apres, termines)
        if (!bilan.vide) celebration = bilan
        if (notes > 0 || termines.isNotEmpty()) derniereValidation = avant.copy(objetsNotes = notes, termines = termines)
    }

    fun finCelebration() {
        celebration = null
    }

    /** Où aller pour trouver ce qui manque encore. */
    fun conseilRaid(): ConseilRaid = preparationRaid.conseiller(aTrouver().map { it.objetId })

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

    /** Amélioration construite en jeu, validée en une touche : le niveau suivant devient l'objectif. */
    fun validerEtabli(etabli: Etabli) {
        val niveau = suivi.prochainNiveau(progression, etabli) ?: return
        valider(Objectif.NiveauDEtabli(etabli.id, niveau.niveau), "${etabli.nom} · niveau ${niveau.niveau}")
    }

    /** Correction du niveau, sans rien valider. */
    fun changerNiveau(etabli: Etabli, niveau: Int) {
        changerProgression(suivi.changerNiveau(progression, etabli, niveau))
        retirerAtteints()
    }

    fun suivreEtabli(etabliId: String, suivre: Boolean) = changerProgression(suivi.suivreEtabli(progression, etabliId, suivre))

    fun suivreTousLesEtablis(suivre: Boolean) = changerProgression(suivi.suivreTousLesEtablis(progression, suivre))

    /** Quête terminée en jeu, validée en une touche. */
    fun validerQuete(queteId: String) {
        val quete = catalogue.quete(queteId) ?: return
        valider(Objectif.QueteATerminer(queteId), quete.nom)
    }

    /** Étape de projet terminée en jeu, validée en une touche. */
    fun validerEtape(projet: Projet) {
        val etape = suivi.prochaineEtape(progression, projet) ?: return
        valider(Objectif.EtapeDeProjet(projet.id, etape), "${projet.nom} · ${planificateur.nomEtape(projet, etape)}")
    }

    fun changerEtapes(projet: Projet, faites: Int) {
        changerProgression(suivi.changerEtapes(progression, projet, faites))
        retirerAtteints()
    }

    fun suivreProjet(projetId: String, suivre: Boolean) = changerProgression(suivi.suivreProjet(progression, projetId, suivre))

    /** Annule la dernière validation : progression, objectifs et ce qui avait été rapporté reviennent comme avant. */
    fun annulerValidation() {
        val v = derniereValidation ?: return
        changerProgression(v.progression)
        objectifsManuels = v.objectifsManuels
        memoire.ecrireObjectifs(v.objectifsManuels)
        changerApports(v.apports)
        listeRaid = v.listeRaid
        memoire.ecrireRaid(v.listeRaid)
        changerTrouvesRaid(v.trouvesRaid)
        derniereValidation = null
    }

    fun oublierValidation() {
        derniereValidation = null
    }

    /**
     * Objectif fait en jeu (« C'est fait »), même sans avoir tout noté : la suite devient l'objectif, et ce
     * qui avait été rapporté pour lui est oublié. La suite peut être déjà complète : elle se termine aussi.
     */
    private fun valider(objectif: Objectif, titre: String) {
        val avant = Validation(titre, progression, objectifsManuels, apports, listeRaid, trouvesRaid)
        changerProgression(suivi.terminer(progression, objectif))
        changerApports(RetourDeRaid.sansLesTermines(apports, listOf(objectif)))
        retirerAtteints()
        derniereValidation = avant.copy(termines = terminerLesComplets())
    }

    /** Termine d'eux-mêmes les objectifs dont tout a été rapporté ; renvoie ceux qui sont terminés. */
    private fun terminerLesComplets(): List<Objectif> {
        val (nouvelle, termines) = suivi.terminerLesComplets(progression, objectifsManuels) { planificateur.complet(it, apports) }
        if (termines.isNotEmpty()) {
            changerProgression(nouvelle)
            changerApports(RetourDeRaid.sansLesTermines(apports, termines))
            retirerAtteints()
        }
        return termines
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

    fun calendrierOfficiel(donnees: DonneesCalendrier): Calendrier = Calendrier(donnees, catalogue)

    fun listeDeCourses(): ListeDeCourses = planificateur.listeDeCourses(objectifs, apports)

    fun verdict(objetId: String): Verdict = conseiller.verdict(objetId, objectifs, apports, progression, listeRaid, trouvesRaid)

    /** À quoi sert l'objet, compte tenu de la progression (ce qui est fait ne compte plus). */
    fun usagesDe(objetId: String): UsagesObjet = usages.pour(objetId, progression)

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

/** Une validation (établi, quête, étape de projet, retour de raid), avec l'état d'avant pour pouvoir l'annuler. */
data class Validation(
    val titre: String,
    val progression: Progression,
    val objectifsManuels: List<Objectif>,
    val apports: Apports,
    val listeRaid: Map<String, Int>,
    val trouvesRaid: Map<String, Int>,
    /** Objets notés au retour de raid. */
    val objetsNotes: Int = 0,
    /** Objectifs terminés d'eux-mêmes, tout ayant été rapporté. */
    val termines: List<Objectif> = emptyList(),
)
