package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Décompte de la liste à trouver et bilan du retour de raid, sur les vraies données du jeu 2.0. */
class RetourDeRaidTest {
    private val planificateur = Planificateur(Catalogue(DonneesJeu.embarquees()))

    /** « Ciel dégagé » demande 3 Alliages ARC. */
    private val cielDegage = Objectif.QueteATerminer("clearer_skies")

    /** Ajoutés à la main : 2 Batteries et une Bobine de film. */
    private val raid = mapOf("battery" to 2, "film_reel" to 1)

    private fun liste(reserve: Map<String, Int>, raid: Map<String, Int>, trouves: Map<String, Int>) =
        planificateur.aTrouver(listOf(cielDegage), reserve, raid, trouves)

    @Test
    fun `compter seulement ce que le joueur cherche`() {
        var trouves = RetourDeRaid.compter(raid, emptyMap(), "battery", 1)
        assertEquals(mapOf("battery" to 1), trouves)
        // Un objet qui n'est pas dans la liste ne se compte pas.
        assertEquals(trouves, RetourDeRaid.compter(raid, trouves, "arc_alloy", 3))
        // Une correction (« − ») se décompte, jamais en dessous de zéro.
        trouves = RetourDeRaid.compter(raid, trouves, "battery", -1)
        assertEquals(emptyMap<String, Int>(), trouves)
        assertEquals(emptyMap<String, Int>(), RetourDeRaid.compter(raid, trouves, "battery", -2))
    }

    @Test
    fun `objets qui atteignent leur nombre, puis raid termine`() {
        val avant = liste(emptyMap(), raid, emptyMap())
        assertEquals(listOf("arc_alloy", "battery", "film_reel"), avant.map { it.objetId })
        assertEquals(listOf(3, 2, 1), avant.map { it.reste })

        // Premier retour : les 3 Alliages ARC de la quête, la Bobine de film, une seule Batterie sur deux.
        val reserve = mapOf("arc_alloy" to 3, "battery" to 1, "film_reel" to 1)
        val trouves = RetourDeRaid.compter(raid, RetourDeRaid.compter(raid, emptyMap(), "battery", 1), "film_reel", 1)
        val apres = liste(reserve, raid, trouves)
        // Complet pour la quête, l'Alliage ARC quitte la liste ; la Batterie est décomptée.
        assertEquals(listOf("battery", "film_reel"), apres.map { it.objetId })
        assertEquals(1, apres.first().reste)
        val bilan = RetourDeRaid.bilan(avant, apres, reserve)
        assertEquals(listOf("arc_alloy", "film_reel"), bilan.atteints.map { it.objetId })
        assertEquals(3, bilan.atteints.first().trouves)
        assertFalse(bilan.termine)

        // La Bobine de film trouvée quitte la liste ; la Batterie reste à trouver.
        val (raid2, trouves2) = RetourDeRaid.sansLesTrouves(raid, trouves)
        assertEquals(mapOf("battery" to 2), raid2)
        assertEquals(mapOf("battery" to 1), trouves2)

        // Second retour : la dernière Batterie, tout est trouvé.
        val avant2 = liste(reserve, raid2, trouves2)
        val reserve2 = reserve + ("battery" to 2)
        val trouves3 = RetourDeRaid.compter(raid2, trouves2, "battery", 1)
        val bilan2 = RetourDeRaid.bilan(avant2, liste(reserve2, raid2, trouves3), reserve2)
        assertEquals(listOf("battery"), bilan2.atteints.map { it.objetId })
        assertTrue(bilan2.termine)
    }

    @Test
    fun `rien a feter sans objet atteint`() {
        val avant = liste(emptyMap(), raid, emptyMap())
        val bilan = RetourDeRaid.bilan(avant, avant, emptyMap())
        assertTrue(bilan.atteints.isEmpty())
        assertFalse(bilan.termine)
        // Rien à trouver : jamais « raid terminé ».
        assertFalse(RetourDeRaid.bilan(emptyList(), emptyList(), emptyMap()).termine)
    }
}
