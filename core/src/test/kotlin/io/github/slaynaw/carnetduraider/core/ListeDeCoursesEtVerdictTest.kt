package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Liste de courses et verdict, sur les vraies données du jeu 2.0. */
class ListeDeCoursesEtVerdictTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val planificateur = Planificateur(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur)
    private val usages = Usages(catalogue)

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
    fun `cle stable des objectifs`() {
        assertEquals("etabli:utility_bench:1", stationUtilitaire1.cle)
        assertEquals("quete:clearer_skies", cielDegage.cle)
        assertEquals("projet:trophy_display_project:0", Objectif.EtapeDeProjet("trophy_display_project", 0).cle)
    }

    @Test
    fun `la liste regroupe les besoins par objet avec ce qui a ete rapporte`() {
        val apports = mapOf(
            stationUtilitaire1.cle to mapOf("arc_alloy" to 2),
            cielDegage.cle to mapOf("arc_alloy" to 3),
            armurier2.cle to mapOf("mechanical_components" to 5),
        )
        val liste = planificateur.listeDeCourses(objectifs, apports)
        val alliage = liste.lignes.single { it.objetId == "arc_alloy" }
        // 6 pour la Station utilitaire niveau 1 (2 rapportés) et 3 pour « Ciel dégagé » (tous rapportés).
        assertEquals(9, alliage.besoin)
        assertEquals(5, alliage.apporte)
        assertEquals(4, alliage.manquant)
        assertEquals(listOf(4, 0), alliage.pour.map { it.manque })

        val plastique = liste.lignes.single { it.objetId == "plastic_parts" }
        assertEquals("Composants plastiques", plastique.nom)
        assertEquals(50, plastique.manquant)

        // Les objets complets passent en fin de liste.
        assertTrue(liste.lignes.last().complet)
        assertEquals("mechanical_components", liste.lignes.last().objetId)
        assertEquals(4 + 50 + 3 + 8, liste.objetsManquants)
        assertEquals(4, liste.objetsATrouver)
    }

    @Test
    fun `liste du raid - ce que demandent les objectifs, et son decompte`() {
        val apports = mapOf(stationUtilitaire1.cle to mapOf("arc_alloy" to 2), cielDegage.cle to mapOf("arc_alloy" to 3))
        val liste = planificateur.listeDuRaid(listOf(stationUtilitaire1, cielDegage), apports)
        val alliage = liste.single { it.objetId == "arc_alloy" }
        assertEquals(listOf(5, 9, 4), listOf(alliage.trouves, alliage.voulu, alliage.reste))
        assertEquals(2, alliage.pour.size)
        assertEquals(listOf("arc_alloy", "plastic_parts"), planificateur.aTrouver(listOf(stationUtilitaire1, cielDegage), apports).map { it.objetId }.sorted())
        assertTrue(planificateur.aTrouver(listOf(cielDegage), apports).isEmpty())
    }

    @Test
    fun `repartir le butin - l'objectif le plus pres d'etre termine d'abord`() {
        // « Ciel dégagé » ne demande que 3 Alliages ARC ; la Station utilitaire 1 en demande 6, plus 50 Composants plastiques.
        val r = planificateur.repartir(listOf(stationUtilitaire1, cielDegage), emptyMap(), mapOf("arc_alloy" to 4))
        assertEquals(mapOf("arc_alloy" to 3), r[cielDegage.cle])
        assertEquals(mapOf("arc_alloy" to 1), r[stationUtilitaire1.cle])
        assertTrue(planificateur.complet(cielDegage, r))
        assertFalse(planificateur.complet(stationUtilitaire1, r))
        assertEquals(mapOf("plastic_parts" to 50, "arc_alloy" to 5), planificateur.manque(stationUtilitaire1, r))
    }

    @Test
    fun `repartir le butin - surplus ignore, retours successifs`() {
        val r = planificateur.repartir(listOf(cielDegage), emptyMap(), mapOf("battery" to 5, "arc_alloy" to 10, "fabric" to 3))
        // Ce qui dépasse n'est gardé nulle part : pas de réserve.
        assertEquals(mapOf(cielDegage.cle to mapOf("arc_alloy" to 3)), r)
        // Un second retour de raid s'ajoute au premier, sans jamais dépasser ce qui est demandé.
        assertEquals(r, planificateur.repartir(listOf(cielDegage), r, mapOf("battery" to 1, "arc_alloy" to 1)))
        val partiel = planificateur.repartir(listOf(stationUtilitaire1), emptyMap(), mapOf("arc_alloy" to 2))
        val suite = planificateur.repartir(listOf(stationUtilitaire1), partiel, mapOf("arc_alloy" to 3))
        assertEquals(mapOf("arc_alloy" to 5), suite[stationUtilitaire1.cle])
    }

    @Test
    fun `un objectif sans objet ne se termine jamais tout seul`() {
        val sansObjet = catalogue.donnees.quetes.first { it.objets.isEmpty() }
        assertFalse(planificateur.complet(Objectif.QueteATerminer(sansObjet.id), emptyMap()))
    }

    @Test
    fun `progression de la liste`() {
        assertEquals(0.0, planificateur.listeDeCourses(listOf(cielDegage), emptyMap()).progression)
        assertEquals(1.0, planificateur.listeDeCourses(listOf(cielDegage), mapOf(cielDegage.cle to mapOf("arc_alloy" to 3))).progression)
        assertEquals(1.0, planificateur.listeDeCourses(emptyList(), emptyMap()).progression)
    }

    @Test
    fun `verdict garder tant que les objectifs en demandent`() {
        val v = conseiller.verdict("arc_alloy", objectifs, mapOf(stationUtilitaire1.cle to mapOf("arc_alloy" to 5)))
        v as Verdict.Garder
        // Encore 1 pour la Station utilitaire et 3 pour « Ciel dégagé ».
        assertEquals(4, v.manquant)
        assertEquals(9, v.besoin)
    }

    @Test
    fun `tout rapporte pour les objectifs, plus besoin de le garder`() {
        val apports = mapOf(stationUtilitaire1.cle to mapOf("arc_alloy" to 6), cielDegage.cle to mapOf("arc_alloy" to 3))
        assertTrue(conseiller.verdict("arc_alloy", objectifs, apports) is Verdict.Inutile)
    }

    @Test
    fun `utile seulement pour un objectif en cours ou un build`() {
        // L'Alliage ARC sert aux établis, à des quêtes, à la fabrication… mais rien n'est en cours : à vendre ou recycler.
        assertTrue(conseiller.verdict("arc_alloy", emptyList()) is Verdict.Inutile)
        assertTrue(conseiller.verdict("blue_gate_cellar_key", emptyList()) is Verdict.Inutile)
        // L'explication dit quand même à quoi il peut servir.
        val u = usages.pour("arc_alloy")
        assertTrue(u.ameliorations.any { it.etabli.id == "utility_bench" && it.niveau == 1 })
        assertTrue(u.utilisations.fabrication.isNotEmpty())
        assertTrue(usages.pour("blue_gate_cellar_key").utilisations.cle)
        assertEquals(listOf("advanced_camera"), usages.pour("advanced_camera_blueprint").utilisations.debloque)

        // Dans un build, ou pour en fabriquer un objet : à garder.
        val build = UsageBuild("Assaut", "ferro_iii", direct = false)
        val v = conseiller.verdict("metal_parts", emptyList(), builds = mapOf("metal_parts" to listOf(build)))
        assertEquals(Verdict.PourBuild(listOf(build)), v)
        // Demandé par un objectif ET par un build : « Garder », avec les deux raisons.
        val garder = conseiller.verdict("arc_alloy", listOf(cielDegage), builds = mapOf("arc_alloy" to listOf(build)))
        garder as Verdict.Garder
        assertEquals(3, garder.manquant)
        assertEquals(listOf(build), garder.builds)
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
        val conseil = Conseiller(c, Planificateur(c))
        assertEquals(Sortie.RECYCLER, conseil.meilleureSortie("boite"))
        assertEquals(Sortie.VENDRE, conseil.meilleureSortie("vis"))
    }
}
