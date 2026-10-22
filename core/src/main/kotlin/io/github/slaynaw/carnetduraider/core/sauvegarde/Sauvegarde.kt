package io.github.slaynaw.carnetduraider.core.sauvegarde

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sauvegarde chiffrée des données du joueur (ce qu'il a en cours, ce qu'il a rapporté, ses builds, réglages).
 *
 * Le fichier est chiffré avec le mot de passe du joueur : clé tirée du mot de passe par PBKDF2
 * (HMAC-SHA256, [ITERATIONS] tours, sel aléatoire), puis AES-256-GCM (IV aléatoire à chaque écriture).
 * L'en-tête (format, tours, sel, IV) est authentifié avec le contenu : toute modification du fichier,
 * comme un mauvais mot de passe, le rend illisible. Sans le mot de passe, personne ne peut le lire.
 *
 * Format : « CDRSAUV1 » | tours (4 octets) | sel (16) | IV (12) | contenu chiffré et étiquette GCM.
 */
object ChiffrementSauvegarde {
    const val ITERATIONS = 600_000
    private val FORMAT = "CDRSAUV1".toByteArray(Charsets.US_ASCII)
    private const val TAILLE_SEL = 16
    private const val TAILLE_IV = 12
    private const val TAILLE_ENTETE = 8 + 4 + TAILLE_SEL + TAILLE_IV
    private val TOURS_ACCEPTES = 100_000..10_000_000
    private val hasard = SecureRandom()

    /** Clé tirée du mot de passe, avec le sel et le nombre de tours qui permettent de la retrouver. */
    class Cle(val sel: ByteArray, val iterations: Int, val octets: ByteArray)

    /** Tire la clé du mot de passe (lent à dessein : environ une seconde sur un téléphone). */
    fun deriver(motDePasse: CharArray, sel: ByteArray = aleatoire(TAILLE_SEL), iterations: Int = ITERATIONS): Cle {
        val spec = PBEKeySpec(motDePasse, sel, iterations, 256)
        try {
            val octets = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Cle(sel.copyOf(), iterations, octets)
        } finally {
            spec.clearPassword()
        }
    }

    fun chiffrer(clair: ByteArray, cle: Cle): ByteArray {
        val iv = aleatoire(TAILLE_IV)
        val entete = ByteBuffer.allocate(TAILLE_ENTETE).put(FORMAT).putInt(cle.iterations).put(cle.sel).put(iv).array()
        val chiffre = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(cle.octets, "AES"), GCMParameterSpec(128, iv))
            updateAAD(entete)
            doFinal(clair)
        }
        return entete + chiffre
    }

    /** Déchiffre avec le mot de passe ; renvoie aussi la clé, pour continuer à écrire dans ce fichier. */
    fun dechiffrer(fichier: ByteArray, motDePasse: CharArray): Pair<ByteArray, Cle> {
        val (iterations, sel) = lireEntete(fichier)
        val cle = deriver(motDePasse, sel, iterations)
        return dechiffrer(fichier, cle) to cle
    }

    fun dechiffrer(fichier: ByteArray, cle: Cle): ByteArray {
        lireEntete(fichier)
        val entete = fichier.copyOfRange(0, TAILLE_ENTETE)
        val iv = fichier.copyOfRange(TAILLE_ENTETE - TAILLE_IV, TAILLE_ENTETE)
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(cle.octets, "AES"), GCMParameterSpec(128, iv))
                updateAAD(entete)
                doFinal(fichier, TAILLE_ENTETE, fichier.size - TAILLE_ENTETE)
            }
        } catch (e: GeneralSecurityException) {
            throw MotDePasseIncorrect()
        }
    }

    private fun lireEntete(fichier: ByteArray): Pair<Int, ByteArray> {
        if (fichier.size <= TAILLE_ENTETE + 16 || !fichier.copyOfRange(0, FORMAT.size).contentEquals(FORMAT)) {
            throw SauvegardeIllisible("Ce fichier n'est pas une sauvegarde du Carnet du Raider.")
        }
        val tampon = ByteBuffer.wrap(fichier, FORMAT.size, TAILLE_ENTETE - FORMAT.size)
        val iterations = tampon.int
        if (iterations !in TOURS_ACCEPTES) throw SauvegardeIllisible("Sauvegarde abîmée ou d'un format inconnu.")
        val sel = ByteArray(TAILLE_SEL).also { tampon.get(it) }
        return iterations to sel
    }

    private fun aleatoire(taille: Int) = ByteArray(taille).also { hasard.nextBytes(it) }
}

/** Le fichier n'est pas une sauvegarde lisible (autre fichier, format inconnu). */
class SauvegardeIllisible(message: String) : Exception(message)

/** Mauvais mot de passe, ou fichier modifié depuis la sauvegarde (le chiffrement ne distingue pas les deux). */
class MotDePasseIncorrect : Exception("Mot de passe incorrect, ou fichier modifié depuis la sauvegarde.")

/**
 * Ce que contient une sauvegarde : les réglages enregistrés de l'appli, fichier par fichier
 * (« carnet », « alertes »…), clé par clé, avec leur type pour les rendre à l'identique.
 */
@Serializable
data class Instantane(
    val version: Int = 1,
    val enregistreLe: Long = 0,
    val fichiers: Map<String, Map<String, Valeur>> = emptyMap(),
) {
    fun versOctets(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun depuisOctets(octets: ByteArray): Instantane = try {
            json.decodeFromString(serializer(), octets.toString(Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            throw SauvegardeIllisible("Contenu de la sauvegarde illisible.")
        }
    }
}

/** Une valeur enregistrée : texte, booléen, nombre ou ensemble de textes. */
@Serializable
data class Valeur(
    val texte: String? = null,
    val booleen: Boolean? = null,
    val entier: Int? = null,
    val long: Long? = null,
    val decimal: Float? = null,
    val ensemble: List<String>? = null,
)
