package io.github.slaynaw.carnetduraider

import android.content.Context
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Progression
import io.github.slaynaw.carnetduraider.core.calendrier.Region

/**
 * Ce que le joueur a saisi (sa réserve, sa progression, ses objectifs en plus), gardé sur le téléphone
 * uniquement.
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

    fun lireProgression(): Progression = Progression(
        niveauxEtablis = lignes(NIVEAUX).mapNotNull { ligne ->
            val (id, niveau) = ligne.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            niveau.toIntOrNull()?.let { id to it }
        }.toMap(),
        quetesTerminees = lignes(QUETES_TERMINEES).toSet(),
        etablisIgnores = lignes(ETABLIS_IGNORES).toSet(),
    )

    fun ecrireProgression(p: Progression) {
        prefs.edit()
            .putString(NIVEAUX, p.niveauxEtablis.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString(QUETES_TERMINEES, p.quetesTerminees.sorted().joinToString("\n"))
            .putString(ETABLIS_IGNORES, p.etablisIgnores.sorted().joinToString("\n"))
            .apply()
    }

    /** Vrai une fois que le joueur a indiqué ses quêtes en cours (sinon, l'appli le lui propose). */
    var quetesIndiquees: Boolean
        get() = prefs.getBoolean(QUETES_INDIQUEES, false)
        set(valeur) = prefs.edit().putBoolean(QUETES_INDIQUEES, valeur).apply()

    var region: Region
        get() = Region.entries.firstOrNull { it.name == prefs.getString(REGION, null) } ?: Region.EUROPE
        set(valeur) = prefs.edit().putString(REGION, valeur.name).apply()

    private fun lignes(cle: String): List<String> = prefs.getString(cle, "").orEmpty().lines().filter { it.isNotBlank() }

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
        const val NIVEAUX = "niveauxEtablis"
        const val QUETES_TERMINEES = "quetesTerminees"
        const val ETABLIS_IGNORES = "etablisIgnores"
        const val QUETES_INDIQUEES = "quetesIndiquees"
        const val REGION = "region"
    }
}
