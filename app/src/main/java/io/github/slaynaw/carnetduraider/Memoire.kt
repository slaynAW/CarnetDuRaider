package io.github.slaynaw.carnetduraider

import android.content.Context
import io.github.slaynaw.carnetduraider.core.Objectif

/**
 * Ce que le joueur a saisi (sa réserve et ses objectifs), gardé sur le téléphone uniquement.
 * Format texte simple, une entrée par ligne, lisible et stable d'une version à l'autre.
 */
class Memoire(context: Context) {
    private val prefs = context.getSharedPreferences("carnet", Context.MODE_PRIVATE)

    fun lireReserve(): Map<String, Int> =
        prefs.getString(RESERVE, "").orEmpty().lines().mapNotNull { ligne ->
            val (id, quantite) = ligne.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            quantite.toIntOrNull()?.takeIf { it > 0 }?.let { id to it }
        }.toMap()

    fun ecrireReserve(reserve: Map<String, Int>) {
        prefs.edit().putString(RESERVE, reserve.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    fun lireObjectifs(): List<Objectif> =
        prefs.getString(OBJECTIFS, "").orEmpty().lines().mapNotNull(::decoder)

    fun ecrireObjectifs(objectifs: List<Objectif>) {
        prefs.edit().putString(OBJECTIFS, objectifs.joinToString("\n", transform = ::encoder)).apply()
    }

    private fun encoder(o: Objectif): String = when (o) {
        is Objectif.NiveauDEtabli -> "etabli:${o.etabliId}:${o.niveau}"
        is Objectif.QueteATerminer -> "quete:${o.queteId}"
    }

    private fun decoder(ligne: String): Objectif? {
        val parties = ligne.split(':')
        return when {
            parties.size == 3 && parties[0] == "etabli" ->
                parties[2].toIntOrNull()?.let { Objectif.NiveauDEtabli(parties[1], it) }
            parties.size == 2 && parties[0] == "quete" -> Objectif.QueteATerminer(parties[1])
            else -> null
        }
    }

    private companion object {
        const val RESERVE = "reserve"
        const val OBJECTIFS = "objectifs"
    }
}
