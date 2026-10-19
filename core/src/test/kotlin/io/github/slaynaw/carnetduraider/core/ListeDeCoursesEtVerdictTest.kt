package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(4, liste.objetsATrouver)
    }

    @Test
    fun `a trouver - objectifs puis ajouts du joueur`() {
        val reserve = mapOf("arc_alloy" to 9, "film_reel" to 1)
        val liste = planificateur.aTrouver(listOf(cielDegage), reserve, mapOf("film_reel" to 2, "battery" to 1, "arc_alloy" to 5))
        // L'Alliage ARC est complet pour la quête ; ajouté à la main (5 voulus, 9 en réserve), il est trouvé.
        assertTrue(liste.single { it.objetId == "arc_alloy" }.let { it.ajouteParLeJoueur && it.trouve })
        val bobine = liste.single { it.objetId == "film_reel" }
        assertTrue(bobine.ajouteParLeJoueur)
        assertEquals(1, bobine.possede)
        assertEquals(2, bobine.voulu)
        assertEquals(listOf("film_reel", "battery", "arc_alloy"), liste.filter { it.ajouteParLeJoueur }.map { it.objetId })
        assertTrue(liste.filterNot { it.ajouteParLeJoueur }.all { it.pour.isNotEmpty() && !it.trouve })
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
    fun `verdict suffisant sans proposer la vente`() {
        val v = conseiller.verdict("arc_alloy", possede = 12, objectifs = objectifs)
        v as Verdict.Suffisant
        assertEquals(3, v.surplus)
    }

    @Test
    fun `jamais a vendre s'il est dans la liste de raid`() {
        val v = conseiller.verdict("film_reel", possede = 4, objectifs = emptyList(), listeRaid = mapOf("film_reel" to 2))
        assertEquals(Verdict.Cherche(voulu = 2, possede = 4), v)
    }

    @Test
    fun `utile quand il sert a autre chose qu'aux objectifs suivis`() {
        // L'Alliage ARC sert aux établis, à des quêtes, à la fabrication : jamais « à vendre » d'office.
        val alliage = conseiller.verdict("arc_alloy", possede = 3, objectifs = listOf(armurier2))
        alliage as Verdict.Utile
        assertTrue(alliage.usages.ameliorations.isNotEmpty())
        assertTrue(alliage.usages.utilisations.fabrication.isNotEmpty())

        // Une clé ouvre une porte, une arme s'équipe, un schéma s'apprend.
        assertTrue((conseiller.verdict("blue_gate_cellar_key", 1, emptyList()) as Verdict.Utile).usages.utilisations.cle)
        assertTrue((conseiller.verdict("anvil_ii", 1, emptyList()) as Verdict.Utile).usages.utilisations.equipement)
        val schema = conseiller.verdict("advanced_camera_blueprint", 1, emptyList()) as Verdict.Utile
        assertEquals(listOf("advanced_camera"), schema.usages.utilisations.debloque)
    }

    @Test
    fun `a vendre seulement s'il ne sert a rien`() {
        // Babiole sans aucun usage dans le jeu.
        val babiole = catalogue.donnees.objets.first { it.categorie == "Babiole" && Usages(catalogue, SuiviProgression(catalogue)).pour(it.id).aucun }
        assertTrue(conseiller.verdict(babiole.id, 1, emptyList()) is Verdict.Inutile)
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
