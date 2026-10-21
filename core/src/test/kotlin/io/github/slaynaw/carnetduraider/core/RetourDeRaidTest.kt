package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Retour de raid : décompte de la liste du raid et bilan, sur les vraies données du jeu 2.0. */
class RetourDeRaidTest {
    private val planificateur = Planificateur(Catalogue(DonneesJeu.embarquees()))

    /** « Ciel dégagé » demande 3 Alliages ARC. */
    private val cielDegage = Objectif.QueteATerminer("clearer_skies")

    /** Ajoutés à la main : 2 Batteries et une Bobine de film. */
    private val raid = mapOf("battery" to 2, "film_reel" to 1)

    private val rien = Rapporte(emptyMap(), emptyMap())

    private fun liste(r: Rapporte) = planificateur.listeDuRaid(listOf(cielDegage), r.apports, raid, r.trouvesRaid)

    @Test
    fun `objets qui atteignent leur nombre, objectifs termines, puis raid termine`() {
        val avant = liste(rien)
        assertEquals(listOf("arc_alloy", "battery", "film_reel"), avant.map { it.objetId })
        assertEquals(listOf(3, 2, 1), avant.map { it.reste })

        // Premier retour : les 3 Alliages ARC de la quête, la Bobine de film, une seule Batterie sur deux.
        val r = planificateur.repartir(listOf(cielDegage), raid, rien, mapOf("arc_alloy" to 3, "film_reel" to 1, "battery" to 1))
        val apres = liste(r)
        assertEquals(listOf(0, 1, 0), apres.map { it.reste })
        val bilan = RetourDeRaid.bilan(avant, apres, listOf(cielDegage))
        assertEquals(listOf("arc_alloy", "film_reel"), bilan.atteints.map { it.objetId })
        assertEquals(listOf(cielDegage), bilan.objectifsTermines)
        assertFalse(bilan.termine)

        // La Bobine de film trouvée quitte la liste, comme ce qui a été rapporté pour la quête terminée.
        val (raid2, trouves2) = RetourDeRaid.sansLesTrouves(raid, r.trouvesRaid)
        assertEquals(mapOf("battery" to 2), raid2)
        assertEquals(mapOf("battery" to 1), trouves2)
        assertEquals(emptyMap<String, Map<String, Int>>(), RetourDeRaid.sansLesTermines(r.apports, listOf(cielDegage)))

        // Second retour : la dernière Batterie, tout est trouvé.
        val r2 = Rapporte(emptyMap(), trouves2)
        val avant2 = planificateur.listeDuRaid(emptyList(), r2.apports, raid2, r2.trouvesRaid)
        val r3 = planificateur.repartir(emptyList(), raid2, r2, mapOf("battery" to 1))
        val bilan2 = RetourDeRaid.bilan(avant2, planificateur.listeDuRaid(emptyList(), r3.apports, raid2, r3.trouvesRaid), emptyList())
        assertEquals(listOf("battery"), bilan2.atteints.map { it.objetId })
        assertTrue(bilan2.termine)
    }

    @Test
    fun `rien a feter sans objet atteint`() {
        val avant = liste(rien)
        val bilan = RetourDeRaid.bilan(avant, avant, emptyList())
        assertTrue(bilan.vide)
        assertFalse(bilan.termine)
        // Rien à trouver : jamais « raid terminé ».
        assertFalse(RetourDeRaid.bilan(emptyList(), emptyList(), emptyList()).termine)
    }
}
