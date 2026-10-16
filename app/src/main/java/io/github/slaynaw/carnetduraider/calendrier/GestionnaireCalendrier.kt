package io.github.slaynaw.carnetduraider.calendrier

import android.content.Context
import android.util.Log
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.DonneesCalendrier
import io.github.slaynaw.carnetduraider.core.calendrier.ErreurCalendrier
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
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

/** État du calendrier officiel, pour l'interface. */
data class EtatCalendrier(
    val donnees: DonneesCalendrier? = null,
    val chargement: Boolean = false,
    val erreur: String? = null,
)

/**
 * Calendrier officiel des conditions de la carte, lu sur le site d'Embark puis gardé sur le
 * téléphone : il couvre plusieurs jours, et reste donc utilisable sans connexion. Relu au plus une
 * fois par heure, quand l'onglet Cartes ou l'accueil s'affiche.
 */
class GestionnaireCalendrier(context: Context, private val portee: CoroutineScope) {
    private val fichier = File(context.applicationContext.filesDir, "calendrier.json")
    private val verrou = Mutex()
    private val _etat = MutableStateFlow(EtatCalendrier())
    val etat: StateFlow<EtatCalendrier> = _etat.asStateFlow()

    init {
        portee.launch {
            val enCache = withContext(Dispatchers.IO) { lireCache() }
            if (enCache != null) _etat.update { if (it.donnees == null) it.copy(donnees = enCache) else it }
        }
    }

    /** Calendrier connu (en mémoire, sinon celui gardé sur le téléphone), sans connexion. */
    suspend fun donneesDisponibles(): DonneesCalendrier? = _etat.value.donnees ?: withContext(Dispatchers.IO) { lireCache() }

    /** Relit le calendrier s'il date de plus d'une heure (ou s'il n'y en a pas encore). */
    fun actualiserSiNecessaire() {
        val e = _etat.value
        if (e.chargement) return
        val luLe = e.donnees?.luLe ?: 0
        if (System.currentTimeMillis() - luLe < INTERVALLE_MS) return
        portee.launch { actualiser() }
    }

    suspend fun actualiser() = verrou.withLock {
        _etat.update { it.copy(chargement = true, erreur = null) }
        try {
            val donnees = Calendrier.telecharger()
            withContext(Dispatchers.IO) { ecrireCache(donnees) }
            _etat.update { it.copy(donnees = donnees, chargement = false) }
        } catch (e: ErreurCalendrier) {
            Log.w(JOURNAL, "calendrier illisible (format : ${e.format})", e)
            val message = if (e.format) {
                "Le calendrier d'Embark a changé de format\u00a0: l'appli ne sait pas encore le lire. Ouvre-le dans le navigateur."
            } else {
                "Le site d'Embark ne répond pas. Vérifie ta connexion et réessaie."
            }
            _etat.update { it.copy(chargement = false, erreur = message) }
        }
    }

    private fun lireCache(): DonneesCalendrier? = try {
        if (fichier.exists()) Calendrier.lire(fichier.readText()) else null
    } catch (e: IOException) {
        null
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun ecrireCache(donnees: DonneesCalendrier) {
        val temporaire = File(fichier.parentFile, fichier.name + ".tmp")
        temporaire.writeText(Calendrier.ecrire(donnees))
        if (!temporaire.renameTo(fichier)) temporaire.delete()
    }

    private companion object {
        const val JOURNAL = "Calendrier"
        const val INTERVALLE_MS = 60 * 60 * 1000L
    }
}
