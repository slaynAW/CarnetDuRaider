package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Progression : tout est fait d'office, sauf ce que le joueur indique en cours ; la suite s'ajoute seule. */
class ProgressionTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val suivi = SuiviProgression(catalogue)
    private val depart = Progression()

    @Test
    fun `a l'installation rien n'est en cours`() {
        assertTrue(suivi.objectifs(depart).isEmpty())
        assertTrue(suivi.quetesEnCours(depart).isEmpty())
    }

    @Test
    fun `enchainement des quetes`() {
        val histoire = catalogue.donnees.quetes.filter { it.histoire }
        assertTrue(histoire.size > 100, "quêtes de l'histoire importées")
        val ids = histoire.map { it.id }.toSet()
        histoire.forEach { q -> assertTrue(ids.containsAll(q.requiert), "prérequis connus pour ${q.id}") }
        assertTrue("picking_up_the_pieces" in suivi.precedentes("off_the_radar"))
        assertTrue("a_bad_feeling" in suivi.suivantes("off_the_radar"))
    }

    @Test
    fun `quetes indiquees en cours, tout le reste est fait d'office`() {
        val p = suivi.indiquerQuetesEnCours(depart, listOf("a_bad_feeling"))
        assertTrue(p.quetesIndiquees)
        assertEquals(listOf("a_bad_feeling"), suivi.quetesEnCours(p).map { it.id })
        assertTrue("off_the_radar" in p.quetesTerminees)
        assertTrue("picking_up_the_pieces" in p.quetesTerminees)
        // Une quête d'une autre branche, jamais indiquée, est faite d'office.
        val autre = catalogue.donnees.quetes.first { it.histoire && it.id != "a_bad_feeling" && it.id !in suivi.suivantes("a_bad_feeling") && it.id !in suivi.precedentes("a_bad_feeling") }
        assertTrue(autre.id in p.quetesTerminees)
        // Ce qui vient après n'est pas fait : la suite arrivera.
        assertTrue(suivi.suivantes("a_bad_feeling").none { it in p.quetesTerminees })
        assertEquals(listOf(Objectif.QueteATerminer("a_bad_feeling")), suivi.objectifs(p))
    }

    @Test
    fun `une quete cochee qui mene a une autre quete cochee est faite`() {
        val p = suivi.indiquerQuetesEnCours(depart, listOf("off_the_radar", "a_bad_feeling"))
        assertEquals(listOf("a_bad_feeling"), suivi.quetesEnCours(p).map { it.id })
        assertTrue("off_the_radar" in p.quetesTerminees)
    }

    @Test
    fun `quete terminee, la suite s'ajoute`() {
        val p = suivi.indiquerQuetesEnCours(depart, listOf("a_bad_feeling"))
        val apres = suivi.terminer(p, Objectif.QueteATerminer("a_bad_feeling"))
        val suites = catalogue.donnees.quetes.filter { it.histoire && "a_bad_feeling" in it.requiert && it.requiert.all { r -> r in apres.quetesTerminees } }
        assertTrue(suites.isNotEmpty())
        assertEquals(suites.map { it.id }.toSet(), suivi.quetesEnCours(apres).map { it.id }.toSet())
        // Plus aucune quête en cours : rien.
        assertTrue(suivi.objectifs(suivi.indiquerQuetesEnCours(depart, emptyList())).isEmpty())
    }

    @Test
    fun `atelier - niveau en cours, puis le suivant`() {
        val coquillard = catalogue.etabli("scrappy")!!
        assertEquals(1, suivi.niveauDeDepart(coquillard))
        assertNull(suivi.niveauEnCours(depart, coquillard), "rien en cours par défaut")
        val station = catalogue.etabli("utility_bench")!!

        val p = suivi.indiquerNiveauEnCours(depart, station, 2)
        assertEquals(2, suivi.niveauEnCours(p, station))
        assertEquals(1, suivi.niveau(p, station))
        assertEquals(listOf(Objectif.NiveauDEtabli(station.id, 2)), suivi.objectifs(p))

        val apres = suivi.terminer(p, Objectif.NiveauDEtabli(station.id, 2))
        assertEquals(listOf(Objectif.NiveauDEtabli(station.id, 3)), suivi.objectifs(apres))
        val auMax = suivi.terminer(apres, Objectif.NiveauDEtabli(station.id, 3))
        assertNull(suivi.niveauEnCours(auMax, station))
        assertTrue(suivi.objectifs(auMax).isEmpty())

        val retire = suivi.indiquerNiveauEnCours(p, station, null)
        assertTrue(suivi.objectifs(retire).isEmpty())
        // Un niveau impossible est ramené aux niveaux du jeu.
        assertEquals(2, suivi.niveauEnCours(suivi.indiquerNiveauEnCours(depart, coquillard, 0), coquillard))
    }

    @Test
    fun `projets et expeditions - etape en cours, puis la suivante`() {
        val projet = catalogue.projet("trophy_display_project")!!
        assertEquals("Présentoir à trophées", projet.nom)
        assertFalse(projet.expedition)
        assertTrue(catalogue.donnees.projets.filter { it.expedition }.all { it.nom.startsWith("Expédition") })
        assertEquals(5, catalogue.donnees.projets.count { it.expedition })

        val p = suivi.indiquerEtapeEnCours(depart, projet, 1)
        assertEquals(listOf(Objectif.EtapeDeProjet(projet.id, 1)), suivi.objectifs(p))
        assertEquals("Présentoir à trophées · Menaces errantes", Planificateur(catalogue).libelle(Objectif.EtapeDeProjet(projet.id, 0)))

        var fin = p
        for (etape in 1 until projet.etapes.size) fin = suivi.terminer(fin, Objectif.EtapeDeProjet(projet.id, etape))
        assertNull(suivi.etapeEnCours(fin, projet))
        assertTrue(suivi.objectifs(fin).isEmpty())
        assertEquals(projet.etapes.size, suivi.terminer(fin, Objectif.EtapeDeProjet(projet.id, 9)).etapesProjets[projet.id], "jamais au-delà de la dernière étape")
        assertTrue(suivi.objectifs(suivi.indiquerEtapeEnCours(p, projet, null)).isEmpty())
    }

    @Test
    fun `objectifs complets termines d'eux-memes, puis la suite`() {
        val planificateur = Planificateur(catalogue)
        val station = catalogue.etabli("utility_bench")!!
        val p = suivi.indiquerNiveauEnCours(depart, station, 1)
        val niveau1 = Objectif.NiveauDEtabli(station.id, 1)
        val niveau2 = Objectif.NiveauDEtabli(station.id, 2)
        // Tout le niveau 1 rapporté, et le niveau 2 aussi : les deux se terminent, le 3 arrive.
        val apports = listOf(niveau1, niveau2).associate { o -> o.cle to planificateur.besoins(o).associate { it.id to it.quantite } }
        val (apres, termines) = suivi.terminerLesComplets(p) { planificateur.complet(it, apports) }
        assertEquals(listOf(niveau1, niveau2), termines)
        assertEquals(2, suivi.niveau(apres, station))
        assertEquals(listOf(Objectif.NiveauDEtabli(station.id, 3)), suivi.objectifs(apres))
        // Rien de complet : rien ne change.
        assertEquals(p to emptyList<Objectif>(), suivi.terminerLesComplets(p) { false })
    }

    @Test
    fun `un objectif lointain n'est pas la prochaine chose a faire`() {
        val niveau3 = Objectif.NiveauDEtabli("utility_bench", 3)
        assertFalse(suivi.atteignable(depart, niveau3))
        assertEquals(depart to emptyList<Objectif>(), suivi.terminerLesComplets(depart) { it == niveau3 })
    }

    @Test
    fun `quete terminee d'elle-meme quand ses objets sont rapportes`() {
        val avant = suivi.indiquerQuetesEnCours(depart, listOf("clearer_skies"))
        val quete = Objectif.QueteATerminer("clearer_skies")
        assertTrue(suivi.atteignable(avant, quete))
        val (apres, termines) = suivi.terminerLesComplets(avant) { it == quete }
        assertEquals(listOf(quete), termines)
        assertTrue("clearer_skies" in apres.quetesTerminees)
        assertFalse(quete in suivi.objectifs(apres))
        // « off_the_radar » demande aussi « trash_into_treasure », fait d'office : elle arrive aussitôt.
        assertTrue(suivi.quetesEnCours(apres).any { it.id == "off_the_radar" })
    }
}
