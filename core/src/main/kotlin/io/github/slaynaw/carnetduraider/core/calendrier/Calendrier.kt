package io.github.slaynaw.carnetduraider.core.calendrier

import io.github.slaynaw.carnetduraider.core.Catalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Régions du calendrier officiel : les conditions n'ont pas lieu à la même heure partout. La clé est
 * celle de la page d'Embark (`null` pour l'Europe, qui y sert de référence).
 */
enum class Region(val libelle: String, internal val cleEmbark: String?) {
    EUROPE("Europe", null),
    AMERIQUE_DU_NORD("Amérique du Nord", "north-america"),
    AMERIQUE_DU_SUD("Amérique du Sud", "brazil"),
    ASIE("Asie", "east-asia"),
    OCEANIE("Océanie", "oceania"),
}

/** Une condition sur une carte, de [debut] à [fin] (millisecondes depuis 1970, UTC). */
data class Creneau(
    /** Identifiant de la carte dans les données du jeu, ou `null` si elle n'y est pas (nom non vérifié). */
    val carteId: String?,
    val conditionId: String?,
    /** Noms français officiels, ou [Catalogue.NOM_A_VERIFIER]. */
    val carte: String,
    val condition: String,
    val majeure: Boolean,
    val debut: Long,
    val fin: Long,
) {
    fun enCours(maintenant: Long): Boolean = maintenant in debut until fin
}

/** Une ligne du calendrier telle que publiée par Embark (noms anglais, horaires en millisecondes). */
@Serializable
data class EntreeEmbark(
    val conditionName: String,
    val mapDisplayName: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val regionTimestamps: Map<String, List<Long>> = emptyMap(),
)

/** Type d'une condition dans le calendrier d'Embark (« major » ou « minor »). */
@Serializable
data class TypeEmbark(val name: String, val type: String = "")

/** Ce qui est gardé du calendrier officiel, sur le téléphone, entre deux lectures. */
@Serializable
data class DonneesCalendrier(
    val types: List<TypeEmbark>,
    val entrees: List<EntreeEmbark>,
    /** Date de la lecture sur le site d'Embark (millisecondes). */
    val luLe: Long,
)

/** Le calendrier n'a pas pu être lu (réseau, ou page d'Embark dans un format inconnu). */
class ErreurCalendrier(val format: Boolean, message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Calendrier officiel des conditions de la carte, publié par Embark sur arcraiders.com, avec les noms
 * français officiels des cartes et des conditions (données du jeu). Un nom qui n'y est pas reste
 * « Nom à vérifier en jeu » : jamais de nom traduit par l'appli.
 */
class Calendrier(val donnees: DonneesCalendrier, catalogue: Catalogue) {
    private val cartes = catalogue.donnees.cartes.associateBy { cle(it.cleCalendrier ?: it.id) }
    private val conditions = catalogue.donnees.conditions.associateBy { cle(it.cleCalendrier ?: it.id) }
    private val majeures = donnees.types.associate { cle(it.name) to it.type.equals("major", ignoreCase = true) }

    private val parRegion = HashMap<Region, List<Creneau>>()

    /** Tous les créneaux pour [region], dans l'ordre. */
    fun creneaux(region: Region): List<Creneau> = parRegion.getOrPut(region) { calculer(region) }

    private fun calculer(region: Region): List<Creneau> = donnees.entrees.mapNotNull { e ->
        val horaires = if (region.cleEmbark == null) listOf(e.startTimestamp, e.endTimestamp) else e.regionTimestamps[region.cleEmbark]
        val debut = horaires?.getOrNull(0) ?: return@mapNotNull null
        val fin = horaires.getOrNull(1)?.takeIf { it > debut } ?: (debut + UNE_HEURE)
        val carte = cartes[cle(e.mapDisplayName)]
        val condition = conditions[cle(e.conditionName)]
        Creneau(
            carteId = carte?.id,
            conditionId = condition?.id,
            carte = carte?.nom ?: Catalogue.NOM_A_VERIFIER,
            condition = condition?.nom ?: Catalogue.NOM_A_VERIFIER,
            majeure = majeures[cle(e.conditionName)] ?: condition?.majeure ?: false,
            debut = debut,
            fin = fin,
        )
    }.distinct().sortedWith(compareBy({ it.debut }, { !it.majeure }, { it.carte }))

    fun enCours(region: Region, maintenant: Long): List<Creneau> = creneaux(region).filter { it.enCours(maintenant) }

    /** Créneaux qui commencent dans les [duree] prochaines millisecondes. */
    fun aVenir(region: Region, maintenant: Long, duree: Long): List<Creneau> =
        creneaux(region).filter { it.debut > maintenant && it.debut <= maintenant + duree }

    /** Pour chaque condition, son prochain créneau (en cours compris), le plus proche d'abord. */
    fun prochaineFois(region: Region, maintenant: Long): List<Creneau> =
        creneaux(region).filter { it.fin > maintenant }
            .groupBy { it.conditionId ?: it.condition }
            .map { (_, liste) -> liste.first() }
            .sortedBy { it.debut }

    /** Noms anglais des cartes du calendrier absentes des données du jeu (nom français non vérifié). */
    fun cartesInconnues(): Set<String> = donnees.entrees.map { it.mapDisplayName }.filter { cartes[cle(it)] == null }.toSortedSet()

    /** Noms anglais des conditions du calendrier absentes des données du jeu. */
    fun conditionsInconnues(): Set<String> = donnees.entrees.map { it.conditionName }.filter { conditions[cle(it)] == null }.toSortedSet()

    /** Fin du dernier créneau connu : au-delà, il faut relire le calendrier. */
    fun connuJusqua(region: Region): Long = creneaux(region).maxOfOrNull { it.fin } ?: 0

    companion object {
        /** Page officielle d'Embark (en anglais, la langue des clés de correspondance). */
        const val ADRESSE = "https://arcraiders.com/map-conditions"
        const val UNE_HEURE = 3_600_000L
        private const val TAILLE_MAX_PAGE = 8 shl 20
        private val json = Json { ignoreUnknownKeys = true }

        private fun cle(nom: String): String = Catalogue.normaliser(nom).replace(Regex("[^a-z0-9]+"), "")

        /**
         * Extrait le calendrier de la page d'Embark. La page (Next.js) embarque les données en JSON
         * échappé dans une chaîne JavaScript : `\"liveEntries\":[…]` et `\"conditionItems\":[…]`.
         */
        fun lirePage(page: String, luLe: Long): DonneesCalendrier {
            val entrees = extraireTableau(page, "liveEntries")
                ?: throw ErreurCalendrier(true, "calendrier introuvable dans la page d'Embark")
            val types = extraireTableau(page, "conditionItems") ?: "[]"
            return try {
                DonneesCalendrier(
                    types = json.decodeFromString(types),
                    entrees = json.decodeFromString(entrees),
                    luLe = luLe,
                ).also { if (it.entrees.isEmpty()) throw ErreurCalendrier(true, "calendrier vide") }
            } catch (e: SerializationException) {
                throw ErreurCalendrier(true, "calendrier illisible", e)
            } catch (e: IllegalArgumentException) {
                throw ErreurCalendrier(true, "calendrier illisible", e)
            }
        }

        fun lire(texte: String): DonneesCalendrier = json.decodeFromString(texte)

        fun ecrire(donnees: DonneesCalendrier): String = json.encodeToString(DonneesCalendrier.serializer(), donnees)

        /**
         * Tableau JSON qui suit la clé [cle], dans la page brute (clé échappée `\"cle\":`) ou en JSON
         * ordinaire (`"cle":`). Le tableau est déséchappé d'un niveau en le parcourant, en respectant
         * les chaînes JSON (un crochet dans un nom ne fausse pas la lecture).
         */
        internal fun extraireTableau(page: String, cle: String): String? {
            for (echappe in listOf(true, false)) {
                val jeton = if (echappe) "\\\"$cle\\\":" else "\"$cle\":"
                val position = page.indexOf(jeton)
                if (position < 0) continue
                var i = page.indexOf('[', position + jeton.length)
                if (i < 0 || page.substring(position + jeton.length, i).isNotBlank()) continue
                val sortie = StringBuilder()
                var profondeur = 0
                var dansChaine = false
                var echappement = false
                while (i < page.length) {
                    var c = page[i]
                    if (echappe && c == '\\' && i + 1 < page.length) {
                        c = page[i + 1]
                        i += 2
                    } else {
                        i += 1
                    }
                    sortie.append(c)
                    when {
                        dansChaine && echappement -> echappement = false
                        dansChaine && c == '\\' -> echappement = true
                        dansChaine && c == '"' -> dansChaine = false
                        c == '"' -> dansChaine = true
                        c == '[' -> profondeur++
                        c == ']' -> if (--profondeur == 0) return sortie.toString()
                    }
                }
            }
            return null
        }

        /** Lit le calendrier sur le site d'Embark. */
        suspend fun telecharger(adresse: String = ADRESSE, maintenant: Long = System.currentTimeMillis()): DonneesCalendrier =
            withContext(Dispatchers.IO) {
                val connexion = (URI(adresse).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "CarnetDuRaider")
                    setRequestProperty("Accept", "text/html")
                }
                try {
                    if (connexion.responseCode != HttpURLConnection.HTTP_OK) {
                        throw ErreurCalendrier(false, "réponse ${connexion.responseCode} du site d'Embark")
                    }
                    val sortie = ByteArrayOutputStream()
                    connexion.inputStream.use { entree ->
                        val tampon = ByteArray(64 * 1024)
                        while (true) {
                            val n = entree.read(tampon)
                            if (n < 0) break
                            sortie.write(tampon, 0, n)
                            if (sortie.size() > TAILLE_MAX_PAGE) throw ErreurCalendrier(true, "page trop volumineuse")
                        }
                    }
                    lirePage(sortie.toByteArray().decodeToString(), maintenant)
                } catch (e: ErreurCalendrier) {
                    throw e
                } catch (e: IOException) {
                    throw ErreurCalendrier(false, e.message ?: "réseau indisponible", e)
                } finally {
                    connexion.disconnect()
                }
            }
    }
}
