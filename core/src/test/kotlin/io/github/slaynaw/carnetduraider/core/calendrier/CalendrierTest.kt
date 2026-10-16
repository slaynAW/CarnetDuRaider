package io.github.slaynaw.carnetduraider.core.calendrier

import com.sun.net.httpserver.HttpServer
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.DonneesJeu
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress

/** Lecture du calendrier officiel, sur une page au format de celle d'Embark (JSON échappé dans du JavaScript). */
class CalendrierTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val h = Calendrier.UNE_HEURE
    private val t0 = 1_791_450_000_000L

    private val donneesEmbark = """
        {"title":"Map Conditions","conditionItems":[
          {"name":"ARC Frigate","type":"major"},{"name":"Electromagnetic Storm","type":"major"},
          {"name":"Matriarch","type":"minor"},{"name":"Hurricane","type":"major"}],
        "liveEntries":[
          {"conditionName":"ARC Frigate","mapDisplayName":"Dam Battlegrounds","startTimestamp":$t0,"endTimestamp":${t0 + h},
           "durationInSeconds":3600,"regionTimestamps":{"north-america":[${t0 + 7 * h},${t0 + 8 * h}]}},
          {"conditionName":"Electromagnetic Storm","mapDisplayName":"The Blue Gate","startTimestamp":${t0 + 2 * h},"endTimestamp":${t0 + 3 * h}},
          {"conditionName":"Matriarch","mapDisplayName":"Spaceport","startTimestamp":${t0 + 3 * h},"endTimestamp":${t0 + 4 * h}},
          {"conditionName":"Hurricane","mapDisplayName":"Riven Tides","startTimestamp":${t0 + h},"endTimestamp":${t0 + 2 * h},
           "regionTimestamps":"${'$'}undefined","note":"crochets [dans] un \"texte\""},
          {"conditionName":"${'$'}undefined","mapDisplayName":"Spaceport","startTimestamp":${t0 + 5 * h}},
          {"conditionName":"Night Raid","mapDisplayName":"Buried City","startTimestamp":"${'$'}undefined"}]}
    """.trimIndent()

    /** Comme la page Next.js : le JSON est une chaîne JavaScript, avec guillemets et barres obliques échappés. */
    private fun page(json: String): String {
        val echappe = json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
        return "<html><script>self.__next_f.push([1,\"5:[\\\"\$\\\",\\\"div\\\",null,$echappe]\"])</script></html>"
    }

    @Test
    fun `lecture de la page et noms officiels`() {
        val donnees = Calendrier.lirePage(page(donneesEmbark), luLe = t0)
        assertEquals(4, donnees.entrees.size)
        val calendrier = Calendrier(donnees, catalogue)

        val europe = calendrier.creneaux(Region.EUROPE)
        assertEquals(listOf("Frégate ARC", "Nom à vérifier en jeu", "Tempête électromagnétique", "Matriarche"), europe.map { it.condition })
        assertEquals("Champ de bataille du Barrage", europe[0].carte)
        assertEquals("Le Portail bleu", europe[2].carte)
        assertEquals("Port spatial", europe[3].carte)
        assertEquals(Catalogue.NOM_A_VERIFIER, europe[1].carte, "carte sans nom français vérifié")
        assertTrue(europe[0].majeure)
        assertEquals(false, europe[3].majeure)

        assertEquals(listOf("Frégate ARC"), calendrier.enCours(Region.EUROPE, t0 + 10 * 60_000).map { it.condition })
        assertEquals(listOf("Tempête électromagnétique", "Matriarche"), calendrier.aVenir(Region.EUROPE, t0 + h + 1, 3 * h).map { it.condition })
        assertEquals(t0 + 4 * h, calendrier.connuJusqua(Region.EUROPE))
        assertEquals(setOf("Riven Tides"), calendrier.cartesInconnues())
        assertEquals(setOf("Hurricane"), calendrier.conditionsInconnues())

        // Autre région : ses propres horaires, et seulement les créneaux qu'Embark y annonce.
        val amerique = calendrier.creneaux(Region.AMERIQUE_DU_NORD)
        assertEquals(1, amerique.size)
        assertEquals(t0 + 7 * h, amerique[0].debut)

        val prochaines = calendrier.prochaineFois(Region.EUROPE, t0 + 2 * h + 1)
        assertEquals(listOf("Tempête électromagnétique", "Matriarche"), prochaines.map { it.condition })
    }

    @Test
    fun `cache sur le telephone`() {
        val donnees = Calendrier.lirePage(page(donneesEmbark), luLe = t0)
        assertEquals(donnees, Calendrier.lire(Calendrier.ecrire(donnees)))
    }

    @Test
    fun `page inconnue`() {
        val e = assertThrows(ErreurCalendrier::class.java) { Calendrier.lirePage("<html>Maintenance</html>", 0) }
        assertTrue(e.format)
        val vide = assertThrows(ErreurCalendrier::class.java) { Calendrier.lirePage(page("{\"liveEntries\":[]}"), 0) }
        assertTrue(vide.format)
    }

    @Test
    fun `json ordinaire accepte`() {
        assertEquals(4, Calendrier.lirePage(donneesEmbark, 0).entrees.size)
    }

    @Test
    fun `telechargement`() {
        val serveur = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val corps = page(donneesEmbark).toByteArray()
        serveur.createContext("/map-conditions") { echange ->
            echange.sendResponseHeaders(200, corps.size.toLong())
            echange.responseBody.use { it.write(corps) }
            echange.close()
        }
        serveur.start()
        try {
            val base = "http://127.0.0.1:${serveur.address.port}"
            val donnees = runBlocking { Calendrier.telecharger("$base/map-conditions", maintenant = 42) }
            assertEquals(42, donnees.luLe)
            assertEquals(4, donnees.entrees.size)
            val e = assertThrows(ErreurCalendrier::class.java) { runBlocking { Calendrier.telecharger("$base/absent") } }
            assertEquals(false, e.format)
        } finally {
            serveur.stop(0)
        }
    }
}
