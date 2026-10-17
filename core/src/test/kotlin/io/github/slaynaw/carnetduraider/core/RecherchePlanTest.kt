package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecherchePlanTest {
    private val plan = Plan(
        zones = listOf(ZonePlan("Vieille ville", 0.5f, 0.5f), ZonePlan("Gare", 0.2f, 0.2f, detail = 1)),
        sorties = listOf(
            SortiePlan(TypeSortie.TRAPPE, 0.1f, 0.1f, "Trappe de la gare"),
            SortiePlan(TypeSortie.TRAPPE, 0.3f, 0.3f, "Bac à sable"),
            SortiePlan(TypeSortie.METRO, 0.6f, 0.6f, "Station est"),
            SortiePlan(TypeSortie.TELEPHERIQUE, 0.7f, 0.7f),
        ),
        arcs = listOf(
            ArcPlan("Bondisseur", listOf(listOf(0.4f, 0.4f))),
            ArcPlan("Guêpe", listOf(listOf(0.8f, 0.8f))),
            ArcPlan("Turbine ARC", listOf(listOf(0.9f, 0.9f))),
        ),
    )

    @Test
    fun `les trappes`() {
        val r = RecherchePlan.chercher(plan, "les trappes")!!
        assertEquals(listOf("Trappe de la gare", "Bac à sable"), r.sorties.map { it.nom })
        assertTrue(r.zones.isEmpty() && r.arcs.isEmpty())
    }

    @Test
    fun `les bondisseurs, sans accents ni pluriel`() {
        assertEquals(listOf("Bondisseur"), RecherchePlan.chercher(plan, "les bondisseurs")!!.arcs.map { it.nom })
        assertEquals(listOf("Guêpe"), RecherchePlan.chercher(plan, "GUEPES")!!.arcs.map { it.nom })
    }

    @Test
    fun `familles et noms combines`() {
        assertEquals(4, RecherchePlan.chercher(plan, "sorties")!!.sorties.size)
        assertEquals(3, RecherchePlan.chercher(plan, "ARC")!!.arcs.size)
        val gare = RecherchePlan.chercher(plan, "trappe gare")!!
        assertEquals(listOf("Trappe de la gare"), gare.sorties.map { it.nom })
        assertTrue(gare.zones.isEmpty())
        assertEquals(listOf("Gare"), RecherchePlan.chercher(plan, "gare")!!.zones.map { it.nom })
        assertTrue(RecherchePlan.chercher(plan, "matriarche")!!.vide)
    }

    @Test
    fun `recherche vide`() {
        assertNull(RecherchePlan.chercher(plan, "  "))
        assertNull(RecherchePlan.chercher(plan, "les"))
    }
}
