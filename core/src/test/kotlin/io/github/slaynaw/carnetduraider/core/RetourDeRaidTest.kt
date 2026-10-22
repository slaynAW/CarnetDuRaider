package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Retour de raid : décompte de la liste du raid et bilan, sur les vraies données du jeu 2.0. */
class RetourDeRaidTest {
    private val planificateur = Planificateur(Catalogue(DonneesJeu.embarquees()))

    /** « Ciel dégagé » demande 3 Alliages ARC ; la Station utilitaire niveau 1, 6 Alliages ARC et 50 Composants plastiques. */
    private val cielDegage = Objectif.QueteATerminer("clearer_skies")
    private val station1 = Objectif.NiveauDEtabli("utility_bench", 1)
    private val objectifs = listOf(cielDegage, station1)

    @Test
    fun `objets qui atteignent leur nombre, objectifs termines, puis raid termine`() {
        val avant = planificateur.listeDuRaid(objectifs, emptyMap())
        assertEquals(listOf(9, 50), avant.sortedBy { it.objetId }.map { it.reste })

        // Premier retour : les 9 Alliages ARC (quête et établi), 20 Composants plastiques sur 50.
        val r = planificateur.repartir(objectifs, emptyMap(), mapOf("arc_alloy" to 9, "plastic_parts" to 20))
        val apres = planificateur.listeDuRaid(objectifs, r)
        val bilan = RetourDeRaid.bilan(avant, apres, listOf(cielDegage))
        assertEquals(listOf("arc_alloy"), bilan.atteints.map { it.objetId })
        assertEquals(listOf(cielDegage), bilan.objectifsTermines)
        assertFalse(bilan.termine)

        // Ce qui a été rapporté pour la quête terminée n'a plus à être gardé.
        val r2 = RetourDeRaid.sansLesTermines(r, listOf(cielDegage))
        assertEquals(setOf(station1.cle), r2.keys)

        // Second retour : les 30 derniers Composants plastiques, tout est trouvé.
        val avant2 = planificateur.listeDuRaid(listOf(station1), r2)
        val r3 = planificateur.repartir(listOf(station1), r2, mapOf("plastic_parts" to 30))
        val bilan2 = RetourDeRaid.bilan(avant2, planificateur.listeDuRaid(listOf(station1), r3), listOf(station1))
        assertEquals(listOf("plastic_parts"), bilan2.atteints.map { it.objetId })
        assertTrue(bilan2.termine)
    }

    @Test
    fun `rien a feter sans objet atteint`() {
        val avant = planificateur.listeDuRaid(objectifs, emptyMap())
        val bilan = RetourDeRaid.bilan(avant, avant, emptyList())
        assertTrue(bilan.vide)
        assertFalse(bilan.termine)
        // Rien à trouver : jamais « raid terminé ».
        assertFalse(RetourDeRaid.bilan(emptyList(), emptyList(), emptyList()).termine)
    }
}
