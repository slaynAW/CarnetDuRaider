package io.github.slaynaw.carnetduraider.core.calendrier

import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.DonneesJeu
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertesTest {
    private val h = Calendrier.UNE_HEURE
    private val t0 = 1_791_450_000_000L
    private val calendrier = Calendrier(
        DonneesCalendrier(
            types = listOf(TypeEmbark("ARC Frigate", "major"), TypeEmbark("Matriarch", "minor")),
            entrees = listOf(
                EntreeEmbark("ARC Frigate", "Dam Battlegrounds", t0, t0 + h),
                EntreeEmbark("Matriarch", "Spaceport", t0 + h, t0 + 2 * h),
                EntreeEmbark("ARC Frigate", "Buried City", t0 + 3 * h, t0 + 4 * h),
                EntreeEmbark("Hurricane", "Riven Tides", t0 + 2 * h, t0 + 3 * h),
            ),
            luLe = t0,
        ),
        Catalogue(DonneesJeu.embarquees()),
    )
    private val reglages = ReglagesAlertes(conditions = setOf("arc-frigate"), avanceMinutes = 10)

    @Test
    fun `alertes des conditions suivies`() {
        val avant = t0 - 30 * 60_000
        val alertes = Alertes.aVenir(calendrier, Region.EUROPE, reglages, avant)
        assertEquals(listOf(t0 - 10 * 60_000, t0 + 3 * h - 10 * 60_000), alertes.map { it.quand })
        assertEquals(listOf("Champ de bataille du Barrage", "Ville enfouie"), alertes.map { it.creneau.carte })

        // 30 min avant : rien à montrer, réveil 10 min avant le début.
        assertTrue(Alertes.aMontrer(alertes, avant, emptySet()).isEmpty())
        assertEquals(t0 - 10 * 60_000, Alertes.prochainReveil(alertes, avant, emptySet()))

        // Au réveil (un peu en avance) : la première est montrée, puis réveil pour la suivante.
        val reveil = t0 - 10 * 60_000 - 30_000
        val montrees = Alertes.aMontrer(alertes, reveil, emptySet())
        assertEquals(1, montrees.size)
        val deja = montrees.map { it.cle }.toSet()
        assertTrue(Alertes.aMontrer(alertes, reveil, deja).isEmpty(), "jamais deux fois")
        assertEquals(t0 + 3 * h - 10 * 60_000, Alertes.prochainReveil(alertes, reveil, deja))

        // Après le dernier créneau connu : plus de réveil.
        assertNull(Alertes.prochainReveil(Alertes.aVenir(calendrier, Region.EUROPE, reglages, t0 + 5 * h), t0 + 5 * h, deja))
    }

    @Test
    fun `nettoyage des alertes deja montrees`() {
        val cles = setOf("${t0}|a|b", "${t0 + 30 * h}|a|b")
        assertEquals(setOf("${t0 + 30 * h}|a|b"), Alertes.nettoyer(cles, t0 + 26 * h))
    }

    @Test
    fun `catalogue complet par ordre alphabetique`() {
        val catalogue = Catalogue(DonneesJeu.embarquees())
        assertEquals(catalogue.donnees.objets.size, catalogue.tous.size)
        val cles = catalogue.tous.map { Catalogue.normaliser(it.nom) }
        assertEquals(cles.sorted(), cles)
    }
}
