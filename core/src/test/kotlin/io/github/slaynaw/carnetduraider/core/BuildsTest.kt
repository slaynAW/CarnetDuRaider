package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mes builds : choix par case, matériaux à garder, enregistrement. Sur les vraies données du jeu 2.0. */
class BuildsTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val builds = Builds(catalogue)

    @Test
    fun `chaque case ne propose que les objets de son emplacement`() {
        assertTrue(builds.choix(CaseBuild.MODULE).any { it.id == "combat_mk1" })
        assertTrue(builds.choix(CaseBuild.BOUCLIER).map { it.id }.containsAll(listOf("light_shield", "medium_shield", "heavy_shield")))
        assertEquals(builds.choix(CaseBuild.ARME_1), builds.choix(CaseBuild.ARME_2))
        assertTrue(builds.choix(CaseBuild.SOINS).any { it.id == "bandage" })
        assertTrue(builds.choix(CaseBuild.GRENADES).any { it.id == "light_impact_grenade" })
        assertTrue(builds.choix(CaseBuild.DIVERS).any { it.id == "zipline" })
        CaseBuild.entries.forEach { case -> assertTrue(builds.choix(case).all { it.emplacement == case.emplacement }) }
    }

    @Test
    fun `materiaux d'une arme amelioree, de proche en proche`() {
        // Ferro III : Ferro II et le coût de l'amélioration, puis Ferro I et sa recette.
        val m = builds.materiaux("ferro_iii")
        assertTrue("ferro_ii" in m && "ferro_i" in m, "$m")
        assertTrue(catalogue.objet("ferro_iii")!!.obtention.amelioration!!.ingredients.all { it.id in m })
        assertTrue(catalogue.objet("ferro_i")!!.obtention.fabrications.first().ingredients.all { it.id in m })
        assertTrue("ferro_iii" !in m)
    }

    @Test
    fun `objets a garder pour les builds`() {
        val assaut = Build("Assaut", mapOf(CaseBuild.ARME_1 to "ferro_iii", CaseBuild.SOINS to "bandage"))
        val aGarder = builds.aGarder(listOf(Build(), assaut))
        assertEquals(listOf(UsageBuild("Assaut", "ferro_iii", direct = true)), aGarder["ferro_iii"])
        assertEquals(listOf(UsageBuild("Assaut", "ferro_iii", direct = false)), aGarder["ferro_ii"])
        val tissu = catalogue.objet("bandage")!!.obtention.fabrications.first().ingredients.first().id
        assertTrue(UsageBuild("Assaut", "bandage", direct = false) in aGarder[tissu].orEmpty())
        // Sans nom, le build s'appelle par son rang.
        assertEquals("Build 1", builds.nom(Build(), 0))
        assertEquals(listOf(UsageBuild("Build 3", "light_shield", direct = true)), builds.aGarder(listOf(Build(), Build(), Build(objets = mapOf(CaseBuild.BOUCLIER to "light_shield"))))["light_shield"])
        assertTrue(builds.aGarder(List(Builds.NOMBRE) { Build() }).isEmpty())
    }

    @Test
    fun `enregistrement des builds`() {
        val liste = listOf(Build("Assaut", mapOf(CaseBuild.ARME_1 to "ferro_iii", CaseBuild.MODULE to "combat_mk1")), Build("Discret | « furtif »"))
        val relus = Builds.decoder(Builds.encoder(liste))
        assertEquals(Builds.NOMBRE, relus.size)
        assertEquals(liste, relus.take(2))
        assertTrue(relus.drop(2).all { it == Build() })
        assertEquals(List(Builds.NOMBRE) { Build() }, Builds.decoder(null))
        assertEquals(List(Builds.NOMBRE) { Build() }, Builds.decoder("pas du JSON"))
    }
}
