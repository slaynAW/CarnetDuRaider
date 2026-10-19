package io.github.slaynaw.carnetduraider.core.sauvegarde

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SauvegardeTest {
    // Moins de tours que dans l'appli : le test reste rapide, le format est le même.
    private val tours = 100_000
    private val instantane = Instantane(
        enregistreLe = 1_700_000_000_000,
        fichiers = mapOf(
            "carnet" to mapOf(
                "reserve" to Valeur(texte = "metal_parts=12\nbattery=3"),
                "quetesIndiquees" to Valeur(booleen = true),
            ),
            "alertes" to mapOf(
                "conditions" to Valeur(ensemble = listOf("cold-snap", "night-raid")),
                "avance" to Valeur(entier = 10),
            ),
        ),
    )

    @Test
    fun `aller et retour avec le bon mot de passe`() {
        val cle = ChiffrementSauvegarde.deriver("mon mot de passe".toCharArray(), iterations = tours)
        val fichier = ChiffrementSauvegarde.chiffrer(instantane.versOctets(), cle)
        assertFalse(String(fichier, Charsets.ISO_8859_1).contains("metal_parts"), "rien en clair dans le fichier")

        val (clair, retrouvee) = ChiffrementSauvegarde.dechiffrer(fichier, "mon mot de passe".toCharArray())
        assertEquals(instantane, Instantane.depuisOctets(clair))
        assertArrayEquals(cle.octets, retrouvee.octets)
        // La clé retrouvée sert aux écritures suivantes, sans redemander le mot de passe.
        val suivant = ChiffrementSauvegarde.chiffrer("encore".toByteArray(), retrouvee)
        assertEquals("encore", String(ChiffrementSauvegarde.dechiffrer(suivant, cle)))
    }

    @Test
    fun `mauvais mot de passe ou fichier modifie`() {
        val cle = ChiffrementSauvegarde.deriver("bon".toCharArray(), iterations = tours)
        val fichier = ChiffrementSauvegarde.chiffrer(instantane.versOctets(), cle)
        assertThrows<MotDePasseIncorrect> { ChiffrementSauvegarde.dechiffrer(fichier, "mauvais".toCharArray()) }
        val modifie = fichier.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        assertThrows<MotDePasseIncorrect> { ChiffrementSauvegarde.dechiffrer(modifie, cle) }
        // L'en-tête est authentifié aussi : changer le sel ne passe pas.
        val selModifie = fichier.copyOf().also { it[14] = (it[14] + 1).toByte() }
        assertThrows<MotDePasseIncorrect> { ChiffrementSauvegarde.dechiffrer(selModifie, cle) }
    }

    @Test
    fun `fichier qui n'est pas une sauvegarde`() {
        assertThrows<SauvegardeIllisible> { ChiffrementSauvegarde.dechiffrer("bonjour".toByteArray(), "x".toCharArray()) }
        assertThrows<SauvegardeIllisible> { ChiffrementSauvegarde.dechiffrer(ByteArray(200), "x".toCharArray()) }
        assertThrows<SauvegardeIllisible> { Instantane.depuisOctets("pas du json".toByteArray()) }
    }

    @Test
    fun `deux ecritures ne donnent jamais le meme fichier`() {
        val cle = ChiffrementSauvegarde.deriver("mdp".toCharArray(), iterations = tours)
        val a = ChiffrementSauvegarde.chiffrer(instantane.versOctets(), cle)
        val b = ChiffrementSauvegarde.chiffrer(instantane.versOctets(), cle)
        assertFalse(a.contentEquals(b))
    }
}
