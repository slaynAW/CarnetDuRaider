package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Garde-fous sur les données embarquées, dont la règle des traductions officielles (CLAUDE.md). */
class DonneesJeuTest {
    private val donnees = DonneesJeu.embarquees()
    private val catalogue = Catalogue(donnees)

    @Test
    fun `les donnees embarquees se lisent et sont completes`() {
        assertEquals("2.0", donnees.versionJeu)
        assertTrue(donnees.objets.size > 500, "${donnees.objets.size} objets")
        assertTrue(donnees.etablis.isNotEmpty())
        assertTrue(donnees.quetes.isNotEmpty())
        assertTrue(donnees.cartes.isNotEmpty())
        assertTrue(donnees.conditions.isNotEmpty())
    }

    @Test
    fun `chaque nom est renseigne et chaque identifiant unique`() {
        val noms = donnees.objets.map { it.id to it.nom } + donnees.etablis.map { it.id to it.nom } +
            donnees.quetes.map { it.id to it.nom } + donnees.cartes.map { it.id to it.nom } +
            donnees.conditions.map { it.id to it.nom }
        noms.forEach { (id, nom) -> assertTrue(nom.isNotBlank() && nom == nom.trim(), "nom invalide pour $id") }
        assertEquals(donnees.objets.size, donnees.objets.map { it.id }.toSet().size)
    }

    @Test
    fun `les noms francais officiels sont bien ceux du jeu`() {
        assertEquals("Alliage ARC", catalogue.objet("arc_alloy")?.nom)
        assertEquals(Rarete.INHABITUEL, catalogue.objet("arc_alloy")?.rarete)
        assertEquals("Inhabituel", Rarete.INHABITUEL.libelle)
        assertEquals("Matériau de la Surface", catalogue.objet("arc_alloy")?.categorie)
        assertEquals("Unité à impulsion de Bondisseur", catalogue.objet("leaper_pulse_unit")?.nom)
        assertEquals("Laboratoire médical", catalogue.etabli("med_station")?.nom)
        assertEquals("Ciel dégagé", catalogue.quete("clearer_skies")?.nom)
        assertEquals("Shani", catalogue.quete("clearer_skies")?.commercant)
        val cartes = donnees.cartes.map { it.nom }
        assertTrue("Col de Pendola" in cartes && "Champ de bataille du Barrage" in cartes, "$cartes")
        assertTrue("Raid nocturne" in donnees.conditions.map { it.nom })
    }

    @Test
    fun `un nom non traduit dans les donnees n'entre pas dans l'appli`() {
        // Exclus par l'import (docs/NOMS-A-VERIFIER.md) : texte français identique à l'anglais.
        assertNull(catalogue.objet("pressure_regulator"))
        assertTrue(donnees.projets.none { it.id == "community_event" })
        assertEquals(Catalogue.NOM_A_VERIFIER, catalogue.nomObjet("pressure_regulator"))
        // Vérifié en jeu par le joueur : « Sheet Metal » s'affiche ainsi dans le jeu en français.
        assertEquals("Sheet Metal", catalogue.objet("sheet_metal")?.nom)
    }

    @Test
    fun `quetes cachees et compteurs ecartes`() {
        // Déblocage de contenu après 18 manches disputées : fait par le jeu, jamais une quête à cocher.
        assertNull(catalogue.quete("blue_gate_content_unlock"))
        val ids = donnees.quetes.map { it.id }.toSet()
        donnees.quetes.forEach { q -> assertTrue(ids.containsAll(q.requiert), "prérequis connus pour ${q.id}") }
        // Ses suites ne l'attendent pas.
        assertEquals(listOf("off_the_radar"), catalogue.quete("a_first_foothold")?.requiert)
        // Ce que demandent les objectifs, ce sont de vrais objets, tous avec leur nom (pas de compteur
        // comme « Manches disputées »).
        val besoins = donnees.quetes.flatMap { it.objets } + donnees.etablis.flatMap { e -> e.niveaux.flatMap { it.cout } } +
            donnees.projets.flatMap { p -> p.etapes.flatMap { it.cout } }
        assertTrue(besoins.all { catalogue.objet(it.id) != null }, besoins.filter { catalogue.objet(it.id) == null }.toString())
    }

    @Test
    fun `noms identiques a l'anglais verifies en jeu`() {
        // Laissés en anglais par la traduction française du jeu, vérifiés en jeu (scripts/noms-identiques-verifies.txt).
        assertEquals("Riven Tides", catalogue.carte("riven-tides")?.nom)
        assertEquals(
            listOf("Beachcombing", "Close Scrutiny", "Hurricane", "Redirection"),
            listOf("beachcombing", "close-scrutiny", "hurricane", "redirection").map { catalogue.condition(it)?.nom },
        )
    }

    @Test
    fun `emplacements des builds et ameliorations d'armes`() {
        assertEquals(Emplacement.MODULE, catalogue.objet("combat_mk1")?.emplacement)
        assertEquals(Emplacement.BOUCLIER, catalogue.objet("light_shield")?.emplacement)
        assertEquals(Emplacement.ARME, catalogue.objet("ferro_i")?.emplacement)
        assertEquals(Emplacement.SOINS, catalogue.objet("bandage")?.emplacement)
        assertEquals(Emplacement.GRENADE, catalogue.objet("light_impact_grenade")?.emplacement)
        assertEquals(Emplacement.DIVERS, catalogue.objet("zipline")?.emplacement)
        assertNull(catalogue.objet("metal_parts")?.emplacement)
        val ferro3 = catalogue.objet("ferro_iii")!!.obtention.amelioration!!
        assertEquals("ferro_ii", ferro3.depuis)
        assertTrue(ferro3.ingredients.isNotEmpty())
    }

    @Test
    fun `la recherche ignore accents et majuscules et classe les debuts de nom d'abord`() {
        val resultats = catalogue.rechercher("pilote de guepe")
        assertEquals("Pilote de Guêpe", resultats.first().nom)
        assertTrue(resultats.any { it.nom == "Pilote de Guêpe endommagé" })
        assertEquals("Alliage ARC", catalogue.rechercher("ALLIAGE").first().nom)
        assertTrue(catalogue.rechercher("  ").isEmpty())
        // Noms officiels avec une espace insécable : trouvés en tapant une espace normale.
        assertEquals("Butin 2", catalogue.rechercher("butin 2").first().nom.replace('\u00a0', ' '))
    }

    @Test
    fun `plans des cartes`() {
        val donnees = DonneesJeu.embarquees()
        for (carte in donnees.cartes) {
            assertTrue(carte.plan.zones.isNotEmpty(), "zones de ${carte.id}")
            assertTrue(carte.plan.sorties.isNotEmpty(), "sorties de ${carte.id}")
            assertTrue(carte.plan.arcs.isNotEmpty(), "ARC de ${carte.id}")
            val points = carte.plan.zones.map { Triple(it.nom, it.x, it.y) } +
                carte.plan.sorties.map { Triple(it.nom ?: it.type.name, it.x, it.y) } +
                carte.plan.arcs.flatMap { a -> a.points.map { Triple(a.nom, it[0], it[1]) } }
            points.forEach { (nom, x, y) ->
                assertTrue(nom.isNotBlank() && x in 0f..1f && y in 0f..1f, "$nom sur ${carte.id}")
            }
        }
        val ville = donnees.cartes.first { it.id == "buried-city" }.plan
        assertTrue(ville.zones.any { it.nom == "Vieille ville" })
        assertTrue(ville.sorties.any { it.nom == "Trappe de la gare" && it.type == TypeSortie.TRAPPE })
        assertTrue(ville.arcs.any { it.nom == "Bondisseur" && it.points.isNotEmpty() })
    }

    @Test
    fun `comment obtenir un objet`() {
        val catalogue = Catalogue(DonneesJeu.embarquees())
        val ids = catalogue.donnees.cartes.map { it.id }.toSet()

        val batterie = catalogue.objet("battery")!!.obtention
        assertTrue(batterie.cartes.isNotEmpty() && batterie.zones.isNotEmpty())
        assertEquals(100, batterie.cartes.first().indice)
        assertEquals(100, batterie.zones.first().indice)
        assertTrue((batterie.cartes.map { it.carte } + batterie.zones.map { it.carte }).all { it in ids })
        assertTrue(batterie.zones.all { it.zone.isNotBlank() && it.indice in 1..100 })

        val butin = catalogue.objet("looting_mk2")!!.obtention
        assertTrue(butin.fabrications.any { it.etabli != null && it.niveau == 2 && it.ingredients.isNotEmpty() })
        assertTrue(butin.achats.any { it.commercant == "Lance" && it.pieces > 0 })
        assertTrue(butin.recompenses.isNotEmpty())

        // Citron : uniquement sur ses arbres, surtout au Barrage.
        assertEquals("dam-battleground", catalogue.objet("lemon")!!.obtention.cartes.first().carte)

        val sources = catalogue.sourcesRecyclage("metal_parts")
        assertTrue(sources.size > 10)
        assertTrue(sources.zipWithNext().all { (a, b) -> a.quantite >= b.quantite })
    }
}
