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
    fun `retirer de la reserve ce qui est consomme`() {
        val reserve = mapOf("metal_parts" to 30, "fabric" to 5)
        val apres = suivi.retirer(reserve, listOf(Quantite("metal_parts", 25), Quantite("fabric", 9), Quantite("rubber_parts", 1)))
        assertEquals(mapOf("metal_parts" to 5), apres)
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

    @Test
    fun `valider sans avoir saisi tous les objets`() {
        // Seul ce qui est noté dans la réserve est retiré, sans jamais passer sous zéro.
        val apres = suivi.retirer(mapOf("metal_parts" to 3), listOf(Quantite("metal_parts", 25), Quantite("fabric", 30)))
        assertTrue(apres.isEmpty())
    }
}
