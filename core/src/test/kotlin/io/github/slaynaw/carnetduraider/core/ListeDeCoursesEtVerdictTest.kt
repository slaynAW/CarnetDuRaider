package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Liste de courses et verdict, sur les vraies données du jeu 2.0. */
class ListeDeCoursesEtVerdictTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val planificateur = Planificateur(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur)

    private val stationUtilitaire1 = Objectif.NiveauDEtabli("utility_bench", 1)
    private val cielDegage = Objectif.QueteATerminer("clearer_skies")
    private val armurier2 = Objectif.NiveauDEtabli("weapon_bench", 2)
    private val objectifs = listOf(stationUtilitaire1, cielDegage, armurier2)

    @Test
    fun `libelles des objectifs avec les noms officiels`() {
        assertEquals("Station utilitaire · niveau 1", planificateur.libelle(stationUtilitaire1))
        assertEquals("Quête «\u00a0Ciel dégagé\u00a0»", planificateur.libelle(cielDegage))
    }

    @Test
    fun `la liste regroupe les besoins par objet`() {
        val liste = planificateur.listeDeCourses(objectifs, mapOf("arc_alloy" to 5, "mechanical_components" to 5))
        val alliage = liste.lignes.single { it.objetId == "arc_alloy" }
        // 6 pour la Station utilitaire niveau 1 et 3 pour « Ciel dégagé ».
        assertEquals(9, alliage.besoin)
        assertEquals(4, alliage.manquant)
        assertEquals(listOf(6, 3), alliage.pour.map { it.quantite })

        val plastique = liste.lignes.single { it.objetId == "plastic_parts" }
        assertEquals("Composants plastiques", plastique.nom)
        assertEquals(50, plastique.manquant)

        // Les objets complets passent en fin de liste.
        assertTrue(liste.lignes.last().complet)
        assertEquals("mechanical_components", liste.lignes.last().objetId)
        assertEquals(4 + 50 + 3 + 8, liste.objetsManquants)
    }

    @Test
    fun `progression de la liste`() {
        assertEquals(0.0, planificateur.listeDeCourses(listOf(cielDegage), emptyMap()).progression)
        assertEquals(1.0, planificateur.listeDeCourses(listOf(cielDegage), mapOf("arc_alloy" to 7)).progression)
        assertEquals(1.0, planificateur.listeDeCourses(emptyList(), emptyMap()).progression)
    }

    @Test
    fun `verdict garder tant qu'il en manque`() {
        val v = conseiller.verdict("arc_alloy", possede = 5, objectifs = objectifs)
        v as Verdict.Garder
        assertEquals(4, v.manquant)
        assertEquals(9, v.besoin)
    }

    @Test
    fun `verdict suffisant avec le surplus a vendre`() {
        val v = conseiller.verdict("arc_alloy", possede = 12, objectifs = objectifs)
        v as Verdict.Suffisant
        assertEquals(3, v.surplus)
        // 200 pièces l'unité contre 2 × Pièces métalliques (75) au recyclage : mieux vaut vendre.
        assertEquals(Sortie.VENDRE, v.conseil)
    }

    @Test
    fun `verdict inutile quand aucun objectif n'en demande`() {
        val v = conseiller.verdict("arc_alloy", possede = 3, objectifs = listOf(armurier2))
        assertEquals(Verdict.Inutile(Sortie.VENDRE), v)
    }

    @Test
    fun `recycler quand les materiaux valent plus que l'objet`() {
        val donnees = DonneesJeu(
            versionJeu = "test", source = "test",
            objets = listOf(
                Objet("boite", "Boîte", valeur = 100, recyclage = listOf(Quantite("vis", 3))),
                Objet("vis", "Vis", valeur = 50),
            ),
            etablis = emptyList(), quetes = emptyList(), cartes = emptyList(), conditions = emptyList(),
        )
        val c = Catalogue(donnees)
        assertEquals(150, c.valeurRecyclage(c.objet("boite")!!))
        assertEquals(Sortie.RECYCLER, Conseiller(c, Planificateur(c)).meilleureSortie("boite"))
        assertEquals(Sortie.VENDRE, Conseiller(c, Planificateur(c)).meilleureSortie("vis"))
    }
}
