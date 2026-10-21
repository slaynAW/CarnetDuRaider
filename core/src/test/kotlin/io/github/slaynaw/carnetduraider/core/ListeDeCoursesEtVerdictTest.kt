package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Liste de courses et verdict, sur les vraies données du jeu 2.0. */
class ListeDeCoursesEtVerdictTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val planificateur = Planificateur(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur, Usages(catalogue, SuiviProgression(catalogue)))

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
    fun `liste du raid - objectifs et ajouts du joueur reunis`() {
        val apports = mapOf(cielDegage.cle to mapOf("arc_alloy" to 3))
        val ajouts = mapOf("film_reel" to 2, "battery" to 1, "arc_alloy" to 5)
        val trouves = mapOf("film_reel" to 1, "arc_alloy" to 2)
        val liste = planificateur.listeDuRaid(listOf(cielDegage), apports, ajouts, trouves)
        // L'Alliage ARC : 3 pour la quête (rapportés) et 5 ajoutés à la main (2 rapportés), sur une seule ligne.
        val alliage = liste.single { it.objetId == "arc_alloy" }
        assertEquals(listOf(5, 8, 3), listOf(alliage.trouves, alliage.voulu, alliage.reste))
        assertTrue(alliage.ajouteParLeJoueur && alliage.pour.isNotEmpty())
        // La Bobine de film, ajoutée à la main, se décompte avec ce qui a été rapporté pour elle.
        val bobine = liste.single { it.objetId == "film_reel" }
        assertEquals(listOf(1, 2, 1), listOf(bobine.trouves, bobine.voulu, bobine.reste))
        assertEquals(listOf("arc_alloy", "film_reel", "battery"), liste.map { it.objetId })
        assertEquals(3, planificateur.aTrouver(listOf(cielDegage), apports, ajouts, trouves).size)
    }

    @Test
    fun `repartir le butin - l'objectif le plus pres d'etre termine d'abord`() {
        // « Ciel dégagé » ne demande que 3 Alliages ARC ; la Station utilitaire 1 en demande 6, plus 50 Composants plastiques.
        val r = planificateur.repartir(listOf(stationUtilitaire1, cielDegage), emptyMap(), Rapporte(emptyMap(), emptyMap()), mapOf("arc_alloy" to 4))
        assertEquals(mapOf("arc_alloy" to 3), r.apports[cielDegage.cle])
        assertEquals(mapOf("arc_alloy" to 1), r.apports[stationUtilitaire1.cle])
        assertTrue(planificateur.complet(cielDegage, r.apports))
        assertFalse(planificateur.complet(stationUtilitaire1, r.apports))
        assertEquals(mapOf("plastic_parts" to 50, "arc_alloy" to 5), planificateur.manque(stationUtilitaire1, r.apports))
    }

    @Test
    fun `repartir le butin - ajouts du joueur, surplus ignore, retours successifs`() {
        val ajouts = mapOf("battery" to 2)
        val r = planificateur.repartir(listOf(cielDegage), ajouts, Rapporte(emptyMap(), emptyMap()), mapOf("battery" to 5, "arc_alloy" to 10, "fabric" to 3))
        // Ce qui dépasse n'est gardé nulle part : pas de réserve.
        assertEquals(mapOf("battery" to 2), r.trouvesRaid)
        assertEquals(mapOf(cielDegage.cle to mapOf("arc_alloy" to 3)), r.apports)
        // Un second retour de raid s'ajoute au premier, sans jamais dépasser ce qui est demandé.
        assertEquals(r, planificateur.repartir(listOf(cielDegage), ajouts, r, mapOf("battery" to 1, "arc_alloy" to 1)))
        val partiel = planificateur.repartir(listOf(stationUtilitaire1), emptyMap(), Rapporte(emptyMap(), emptyMap()), mapOf("arc_alloy" to 2))
        val suite = planificateur.repartir(listOf(stationUtilitaire1), emptyMap(), partiel, mapOf("arc_alloy" to 3))
        assertEquals(mapOf("arc_alloy" to 5), suite.apports[stationUtilitaire1.cle])
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
    fun `verdict suffisant quand tout a ete rapporte, sans proposer la vente`() {
        val apports = mapOf(stationUtilitaire1.cle to mapOf("arc_alloy" to 6), cielDegage.cle to mapOf("arc_alloy" to 3))
        val v = conseiller.verdict("arc_alloy", objectifs, apports)
        v as Verdict.Suffisant
        assertEquals(9, v.besoin)
    }

    @Test
    fun `jamais a vendre s'il est dans la liste de raid`() {
        val v = conseiller.verdict("film_reel", emptyList(), listeRaid = mapOf("film_reel" to 2))
        assertEquals(Verdict.Cherche(voulu = 2, trouves = 0), v)
        val rapporte = conseiller.verdict("film_reel", emptyList(), listeRaid = mapOf("film_reel" to 2), trouvesRaid = mapOf("film_reel" to 1))
        assertEquals(Verdict.Cherche(voulu = 2, trouves = 1), rapporte)
    }

    @Test
    fun `utile quand il sert a autre chose qu'aux objectifs suivis`() {
        // L'Alliage ARC sert aux établis, à des quêtes, à la fabrication : jamais « à vendre » d'office.
        val alliage = conseiller.verdict("arc_alloy", listOf(armurier2))
        alliage as Verdict.Utile
        assertTrue(alliage.usages.ameliorations.isNotEmpty())
        assertTrue(alliage.usages.utilisations.fabrication.isNotEmpty())

        // Une clé ouvre une porte, une arme s'équipe, un schéma s'apprend.
        assertTrue((conseiller.verdict("blue_gate_cellar_key", emptyList()) as Verdict.Utile).usages.utilisations.cle)
        assertTrue((conseiller.verdict("anvil_ii", emptyList()) as Verdict.Utile).usages.utilisations.equipement)
        val schema = conseiller.verdict("advanced_camera_blueprint", emptyList()) as Verdict.Utile
        assertEquals(listOf("advanced_camera"), schema.usages.utilisations.debloque)
    }

    @Test
    fun `a vendre seulement s'il ne sert a rien`() {
        // Babiole sans aucun usage dans le jeu.
        val babiole = catalogue.donnees.objets.first { it.categorie == "Babiole" && Usages(catalogue, SuiviProgression(catalogue)).pour(it.id).aucun }
        assertTrue(conseiller.verdict(babiole.id, emptyList()) is Verdict.Inutile)
    }

    @Test
    fun `ce qui est fait ne compte plus`() {
        val suivi = SuiviProgression(catalogue)
        val usages = Usages(catalogue, suivi)
        val avant = usages.pour("arc_alloy").ameliorations.map { it.etabli.id to it.niveau }
        assertTrue("utility_bench" to 1 in avant)
        val station = catalogue.etabli("utility_bench")!!
        val apres = usages.pour("arc_alloy", suivi.changerNiveau(Progression(), station, 3)).ameliorations.map { it.etabli.id to it.niveau }
        assertTrue(apres.none { it.first == "utility_bench" })
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
        val conseil = Conseiller(c, Planificateur(c), Usages(c, SuiviProgression(c)))
        assertEquals(Sortie.RECYCLER, conseil.meilleureSortie("boite"))
        assertEquals(Sortie.VENDRE, conseil.meilleureSortie("vis"))
    }
}
