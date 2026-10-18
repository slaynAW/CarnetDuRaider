package io.github.slaynaw.carnetduraider.sauvegarde

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.slaynaw.carnetduraider.core.sauvegarde.ChiffrementSauvegarde
import io.github.slaynaw.carnetduraider.core.sauvegarde.Instantane
import io.github.slaynaw.carnetduraider.core.sauvegarde.MotDePasseIncorrect
import io.github.slaynaw.carnetduraider.core.sauvegarde.SauvegardeIllisible
import io.github.slaynaw.carnetduraider.core.sauvegarde.Valeur
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** État de la sauvegarde, pour les réglages. */
data class EtatSauvegarde(
    val active: Boolean = false,
    val fichier: String? = null,
    val derniere: Long = 0,
    val erreur: String? = null,
    val enCours: Boolean = false,
)

/**
 * Sauvegarde automatique et chiffrée des données du joueur (fichiers de réglages « carnet » et
 * « alertes »), dans un fichier que le joueur choisit avec le sélecteur d'Android : Google Drive pour la
 * retrouver sur un autre téléphone, ou un dossier du téléphone. L'appli n'ouvre aucune connexion :
 * c'est le service choisi qui range le fichier.
 *
 * Le fichier est chiffré avec le mot de passe du joueur ([ChiffrementSauvegarde]). Pour réécrire le
 * fichier à chaque changement sans redemander le mot de passe, la clé qui en est tirée est gardée sur
 * le téléphone, elle-même chiffrée par une clé du Keystore d'Android (jamais extractible). Le mot de
 * passe, lui, n'est jamais enregistré.
 */
class GestionnaireSauvegarde(context: Context, private val portee: CoroutineScope) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("sauvegarde", Context.MODE_PRIVATE)
    private val sauvegardes = FICHIERS.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
    private val verrou = Mutex()
    private var prevue: Job? = null
    private val _etat = MutableStateFlow(lireEtat())
    val etat: StateFlow<EtatSauvegarde> = _etat.asStateFlow()

    // Gardé dans un champ : Android ne retient les écouteurs que faiblement.
    private val ecouteur = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> programmer() }

    init {
        sauvegardes.values.forEach { it.registerOnSharedPreferenceChangeListener(ecouteur) }
    }

    /** Active la sauvegarde dans [uri] (fichier créé par le joueur), chiffrée avec [motDePasse]. */
    suspend fun activer(uri: Uri, motDePasse: CharArray): String? = verrou.withLock {
        _etat.value = _etat.value.copy(enCours = true, erreur = null)
        val cle = withContext(Dispatchers.Default) { ChiffrementSauvegarde.deriver(motDePasse) }
        motDePasse.fill(' ')
        withContext(Dispatchers.IO) { enregistrerCible(uri, cle) }
        ecrire()
    }

    /**
     * Restaure la sauvegarde [uri] : remplace toutes les données de l'appli, puis continue à
     * sauvegarder dans ce même fichier. Renvoie un message d'erreur, ou `null` si tout s'est bien passé.
     */
    suspend fun restaurer(uri: Uri, motDePasse: CharArray): String? = verrou.withLock {
        _etat.value = _etat.value.copy(enCours = true, erreur = null)
        val resultat = withContext(Dispatchers.IO) {
            try {
                val octets = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(IOException("Fichier introuvable."))
                val (clair, cle) = ChiffrementSauvegarde.dechiffrer(octets, motDePasse)
                Result.success(Instantane.depuisOctets(clair) to cle)
            } catch (e: MotDePasseIncorrect) {
                Result.failure(e)
            } catch (e: SauvegardeIllisible) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(IOException("Fichier illisible."))
            } catch (e: SecurityException) {
                Result.failure(IOException("Fichier illisible."))
            } finally {
                motDePasse.fill(' ')
            }
        }
        val (instantane, cle) = resultat.getOrElse { e ->
            _etat.value = _etat.value.copy(enCours = false)
            return@withLock e.message ?: "Restauration impossible."
        }
        withContext(Dispatchers.IO) {
            appliquer(instantane)
            enregistrerCible(uri, cle)
        }
        _etat.value = _etat.value.copy(enCours = false, derniere = instantane.enregistreLe)
        null
    }

    /** Arrête la sauvegarde automatique (le fichier déjà écrit reste là où il est). */
    fun arreter() {
        prevue?.cancel()
        prefs.getString(URI, null)?.let { ancien ->
            runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(ancien), DROITS) }
        }
        prefs.edit().clear().apply()
        _etat.value = EtatSauvegarde()
    }

    fun sauvegarderMaintenant() {
        prevue?.cancel()
        portee.launch { verrou.withLock { ecrire() } }
    }

    /** Un changement de données : sauvegarde quelques secondes plus tard (les changements groupés n'en font qu'une). */
    private fun programmer() {
        if (!_etat.value.active) return
        prevue?.cancel()
        prevue = portee.launch {
            delay(DELAI_MS)
            verrou.withLock { ecrire() }
        }
    }

    /** Écrit la sauvegarde ; renvoie un message d'erreur, ou `null`. */
    private suspend fun ecrire(): String? = withContext(Dispatchers.IO) {
        val uri = prefs.getString(URI, null)?.let(Uri::parse)
        val cle = lireCle()
        if (uri == null || cle == null) {
            _etat.value = _etat.value.copy(enCours = false)
            return@withContext null
        }
        val maintenant = System.currentTimeMillis()
        val erreur = try {
            val fichier = ChiffrementSauvegarde.chiffrer(instantane(maintenant).versOctets(), cle)
            ouvrirEnEcriture(uri).use { it.write(fichier) }
            null
        } catch (e: IOException) {
            "Écriture impossible\u00a0: le fichier a peut-être été déplacé ou supprimé."
        } catch (e: SecurityException) {
            "L'appli n'a plus accès au fichier\u00a0: choisis-le à nouveau."
        }
        if (erreur == null) prefs.edit().putLong(DERNIERE, maintenant).remove(ERREUR).apply() else prefs.edit().putString(ERREUR, erreur).apply()
        _etat.value = lireEtat()
        erreur
    }

    private fun ouvrirEnEcriture(uri: Uri) =
        // « wt » vide le fichier avant d'écrire ; certains services ne connaissent que « w ».
        runCatching { app.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: app.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Fichier introuvable")

    private fun instantane(maintenant: Long) = Instantane(
        enregistreLe = maintenant,
        fichiers = sauvegardes.mapValues { (nom, p) ->
            p.all.filterKeys { (nom to it) !in EXCLUES }.mapNotNull { (cle, v) -> valeur(v)?.let { cle to it } }.toMap()
        },
    )

    private fun valeur(v: Any?): Valeur? = when (v) {
        is String -> Valeur(texte = v)
        is Boolean -> Valeur(booleen = v)
        is Int -> Valeur(entier = v)
        is Long -> Valeur(long = v)
        is Float -> Valeur(decimal = v)
        is Set<*> -> Valeur(ensemble = v.filterIsInstance<String>().sorted())
        else -> null
    }

    /** Remplace les données de l'appli par celles de la sauvegarde. */
    private fun appliquer(instantane: Instantane) {
        for ((nom, p) in sauvegardes) {
            val edition = p.edit().clear()
            instantane.fichiers[nom].orEmpty().forEach { (cle, v) ->
                when {
                    v.texte != null -> edition.putString(cle, v.texte)
                    v.booleen != null -> edition.putBoolean(cle, v.booleen!!)
                    v.entier != null -> edition.putInt(cle, v.entier!!)
                    v.long != null -> edition.putLong(cle, v.long!!)
                    v.decimal != null -> edition.putFloat(cle, v.decimal!!)
                    v.ensemble != null -> edition.putStringSet(cle, v.ensemble!!.toSet())
                }
            }
            edition.commit()
        }
    }

    private fun enregistrerCible(uri: Uri, cle: ChiffrementSauvegarde.Cle) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, DROITS) }
        val (iv, chiffree) = protegerCle(cle.octets)
        prefs.edit()
            .putString(URI, uri.toString())
            .putString(NOM, nomDuFichier(uri))
            .putString(SEL, Base64.encodeToString(cle.sel, Base64.NO_WRAP))
            .putInt(ITERATIONS, cle.iterations)
            .putString(IV_CLE, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(CLE, Base64.encodeToString(chiffree, Base64.NO_WRAP))
            .remove(ERREUR)
            .commit()
        _etat.value = lireEtat()
    }

    private fun lireCle(): ChiffrementSauvegarde.Cle? = runCatching {
        val sel = Base64.decode(prefs.getString(SEL, null) ?: return null, Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString(IV_CLE, null) ?: return null, Base64.NO_WRAP)
        val chiffree = Base64.decode(prefs.getString(CLE, null) ?: return null, Base64.NO_WRAP)
        val octets = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, cleKeystore(), GCMParameterSpec(128, iv))
            doFinal(chiffree)
        }
        ChiffrementSauvegarde.Cle(sel, prefs.getInt(ITERATIONS, ChiffrementSauvegarde.ITERATIONS), octets)
    }.getOrNull()

    private fun protegerCle(octets: ByteArray): Pair<ByteArray, ByteArray> {
        val chiffre = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, cleKeystore()) }
        return chiffre.iv to chiffre.doFinal(octets)
    }

    /** Clé AES du Keystore d'Android, créée au premier usage, qui ne quitte jamais le téléphone. */
    private fun cleKeystore(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generateur.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generateur.generateKey()
    }

    private fun nomDuFichier(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun lireEtat() = EtatSauvegarde(
        active = prefs.getString(URI, null) != null && prefs.getString(CLE, null) != null,
        fichier = prefs.getString(NOM, null),
        derniere = prefs.getLong(DERNIERE, 0),
        erreur = prefs.getString(ERREUR, null),
    )

    companion object {
        /** Fichiers de réglages sauvegardés : les données saisies par le joueur et ses alertes. */
        val FICHIERS = listOf("carnet", "alertes")

        /** Ce qui ne concerne que ce téléphone (notifications déjà montrées). */
        private val EXCLUES = setOf("alertes" to "dejaMontrees")
        private const val DROITS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val DELAI_MS = 3_000L
        private const val ALIAS = "carnet-sauvegarde"
        private const val URI = "uri"
        private const val NOM = "nom"
        private const val SEL = "sel"
        private const val ITERATIONS = "iterations"
        private const val IV_CLE = "ivCle"
        private const val CLE = "cle"
        private const val DERNIERE = "derniere"
        private const val ERREUR = "erreur"
    }
}
