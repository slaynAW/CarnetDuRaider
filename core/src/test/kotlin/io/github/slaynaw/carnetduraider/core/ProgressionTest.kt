package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Progression automatique, sur les vraies données du jeu. */
class ProgressionTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val suivi = SuiviProgression(catalogue)
    private val depart = Progression()

    @Test
    fun `enchainement des quetes`() {
        val histoire = catalogue.donnees.quetes.filter { it.histoire }
        assertTrue(histoire.size > 100, "quêtes de l'histoire importées")
        val ids = histoire.map { it.id }.toSet()
        histoire.forEach { q -> assertTrue(ids.containsAll(q.requiert), "prérequis connus pour ${q.id}") }

        // Au départ : seulement les quêtes sans prérequis, dont la première de l'histoire.
        val enCours = suivi.quetesEnCours(depart).map { it.id }
        assertTrue("picking_up_the_pieces" in enCours)
        assertFalse("off_the_radar" in enCours)
        assertTrue(suivi.quetesEnCours(depart).all { it.requiert.isEmpty() })
    }

    @Test
    fun `indiquer les quetes en cours termine celles qui y menent`() {
        val p = suivi.indiquerQuetesEnCours(depart, listOf("a_bad_feeling"))
        assertTrue("off_the_radar" in p.quetesTerminees)
        assertTrue("picking_up_the_pieces" in p.quetesTerminees)
        assertFalse("a_bad_feeling" in p.quetesTerminees)
        assertTrue("a_bad_feeling" in suivi.quetesEnCours(p).map { it.id })

        // Terminer : ses suites deviennent disponibles.
        val apres = suivi.terminerQuete(p, "a_bad_feeling")
        val suites = catalogue.donnees.quetes.filter { "a_bad_feeling" in it.requiert && it.requiert.all { r -> r in apres.quetesTerminees } }
        assertTrue(suites.isNotEmpty())
        assertTrue(suivi.quetesEnCours(apres).map { it.id }.containsAll(suites.map { it.id }))

        // Annuler : la quête et ce qui en dépend redeviennent à faire.
        val annule = suivi.annulerQuete(apres, "off_the_radar")
        assertFalse("off_the_radar" in annule.quetesTerminees)
        assertFalse("a_bad_feeling" in annule.quetesTerminees)
        assertTrue("picking_up_the_pieces" in annule.quetesTerminees)
    }

    @Test
    fun `etablis et objectifs automatiques`() {
        val coquillard = catalogue.etabli("scrappy")!!
        assertEquals(1, suivi.niveauDeDepart(coquillard))
        assertEquals(2, suivi.prochainNiveau(depart, coquillard)?.niveau)
        val station = catalogue.etabli("utility_bench")!!
        assertEquals(0, suivi.niveau(depart, station))

        // À l'installation, rien n'est suivi : liste de courses vide.
        assertTrue(suivi.objectifsAutomatiques(depart).isEmpty())

        val tous = suivi.suivreTousLesEtablis(depart, true)
        val objectifs = suivi.objectifsAutomatiques(tous)
        assertTrue(Objectif.NiveauDEtabli("utility_bench", 1) in objectifs)
        assertTrue(Objectif.NiveauDEtabli("scrappy", 2) in objectifs)
        assertFalse(objectifs.any { it is Objectif.QueteATerminer }, "quêtes pas encore indiquées")

        val auMax = suivi.changerNiveau(tous, station, 99)
        assertEquals(3, suivi.niveau(auMax, station))
        assertNull(suivi.prochainNiveau(auMax, station))
        assertFalse(suivi.objectifsAutomatiques(auMax).any { it is Objectif.NiveauDEtabli && it.etabliId == "utility_bench" })

        val ignore = suivi.suivreEtabli(tous, "scrappy", false)
        assertFalse(suivi.objectifsAutomatiques(ignore).any { it is Objectif.NiveauDEtabli && it.etabliId == "scrappy" })
        val unSeul = suivi.suivreEtabli(depart, "scrappy", true)
        assertEquals(listOf(Objectif.NiveauDEtabli("scrappy", 2)), suivi.objectifsAutomatiques(unSeul))
        assertTrue(suivi.objectifsAutomatiques(suivi.suivreTousLesEtablis(tous, false)).isEmpty())
    }

    @Test
    fun `quetes suivies une fois indiquees`() {
        val premiere = suivi.quetesEnCours(depart).first()
        val indiquees = suivi.indiquerQuetesEnCours(depart, listOf(premiere.id))
        assertTrue(indiquees.quetesIndiquees)
        val attendues = suivi.quetesEnCours(indiquees).filter { it.objets.isNotEmpty() }.map { Objectif.QueteATerminer(it.id) }
        assertEquals(attendues, suivi.objectifsAutomatiques(indiquees).filterIsInstance<Objectif.QueteATerminer>())
    }

    @Test
    fun `objectifs complets termines d'eux-memes`() {
        val planificateur = Planificateur(catalogue)
        val station = catalogue.etabli("utility_bench")!!
        val suit = suivi.suivreEtabli(depart, station.id, true)
        val niveau1 = Objectif.NiveauDEtabli(station.id, 1)
        val niveau2 = Objectif.NiveauDEtabli(station.id, 2)
        // Tout le niveau 1 rapporté, et tout le niveau 2 aussi (ajouté à la main comme objectif plus lointain).
        val apports = listOf(niveau1, niveau2).associate { o -> o.cle to planificateur.besoins(o).associate { it.id to it.quantite } }
        val (apres, termines) = suivi.terminerLesComplets(suit, listOf(niveau2)) { planificateur.complet(it, apports) }
        assertEquals(listOf(niveau1, niveau2), termines)
        assertEquals(2, suivi.niveau(apres, station))
        assertEquals(listOf(Objectif.NiveauDEtabli(station.id, 3)), suivi.objectifsAutomatiques(apres))
        // Rien de complet : rien ne change.
        assertEquals(suit to emptyList<Objectif>(), suivi.terminerLesComplets(suit, emptyList()) { false })
    }

    @Test
    fun `un objectif lointain complet attend son tour`() {
        val niveau3 = Objectif.NiveauDEtabli("utility_bench", 3)
        assertFalse(suivi.atteignable(depart, niveau3))
        assertEquals(depart to emptyList<Objectif>(), suivi.terminerLesComplets(depart, listOf(niveau3)) { it == niveau3 })
    }

    @Test
    fun `quete terminee d'elle-meme quand ses objets sont rapportes`() {
        val avant = suivi.indiquerQuetesEnCours(depart, listOf("clearer_skies"))
        val quete = Objectif.QueteATerminer("clearer_skies")
        assertTrue(suivi.atteignable(avant, quete))
        val (apres, termines) = suivi.terminerLesComplets(avant, emptyList()) { it == quete }
        assertEquals(listOf(quete), termines)
        assertTrue("clearer_skies" in apres.quetesTerminees)
        assertFalse(Objectif.QueteATerminer("clearer_skies") in suivi.objectifsAutomatiques(apres))
        // Sa suite arrive dès que tous ses prérequis sont faits (« off_the_radar » demande aussi « trash_into_treasure »).
        val apres2 = suivi.terminer(apres, Objectif.QueteATerminer("trash_into_treasure"))
        assertTrue(suivi.quetesEnCours(apres2).any { it.id == "off_the_radar" })
    }

    @Test
    fun `projets suivis et etapes`() {
        val projet = catalogue.projet("trophy_display_project")!!
        assertEquals("Présentoir à trophées", projet.nom)
        assertFalse(suivi.objectifsAutomatiques(depart).any { it is Objectif.EtapeDeProjet }, "aucun projet suivi par défaut")

        val suit = suivi.suivreProjet(depart, projet.id, true)
        assertTrue(Objectif.EtapeDeProjet(projet.id, 0) in suivi.objectifsAutomatiques(suit))
        assertEquals("Présentoir à trophées · Menaces errantes", Planificateur(catalogue).libelle(Objectif.EtapeDeProjet(projet.id, 0)))

        var p = suit
        repeat(projet.etapes.size) { p = suivi.terminerEtape(p, projet) }
        assertNull(suivi.prochaineEtape(p, projet))
        assertFalse(suivi.objectifsAutomatiques(p).any { it is Objectif.EtapeDeProjet })
        assertEquals(projet.etapes.size, suivi.terminerEtape(p, projet).etapesProjets[projet.id], "jamais au-delà de la dernière étape")
    }
}
