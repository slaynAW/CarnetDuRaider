package io.github.slaynaw.carnetduraider.core.calendrier

import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.DonneesJeu
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * Lecture de la vraie page d'Embark, téléchargée par la CI (workflow « Calendrier officiel ») :
 * prévient si Embark change le format de sa page ou ajoute une carte ou une condition inconnue.
 */
@EnabledIfEnvironmentVariable(named = "CDR_PAGE_CONDITIONS", matches = ".+")
class CalendrierEnDirectTest {
    @Test
    fun `page officielle lisible`() {
        val page = File(System.getenv("CDR_PAGE_CONDITIONS")).readText()
        val donnees = Calendrier.lirePage(page, System.currentTimeMillis())
        val calendrier = Calendrier(donnees, Catalogue(DonneesJeu.embarquees()))

        println("Calendrier officiel : ${donnees.entrees.size} entrées, ${donnees.types.size} types de conditions.")
        for (region in Region.entries) {
            val creneaux = calendrier.creneaux(region)
            println("  ${region.libelle} : ${creneaux.size} créneaux, jusqu'au ${java.time.Instant.ofEpochMilli(calendrier.connuJusqua(region))}")
        }
        val europe = calendrier.creneaux(Region.EUROPE)
        println("  Cartes sans nom français vérifié : ${calendrier.cartesInconnues()}")
        println("  Conditions sans nom français vérifié : ${calendrier.conditionsInconnues()}")

        assertTrue(donnees.entrees.isNotEmpty(), "aucune entrée")
        assertTrue(europe.count { it.carteId != null && it.conditionId != null } >= europe.size / 2, "la plupart des noms ne sont pas reconnus")
        assertTrue(Region.entries.all { calendrier.creneaux(it).isNotEmpty() }, "une région n'a aucun créneau")
        assertTrue(calendrier.connuJusqua(Region.EUROPE) > System.currentTimeMillis(), "calendrier déjà terminé")
    }
}
