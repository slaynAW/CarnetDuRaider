package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreparationRaidTest {
    private fun objet(id: String, cartes: List<CarteButin>, zones: List<ZoneButin> = emptyList()) =
        Objet(id = id, nom = id, obtention = Obtention(cartes = cartes, zones = zones))

    private val donnees = DonneesJeu(
        versionJeu = "test",
        source = "test",
        objets = listOf(
            objet(
                "a", listOf(CarteButin("barrage", 100), CarteButin("col", 40)),
                listOf(ZoneButin("barrage", "Station de pompage", 100), ZoneButin("col", "Observatoire", 30)),
            ),
            objet(
                "b", listOf(CarteButin("col", 100), CarteButin("barrage", 80)),
                listOf(ZoneButin("col", "Observatoire", 100), ZoneButin("barrage", "Station de pompage", 60), ZoneButin("barrage", "Marécage", 70)),
            ),
            objet("c", listOf(CarteButin("barrage", 50))),
            objet("d", emptyList()),
        ),
        etablis = emptyList(),
        quetes = emptyList(),
        cartes = emptyList(),
        conditions = emptyList(),
    )
    private val preparation = PreparationRaid(Catalogue(donnees))

    @Test
    fun `la carte qui donne le plus d'objets passe devant`() {
        val conseil = preparation.conseiller(listOf("a", "b", "c", "d", "inconnu"))
        assertEquals(listOf("barrage", "col"), conseil.cartes.map { it.carteId })
        val barrage = conseil.cartes.first()
        assertEquals(2.3, barrage.score, 1e-9)
        assertEquals(listOf("a", "b", "c"), barrage.objets)
        assertEquals(listOf("a"), barrage.meilleurePour)
        assertEquals(listOf("d"), conseil.sansButin)
    }

    @Test
    fun `zones de la carte, celles qui donnent le plus d'objets d'abord`() {
        val barrage = preparation.conseiller(listOf("a", "b")).cartes.first()
        assertEquals(listOf("Station de pompage", "Marécage"), barrage.zones.map { it.zone })
        assertEquals(listOf("a", "b"), barrage.zones.first().objets)
    }

    @Test
    fun `liste vide ou doublons`() {
        assertTrue(preparation.conseiller(emptyList()).cartes.isEmpty())
        assertEquals(listOf("a"), preparation.conseiller(listOf("a", "a")).cartes.first().objets)
    }

    @Test
    fun `avec les vraies donnees`() {
        val vraie = PreparationRaid(Catalogue(DonneesJeu.embarquees()))
        val conseil = vraie.conseiller(listOf("battery", "metal_parts", "rubber_parts", "lemon"))
        assertTrue(conseil.cartes.isNotEmpty())
        assertTrue(conseil.cartes.first().zones.isNotEmpty())
        assertTrue(conseil.cartes.zipWithNext().all { (x, y) -> x.score >= y.score })
    }

    @Test
    fun `parcours pour tout trouver`() {
        val catalogue = Catalogue(
            donnees.copy(
                objets = donnees.objets + listOf(
                    objet("e", listOf(CarteButin("col", 100))),
                    objet("f", listOf(CarteButin("pont", 100))),
                ),
            ),
        )
        val conseil = PreparationRaid(catalogue).conseiller(listOf("a", "b", "c", "e", "f", "d"))
        // Col apporte le plus (a, b, e), puis le Pont (f, indice 100) avant le Barrage (c, indice 50).
        assertEquals(listOf("col", "pont", "barrage"), conseil.parcours.map { it.carteId })
        assertEquals(listOf("b", "e", "a"), conseil.parcours[0].objets)
        assertEquals(listOf("f"), conseil.parcours[1].objets)
        assertEquals(listOf("c"), conseil.parcours[2].objets)
        // Une seule carte suffit : le parcours s'arrête là.
        assertEquals(listOf("barrage"), preparation.conseiller(listOf("a", "c")).parcours.map { it.carteId })
    }
}
