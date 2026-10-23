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
        assertTrue(builds.choix(CaseBuild.AUTRE_1).any { it.id == "zipline" })
        // Six cases « Autres », toutes pour les mêmes objets.
        assertEquals((1..6).map { "Autre $it" }, CaseBuild.entries.filter { it.autre }.map { it.libelle })
        assertTrue(CaseBuild.entries.filter { it.autre }.all { builds.choix(it) == builds.choix(CaseBuild.AUTRE_1) })
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

    @Test
    fun `l'ancienne case Divers devient Autre 1, et les autres cases s'enregistrent`() {
        val avant = Builds.decoder("""[{"nom":"Assaut","objets":{"ARME_1":"ferro_iii","DIVERS":"zipline"}}]""")
        assertEquals(mapOf(CaseBuild.ARME_1 to "ferro_iii", CaseBuild.AUTRE_1 to "zipline"), avant.first().objets)
        val plein = listOf(Build("Pièges", CaseBuild.entries.filter { it.autre }.associateWith { "zipline" }))
        assertEquals(plein.first(), Builds.decoder(Builds.encoder(plein)).first())
        assertEquals(listOf(UsageBuild("Pièges", "zipline", direct = true)), builds.aGarder(plein)["zipline"])
    }

    @Test
    fun `build prefere en premier et ouvert a l'arrivee`() {
        val liste = Builds.completer(listOf(Build("A"), Build("B"), Build("C")))
        assertEquals((0 until Builds.NOMBRE).toList(), Builds.ordre(liste))
        assertEquals(0, Builds.ouvert(liste))
        val avecC = Builds.preferer(liste, 2)
        assertEquals(listOf(2, 0, 1, 3, 4, 5), Builds.ordre(avecC))
        assertEquals(2, Builds.ouvert(avecC))
        // Un seul préféré : en choisir un autre retire le premier.
        val avecB = Builds.preferer(avecC, 1)
        assertEquals(listOf(false, true, false, false, false, false), avecB.map { it.prefere })
        assertTrue(Builds.preferer(avecB, 1, prefere = false).none { it.prefere })
        // Enregistré, et un fichier avec deux préférés n'en garde que le premier.
        assertEquals(avecB, Builds.decoder(Builds.encoder(avecB)))
        val deux = Builds.completer(listOf(Build("A", prefere = true), Build("B", prefere = true)))
        assertEquals(listOf(true, false), deux.take(2).map { it.prefere })
        // Un build d'avant la 0.14 (sans le champ) n'est pas préféré.
        assertEquals(Build("Vieux"), Builds.decoder("""[{"nom":"Vieux"}]""").first())
    }

    @Test
    fun `ce qu'il faut pour fabriquer tout le build, avec les quantites`() {
        // Ferro III : coût de l'amélioration II → III, celui de I → II, puis la recette de Ferro I.
        val ferro = catalogue.objet("ferro_iii")!!.obtention
        val attendu = LinkedHashMap<String, Int>()
        fun compter(q: List<Quantite>) = q.forEach { attendu[it.id] = (attendu[it.id] ?: 0) + it.quantite }
        compter(ferro.amelioration!!.ingredients)
        compter(catalogue.objet("ferro_ii")!!.obtention.amelioration!!.ingredients)
        compter(catalogue.objet("ferro_i")!!.obtention.fabrications.first().ingredients)
        val bandage = catalogue.objet("bandage")!!.obtention.fabrications.first().ingredients
        bandage.forEach { attendu[it.id] = (attendu[it.id] ?: 0) + it.quantite * 2 }
        val build = Build(objets = mapOf(CaseBuild.ARME_1 to "ferro_iii", CaseBuild.SOINS to "bandage", CaseBuild.AUTRE_1 to "bandage"))
        val liste = builds.aFabriquer(build)
        assertEquals(attendu, liste.toMap())
        // Du plus demandé au moins demandé ; les armes elles-mêmes n'y sont pas.
        assertEquals(liste.map { it.second }.sortedDescending(), liste.map { it.second })
        assertTrue(liste.none { it.first.startsWith("ferro") })
        assertTrue(builds.aFabriquer(Build()).isEmpty())
    }
}
