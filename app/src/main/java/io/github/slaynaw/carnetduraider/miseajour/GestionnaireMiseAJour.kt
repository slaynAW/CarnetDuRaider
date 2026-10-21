package io.github.slaynaw.carnetduraider.miseajour

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import io.github.slaynaw.carnetduraider.BuildConfig
import io.github.slaynaw.carnetduraider.core.miseajour.ClientMiseAJour
import io.github.slaynaw.carnetduraider.core.miseajour.ErreurMiseAJour
import io.github.slaynaw.carnetduraider.core.miseajour.Manifeste
import io.github.slaynaw.carnetduraider.core.miseajour.MisesAJour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

/** Étape d'une installation en cours. */
enum class Etape { AUCUNE, TELECHARGEMENT, INSTALLATION }

/** État des mises à jour intégrées, pour l'interface. */
data class EtatMiseAJour(
    /** Faux si le certificat de l'appli installée est illisible : rien ne pourrait être vérifié. */
    val disponible: Boolean = false,
    val automatique: Boolean = true,
    val recherche: Boolean = false,
    val derniereRecherche: Long = 0,
    val erreur: String? = null,
    val nouvelle: Manifeste? = null,
    /** « Plus tard » choisi pour cette version : elle n'est plus proposée d'elle-même pendant 24 h. */
    val reportee: Boolean = false,
    val etape: Etape = Etape.AUCUNE,
    val progression: Float = 0f,
)

/**
 * Mises à jour intégrées, comme dans Patronus : le manifeste de la dernière version officielle est
 * authentifié avec le certificat de signature de l'appli installée, l'APK téléchargé doit avoir la
 * taille et l'empreinte annoncées, puis il est installé par l'installateur d'Android, qui vérifie de
 * nouveau que la signature est la même. Une mise à jour conserve la progression et les objectifs.
 * La recherche contacte GitHub (en HTTPS) et ne transmet rien.
 */
class GestionnaireMiseAJour(context: Context, private val portee: CoroutineScope) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("mises-a-jour", Context.MODE_PRIVATE)
    private val client: ClientMiseAJour? = cleDeSignature()?.let { ClientMiseAJour(it, agentUtilisateur = "CarnetDuRaider-Android") }
    private val verrou = Mutex()
    private val _etat = MutableStateFlow(
        EtatMiseAJour(
            disponible = client != null,
            automatique = prefs.getBoolean(AUTOMATIQUE, true),
            derniereRecherche = prefs.getLong(DERNIERE_RECHERCHE, 0),
        ),
    )

    val etat: StateFlow<EtatMiseAJour> = _etat.asStateFlow()

    /** Au retour dans l'appli : recherche automatique si la dernière date d'au moins 12 h. */
    fun rechercherSiNecessaire() {
        val e = _etat.value
        if (client == null || !e.automatique || e.recherche || e.etape != Etape.AUCUNE) return
        if (System.currentTimeMillis() - e.derniereRecherche < INTERVALLE_MS) return
        portee.launch { rechercher() }
    }

    /** Recherche une nouvelle version ; renvoie l'état obtenu. */
    suspend fun rechercher(): EtatMiseAJour = verrou.withLock {
        val c = client ?: return _etat.value
        _etat.update { it.copy(recherche = true, erreur = null) }
        try {
            val manifeste = c.derniereVersion()
            val nouvelle = manifeste.takeIf { it.code > BuildConfig.VERSION_CODE && it.fichier(MisesAJour.PLATEFORME) != null }
            val maintenant = noterRecherche()
            Log.i(JOURNAL, "dernière version ${manifeste.version} (code ${manifeste.code}), ${if (nouvelle != null) "proposée" else "rien de plus récent"}")
            _etat.update { it.copy(recherche = false, derniereRecherche = maintenant, nouvelle = nouvelle, reportee = estReportee(nouvelle)) }
        } catch (e: ErreurMiseAJour) {
            if (e.raison == ErreurMiseAJour.Raison.NON_PUBLIEE) {
                val maintenant = noterRecherche()
                _etat.update { it.copy(recherche = false, derniereRecherche = maintenant, nouvelle = null) }
            } else {
                Log.w(JOURNAL, "recherche impossible (${e.raison})", e)
                _etat.update { it.copy(recherche = false, erreur = message(e)) }
            }
        }
        _etat.value
    }

    fun changerAutomatique(actif: Boolean) {
        prefs.edit().putBoolean(AUTOMATIQUE, actif).apply()
        _etat.update { it.copy(automatique = actif) }
    }

    /** « Plus tard » : la version n'est plus proposée d'elle-même pendant 24 h. */
    fun reporter() {
        val version = _etat.value.nouvelle?.version ?: return
        prefs.edit().putString(REPORT_VERSION, version).putLong(REPORT_DATE, System.currentTimeMillis()).apply()
        _etat.update { it.copy(reportee = true) }
    }

    /** Télécharge, vérifie puis confie l'APK à l'installateur d'Android (en arrière-plan). */
    fun installer() {
        val c = client ?: return
        val manifeste = _etat.value.nouvelle ?: return
        val fichier = manifeste.fichier(MisesAJour.PLATEFORME) ?: return
        if (_etat.value.etape != Etape.AUCUNE) return
        _etat.update { it.copy(etape = Etape.TELECHARGEMENT, progression = 0f, erreur = null) }
        portee.launch {
            try {
                val dossier = File(app.cacheDir, "mises-a-jour").apply { mkdirs() }
                dossier.listFiles()?.forEach { it.delete() }
                val apk = File(dossier, "CarnetDuRaider.apk")
                c.telecharger(manifeste, fichier, apk) { recus, total ->
                    _etat.update { it.copy(progression = recus.toFloat() / total) }
                }
                verifierArchive(apk, manifeste)
                _etat.update { it.copy(etape = Etape.INSTALLATION, progression = 1f) }
                withContext(Dispatchers.IO) { confier(apk) }
            } catch (e: ErreurMiseAJour) {
                echec(message(e), e)
            } catch (e: ArchiveRefusee) {
                echec(e.message, e)
            } catch (e: IOException) {
                echec(INSTALLATION_IMPOSSIBLE, e)
            } catch (e: SecurityException) {
                echec(INSTALLATION_IMPOSSIBLE, e)
            } catch (e: IllegalStateException) {
                echec(INSTALLATION_IMPOSSIBLE, e)
            }
        }
    }

    /** Réponse de l'installateur d'Android (annulation, échec) ; en cas de succès, Android relance l'appli. */
    fun installationEchouee(message: String) = echec(message, null)

    private fun echec(message: String?, cause: Throwable?) {
        Log.w(JOURNAL, "mise à jour impossible\u00a0: $message", cause)
        _etat.update { it.copy(etape = Etape.AUCUNE, progression = 0f, erreur = message ?: INSTALLATION_IMPOSSIBLE) }
    }

    private fun noterRecherche(): Long = System.currentTimeMillis().also { prefs.edit().putLong(DERNIERE_RECHERCHE, it).apply() }

    private fun estReportee(manifeste: Manifeste?): Boolean =
        manifeste != null && prefs.getString(REPORT_VERSION, null) == manifeste.version &&
            System.currentTimeMillis() - prefs.getLong(REPORT_DATE, 0) < REPORT_MS

    /** Message affiché : les erreurs réseau d'Android sont en anglais, on garde une phrase simple. */
    private fun message(e: ErreurMiseAJour): String = when (e.raison) {
        ErreurMiseAJour.Raison.RESEAU -> "GitHub ne répond pas. Vérifie ta connexion et réessaie."
        ErreurMiseAJour.Raison.SIGNATURE -> "Mise à jour refusée\u00a0: sa signature ne correspond pas à cette appli " +
            "(une version de test ne se met pas à jour toute seule)."
        ErreurMiseAJour.Raison.INVALIDE -> "Mise à jour refusée\u00a0: informations de version illisibles."
        ErreurMiseAJour.Raison.CORROMPU -> "Fichier refusé\u00a0: il ne correspond pas à la version publiée. Réessaie."
        ErreurMiseAJour.Raison.NON_PUBLIEE -> "Aucune version publiée pour l'instant."
    }

    /** L'APK doit être cette appli, dans la version annoncée. */
    private fun verifierArchive(apk: File, manifeste: Manifeste) {
        val info = infoArchive(apk) ?: throw ArchiveRefusee("Fichier refusé\u00a0: ce n'est pas une version de Carnet du Raider.")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (info.packageName != app.packageName || code != manifeste.code) {
            throw ArchiveRefusee("Fichier refusé\u00a0: ce n'est pas la version annoncée.")
        }
    }

    private fun infoArchive(apk: File) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        app.packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageArchiveInfo(apk.path, 0)
    }

    private fun confier(apk: File) {
        val installateur = app.packageManager.packageInstaller
        val parametres = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Plus de confirmation une fois que l'appli s'est elle-même mise à jour.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installateur.createSession(parametres)
        installateur.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { sortie ->
                apk.inputStream().use { it.copyTo(sortie) }
                session.fsync(sortie)
            }
            val intent = Intent(app, RecepteurInstallation::class.java).setPackage(app.packageName)
            val drapeaux = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            session.commit(PendingIntent.getBroadcast(app, id, intent, drapeaux).intentSender)
        }
    }

    /** Clé publique du certificat qui a signé l'appli installée. */
    private fun cleDeSignature(): PublicKey? = try {
        val pm = app.packageManager
        val signatures: Array<out Signature> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                    .signingInfo?.apkContentsSigners.orEmpty()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners.orEmpty()
            else ->
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        signatures.singleOrNull()?.let { signature ->
            CertificateFactory.getInstance("X.509").generateCertificate(signature.toByteArray().inputStream()).publicKey
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: CertificateException) {
        null
    }

    /** APK téléchargé intact mais qui n'est pas la version annoncée de cette appli. */
    private class ArchiveRefusee(message: String) : IOException(message)

    private companion object {
        const val INSTALLATION_IMPOSSIBLE = "Android n'a pas pu préparer l'installation. Réessaie."
        const val JOURNAL = "MiseAJour"
        const val AUTOMATIQUE = "automatique"
        const val DERNIERE_RECHERCHE = "derniereRecherche"
        const val REPORT_VERSION = "reportVersion"
        const val REPORT_DATE = "reportDate"
        const val INTERVALLE_MS = 12 * 60 * 60 * 1000L
        const val REPORT_MS = 24 * 60 * 60 * 1000L
    }
}
