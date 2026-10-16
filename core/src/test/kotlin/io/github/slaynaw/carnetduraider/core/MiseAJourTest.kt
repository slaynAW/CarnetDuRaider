package io.github.slaynaw.carnetduraider.core

import com.sun.net.httpserver.HttpServer
import io.github.slaynaw.carnetduraider.core.miseajour.ClientMiseAJour
import io.github.slaynaw.carnetduraider.core.miseajour.ErreurMiseAJour
import io.github.slaynaw.carnetduraider.core.miseajour.MisesAJour
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress

private val json = Json { ignoreUnknownKeys = true }

/** Mises à jour : manifeste signé par OpenSSL comme en CI (vecteurs-mise-a-jour.json). */
class MiseAJourTest {
    @Serializable
    private data class Invalide(val raison: String, val manifeste: String)

    @Serializable
    private data class Vecteurs(
        val clePublique: String,
        val manifeste: String,
        val signature: String,
        val manifesteModifie: String,
        val invalides: List<Invalide>,
    )

    private val v: Vecteurs = json.decodeFromString(
        MiseAJourTest::class.java.getResource("/vecteurs-mise-a-jour.json")!!.readText(),
    )
    private val cle = MisesAJour.clePublique(v.clePublique)

    @TempDir
    lateinit var dossier: File

    @Test
    fun `signature et manifeste`() {
        assertTrue(MisesAJour.verifier(cle, v.manifeste.toByteArray(), v.signature))
        assertFalse(MisesAJour.verifier(cle, v.manifesteModifie.toByteArray(), v.signature), "manifeste modifié")
        assertFalse(MisesAJour.verifier(cle, v.manifeste.toByteArray(), "pas du base64"), "signature illisible")

        val m = MisesAJour.lire(v.manifeste.toByteArray())
        assertEquals("1.2.0", m.version)
        assertEquals(57, m.code)
        assertEquals("2026-11-02", m.date)
        assertTrue("Minuteurs" in m.nouveautes)
        val apk = m.fichier(MisesAJour.PLATEFORME)!!
        assertEquals(7, apk.taille)
        assertEquals(
            "https://github.com/slaynAW/CarnetDuRaider/releases/download/v1.2.0/CarnetDuRaider-1.2.0.apk",
            MisesAJour.adresseFichier(MisesAJour.ADRESSE, m, apk),
        )

        for (cas in v.invalides) {
            val e = assertThrows(ErreurMiseAJour::class.java, { MisesAJour.lire(cas.manifeste.toByteArray()) }, cas.raison)
            assertEquals(ErreurMiseAJour.Raison.INVALIDE, e.raison, cas.raison)
        }
    }

    /** Serveur local qui imite GitHub Releases. */
    private fun servir(manifeste: String, apk: ByteArray, bloc: (String) -> Unit) {
        val serveur = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        fun route(chemin: String, corps: ByteArray) = serveur.createContext(chemin) { echange ->
            if (echange.requestURI.path != chemin) {
                echange.sendResponseHeaders(404, -1)
            } else {
                echange.sendResponseHeaders(200, corps.size.toLong())
                echange.responseBody.use { it.write(corps) }
            }
            echange.close()
        }
        route("/releases/latest/download/update.json", manifeste.toByteArray())
        route("/releases/latest/download/update.json.sig", (v.signature + "\n").toByteArray())
        route("/releases/download/v1.2.0/CarnetDuRaider-1.2.0.apk", apk)
        serveur.start()
        try {
            bloc("http://127.0.0.1:${serveur.address.port}/releases")
        } finally {
            serveur.stop(0)
        }
    }

    @Test
    fun `recherche et telechargement verifies`() = servir(v.manifeste, "contenu".toByteArray()) { base ->
        runBlocking {
            val client = ClientMiseAJour(cle, base)
            val m = client.derniereVersion()
            assertEquals("1.2.0", m.version)

            val apk = m.fichier(MisesAJour.PLATEFORME)!!
            var recus = 0L
            val destination = File(dossier, "mise-a-jour.apk")
            client.telecharger(m, apk, destination) { n, _ -> recus = n }
            assertEquals("contenu", destination.readText())
            assertEquals(7, recus)

            val autre = File(dossier, "autre.apk")
            val e = assertThrows(ErreurMiseAJour::class.java) {
                runBlocking { client.telecharger(m, apk.copy(sha256 = "0".repeat(64)), autre) }
            }
            assertEquals(ErreurMiseAJour.Raison.CORROMPU, e.raison)
            assertFalse(autre.exists(), "fichier corrompu écrit")
            assertEquals(listOf("mise-a-jour.apk"), dossier.list()!!.toList(), "fichiers temporaires restants")
        }
    }

    @Test
    fun `fichier plus long que annonce`() = servir(v.manifeste, "contenu en trop".toByteArray()) { base ->
        runBlocking {
            val client = ClientMiseAJour(cle, base)
            val m = client.derniereVersion()
            val e = assertThrows(ErreurMiseAJour::class.java) {
                runBlocking { client.telecharger(m, m.fichier(MisesAJour.PLATEFORME)!!, File(dossier, "a.apk")) }
            }
            assertEquals(ErreurMiseAJour.Raison.CORROMPU, e.raison)
            assertEquals(0, dossier.list()!!.size, "fichiers temporaires restants")
        }
    }

    @Test
    fun `manifeste modifie ou absent`() {
        servir(v.manifesteModifie, ByteArray(0)) { base ->
            val e = assertThrows(ErreurMiseAJour::class.java) { runBlocking { ClientMiseAJour(cle, base).derniereVersion() } }
            assertEquals(ErreurMiseAJour.Raison.SIGNATURE, e.raison)
        }
        servir(v.manifeste, ByteArray(0)) { base ->
            val e = assertThrows(ErreurMiseAJour::class.java) { runBlocking { ClientMiseAJour(cle, "$base/absent").derniereVersion() } }
            assertEquals(ErreurMiseAJour.Raison.NON_PUBLIEE, e.raison)
        }
    }
}
