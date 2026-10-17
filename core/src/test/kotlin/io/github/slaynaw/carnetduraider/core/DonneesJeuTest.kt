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
        assertNull(catalogue.objet("sheet_metal"))
        assertTrue(donnees.cartes.none { it.id == "riven-tides" })
        assertTrue(donnees.conditions.none { it.id == "hurricane" })
        assertEquals(Catalogue.NOM_A_VERIFIER, catalogue.nomObjet("sheet_metal"))
    }

    @Test
    fun `la recherche ignore accents et majuscules et classe les debuts de nom d'abord`() {
        val resultats = catalogue.rechercher("pilote de guepe")
        assertEquals("Pilote de Guêpe", resultats.first().nom)
        assertTrue(resultats.any { it.nom == "Pilote de Guêpe endommagé" })
        assertEquals("Alliage ARC", catalogue.rechercher("ALLIAGE").first().nom)
        assertTrue(catalogue.rechercher("  ").isEmpty())
    }

    @Test
    fun `plans des cartes`() {
        val donnees = DonneesJeu.embarquees()
        for (carte in donnees.cartes) {
            assertTrue(carte.plan.zones.isNotEmpty(), "zones de ${carte.id}")
            (carte.plan.zones.map { Triple(it.nom, it.x, it.y) } + carte.plan.sorties.map { Triple(it.nom, it.x, it.y) }).forEach { (nom, x, y) ->
                assertTrue(nom.isNotBlank() && x in 0f..1f && y in 0f..1f, "$nom sur ${carte.id}")
            }
        }
        val ville = donnees.cartes.first { it.id == "buried-city" }.plan
        assertTrue(ville.zones.any { it.nom == "Vieille ville" })
        assertTrue(ville.sorties.any { it.nom == "Trappe de la gare" })
    }
}
