package io.github.slaynaw.carnetduraider.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** « Butin recherché » : des objets à rapporter sans objectif du jeu. Sur les vraies données du jeu 2.0. */
class ButinRechercheTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val planificateur = Planificateur(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur)
    private val stationUtilitaire1 = Objectif.NiveauDEtabli("utility_bench", 1)

    @Test
    fun `ajouter, changer, retirer et enregistrer`() {
        var liste = ButinRecherche.ajouter(emptyMap(), "battery")
        liste = ButinRecherche.ajouter(liste, "battery", 2)
        liste = ButinRecherche.ajouter(liste, "arc_alloy")
        assertEquals(mapOf("battery" to 3, "arc_alloy" to 1), liste)
        assertEquals(mapOf("battery" to 3), ButinRecherche.changer(liste, "arc_alloy", 0))
        assertEquals(ButinRecherche.MAXIMUM, ButinRecherche.changer(liste, "battery", 5000)["battery"])
        assertEquals(liste, ButinRecherche.decoder(ButinRecherche.encoder(liste)))
        assertEquals(Objectif.ObjetRecherche("battery", 3).cle, ButinRecherche.cle("battery"))
        assertEquals(mapOf("wires" to 2), ButinRecherche.decoder("wires=2\npas une ligne\n=3\nbattery=0"))
        assertEquals(emptyMap<String, Int>(), ButinRecherche.decoder(null))
    }

    @Test
    fun `les objets recherches s'ajoutent a la liste du raid`() {
        val objectifs = listOf(stationUtilitaire1) + ButinRecherche.objectifs(mapOf("arc_alloy" to 2, "battery" to 1))
        val liste = planificateur.listeDuRaid(objectifs, emptyMap())
        val alliage = liste.single { it.objetId == "arc_alloy" }
        assertEquals(6 + 2, alliage.voulu)
        assertEquals(listOf("Station utilitaire · niveau 1", ButinRecherche.TITRE), alliage.pour.map { it.libelle })
        assertEquals(listOf(ButinRecherche.TITRE), liste.single { it.objetId == "battery" }.pour.map { it.libelle })
        // Tant qu'il en manque, l'objet recherché est à garder.
        val verdict = conseiller.verdict("battery", objectifs)
        assertTrue(verdict is Verdict.Garder && verdict.pour.single().libelle == ButinRecherche.TITRE, "$verdict")
    }

    @Test
    fun `au retour de raid, les objectifs du jeu d'abord, puis le butin recherche`() {
        // Le butin recherché (1 objet) est plus près d'être terminé que la Station utilitaire : il passe quand même après.
        val objectifs = ButinRecherche.objectifs(mapOf("arc_alloy" to 1)) + stationUtilitaire1
        val apports = planificateur.repartir(objectifs, emptyMap(), mapOf("arc_alloy" to 7))
        assertEquals(6, apports[stationUtilitaire1.cle]?.get("arc_alloy"))
        assertEquals(1, ButinRecherche.rapportes(apports, "arc_alloy"))
        // Seulement 3 : tout va à la Station utilitaire.
        val peu = planificateur.repartir(objectifs, emptyMap(), mapOf("arc_alloy" to 3))
        assertEquals(0, ButinRecherche.rapportes(peu, "arc_alloy"))
    }

    @Test
    fun `un objet dont tout a ete rapporte quitte la liste`() {
        val liste = mapOf("battery" to 2, "wires" to 3)
        val apports = planificateur.repartir(ButinRecherche.objectifs(liste), emptyMap(), mapOf("battery" to 2, "wires" to 1))
        val (reste, apportsApres, termines) = ButinRecherche.terminerLesComplets(liste, apports)
        assertEquals(listOf("battery"), termines)
        assertEquals(mapOf("wires" to 3), reste)
        assertEquals(1, ButinRecherche.rapportes(apportsApres, "wires"))
        assertEquals(0, ButinRecherche.rapportes(apportsApres, "battery"))
        // Rien de complet : rien ne change.
        assertEquals(Triple(reste, apportsApres, emptyList<String>()), ButinRecherche.terminerLesComplets(reste, apportsApres))
        // Le butin recherché ne touche pas à la progression du jeu.
        val suivi = SuiviProgression(catalogue)
        val p = Progression()
        assertEquals(p, suivi.terminer(p, Objectif.ObjetRecherche("battery", 2)))
    }
}
