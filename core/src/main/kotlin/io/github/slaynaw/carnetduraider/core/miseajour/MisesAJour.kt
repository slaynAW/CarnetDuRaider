package io.github.slaynaw.carnetduraider.core.miseajour

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Un fichier de la version, pour une plateforme (« android »). */
@Serializable
data class FichierPublie(
    @SerialName("platform") val plateforme: String,
    @SerialName("name") val nom: String,
    @SerialName("size") val taille: Long,
    val sha256: String,
)

/**
 * Manifeste d'une version publiée (« update.json »), produit par la CI
 * (`.github/scripts/manifeste-mise-a-jour.sh`). Mêmes champs que celui de Patronus.
 */
@Serializable
data class Manifeste(
    val format: Int,
    val version: String,
    /** Croît à chaque build (numéro d'exécution de la CI) : c'est lui qui est comparé. */
    val code: Long,
    val date: String = "",
    @SerialName("notes") val nouveautes: String = "",
    @SerialName("files") val fichiers: List<FichierPublie> = emptyList(),
) {
    fun fichier(plateforme: String): FichierPublie? = fichiers.firstOrNull { it.plateforme == plateforme }
}

/** Échec d'une recherche ou d'un téléchargement de mise à jour. */
class ErreurMiseAJour(val raison: Raison, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Raison {
        /** Aucune version publiée ne contient encore de manifeste. */
        NON_PUBLIEE,

        /** Signature absente ou invalide : manifeste refusé. */
        SIGNATURE,

        /** Manifeste mal formé. */
        INVALIDE,

        /** Fichier téléchargé différent de celui annoncé. */
        CORROMPU,

        /** Réseau indisponible, serveur en erreur. */
        RESEAU,
    }
}

/**
 * Vérification des mises à jour. Chaque version officielle publiée par la CI contient
 * « update.json » signé avec la clé de signature de l'APK (« update.json.sig » : RSA PKCS#1 v1.5 /
 * SHA-256, en base64). Un manifeste n'est accepté que si sa signature est valide, et un fichier que
 * s'il a exactement la taille et l'empreinte annoncées. Voir docs/MISES-A-JOUR.md.
 */
object MisesAJour {
    /** Versions publiées (GitHub Releases du dépôt). */
    const val ADRESSE = "https://github.com/slaynAW/CarnetDuRaider/releases"
    const val MANIFESTE = "update.json"
    const val FORMAT = 1
    const val PLATEFORME = "android"
    const val TAILLE_MAX = 100L shl 20
    const val TAILLE_MAX_MANIFESTE = 64 shl 10
    const val TAILLE_MAX_SIGNATURE = 4096

    private val motifVersion = Regex("^[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}$")
    private val motifNom = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val motifDate = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val motifSha256 = Regex("^[0-9a-f]{64}$")
    private val json = Json { ignoreUnknownKeys = true }

    /** Clé publique RSA (SubjectPublicKeyInfo DER, en base64). */
    fun clePublique(base64: String): PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64)))

    /** Vrai si [signature] (base64) authentifie le manifeste brut [donnees]. */
    fun verifier(cle: PublicKey, donnees: ByteArray, signature: String): Boolean = try {
        val octets = Base64.getDecoder().decode(signature.trim())
        Signature.getInstance("SHA256withRSA").run {
            initVerify(cle)
            update(donnees)
            verify(octets)
        }
    } catch (e: IllegalArgumentException) {
        false
    } catch (e: GeneralSecurityException) {
        false
    }

    /** Lit et contrôle un manifeste (déjà authentifié par [verifier]). */
    fun lire(donnees: ByteArray): Manifeste {
        val m = try {
            json.decodeFromString<Manifeste>(donnees.decodeToString())
        } catch (e: SerializationException) {
            throw invalide("manifeste illisible", e)
        } catch (e: IllegalArgumentException) {
            throw invalide("manifeste illisible", e)
        }
        when {
            m.format != FORMAT -> throw invalide("format de manifeste ${m.format} non pris en charge")
            !motifVersion.matches(m.version) -> throw invalide("numéro de version invalide")
            m.code <= 0 -> throw invalide("code de version invalide")
            m.date.isNotEmpty() && !motifDate.matches(m.date) -> throw invalide("date invalide")
            m.nouveautes.length > 32 * 1024 -> throw invalide("nouveautés trop longues")
        }
        val vues = HashSet<String>()
        for (f in m.fichiers) {
            when {
                f.plateforme.isEmpty() || !vues.add(f.plateforme) -> throw invalide("plateforme absente ou en double")
                !motifNom.matches(f.nom) -> throw invalide("nom de fichier invalide")
                f.taille <= 0 || f.taille > TAILLE_MAX -> throw invalide("taille invalide")
                !motifSha256.matches(f.sha256) -> throw invalide("empreinte invalide")
            }
        }
        return m
    }

    /** Adresse de téléchargement d'un fichier de la version (tag « vX.Y.Z »). */
    fun adresseFichier(base: String, manifeste: Manifeste, fichier: FichierPublie): String =
        "$base/download/v${manifeste.version}/${fichier.nom}"

    private fun invalide(message: String, cause: Throwable? = null) =
        ErreurMiseAJour(ErreurMiseAJour.Raison.INVALIDE, message, cause)
}

/** Recherche et téléchargement des versions publiées sous [base], authentifiées par [cle]. */
class ClientMiseAJour(
    private val cle: PublicKey,
    private val base: String = MisesAJour.ADRESSE,
    private val agentUtilisateur: String = "CarnetDuRaider",
) {
    /** Manifeste de la dernière version officielle, authentifié et contrôlé. */
    suspend fun derniereVersion(): Manifeste = withContext(Dispatchers.IO) {
        val adresse = "$base/latest/download/${MisesAJour.MANIFESTE}"
        val donnees = lire(adresse, MisesAJour.TAILLE_MAX_MANIFESTE)
        val signature = lire("$adresse.sig", MisesAJour.TAILLE_MAX_SIGNATURE).decodeToString()
        if (!MisesAJour.verifier(cle, donnees, signature)) {
            throw ErreurMiseAJour(ErreurMiseAJour.Raison.SIGNATURE, "signature de la mise à jour invalide")
        }
        MisesAJour.lire(donnees)
    }

    /**
     * Télécharge [fichier] dans [destination] ; [destination] n'est écrit que si le fichier est
     * complet et conforme à l'empreinte annoncée. [progression] reçoit les octets reçus et le total.
     */
    suspend fun telecharger(
        manifeste: Manifeste,
        fichier: FichierPublie,
        destination: File,
        progression: (Long, Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val connexion = ouvrir(MisesAJour.adresseFichier(base, manifeste, fichier))
        val partiel = File(destination.parentFile, destination.name + ".part")
        try {
            if (connexion.responseCode != HttpURLConnection.HTTP_OK) {
                throw ErreurMiseAJour(ErreurMiseAJour.Raison.RESEAU, "téléchargement\u00a0: réponse ${connexion.responseCode}")
            }
            val empreinte = MessageDigest.getInstance("SHA-256")
            var recus = 0L
            connexion.inputStream.use { entree ->
                partiel.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = entree.read(tampon)
                        if (n < 0) break
                        recus += n
                        if (recus > fichier.taille) break
                        sortie.write(tampon, 0, n)
                        empreinte.update(tampon, 0, n)
                        progression(recus, fichier.taille)
                    }
                }
            }
            val hex = empreinte.digest().joinToString("") { "%02x".format(it) }
            if (recus != fichier.taille || hex != fichier.sha256) {
                throw ErreurMiseAJour(ErreurMiseAJour.Raison.CORROMPU, "le fichier téléchargé ne correspond pas à la version publiée")
            }
            if (!partiel.renameTo(destination)) throw IOException("écriture impossible\u00a0: ${destination.name}")
        } catch (e: ErreurMiseAJour) {
            throw e
        } catch (e: IOException) {
            throw ErreurMiseAJour(ErreurMiseAJour.Raison.RESEAU, e.message ?: "téléchargement interrompu", e)
        } finally {
            partiel.delete()
            connexion.disconnect()
        }
    }

    private fun ouvrir(adresse: String): HttpURLConnection =
        (URI(adresse).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", agentUtilisateur)
        }

    private fun lire(adresse: String, limite: Int): ByteArray {
        val connexion = ouvrir(adresse)
        try {
            when (connexion.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> throw ErreurMiseAJour(ErreurMiseAJour.Raison.NON_PUBLIEE, "aucune mise à jour publiée")
                else -> throw ErreurMiseAJour(ErreurMiseAJour.Raison.RESEAU, "réponse ${connexion.responseCode} du serveur des mises à jour")
            }
            // Lecture bornée (InputStream.readNBytes n'existe qu'à partir d'Android 13).
            val sortie = ByteArrayOutputStream()
            connexion.inputStream.use { entree ->
                val tampon = ByteArray(8 * 1024)
                while (true) {
                    val n = entree.read(tampon)
                    if (n < 0) break
                    sortie.write(tampon, 0, n)
                    if (sortie.size() > limite) throw ErreurMiseAJour(ErreurMiseAJour.Raison.INVALIDE, "réponse trop volumineuse")
                }
            }
            return sortie.toByteArray()
        } catch (e: ErreurMiseAJour) {
            throw e
        } catch (e: IOException) {
            throw ErreurMiseAJour(ErreurMiseAJour.Raison.RESEAU, e.message ?: "réseau indisponible", e)
        } finally {
            connexion.disconnect()
        }
    }
}
