package io.github.slaynaw.carnetduraider

import android.content.Context
import io.github.slaynaw.carnetduraider.core.Apports
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Progression
import io.github.slaynaw.carnetduraider.core.calendrier.Region
import io.github.slaynaw.carnetduraider.core.cle

/**
 * Ce que le joueur a saisi (sa progression, ses objectifs en plus, ce qu'il a rapporté de raid pour eux),
 * gardé sur le téléphone uniquement.
 * Format texte simple, une entrée par ligne, lisible et stable d'une version à l'autre.
 */
class Memoire(context: Context) {
    private val prefs = context.getSharedPreferences("carnet", Context.MODE_PRIVATE)

    /** Il n'y a plus de réserve : ce qui y était noté par une version précédente est oublié. */
    fun oublierReserve() {
        if (prefs.contains(RESERVE)) prefs.edit().remove(RESERVE).apply()
    }

    /** Ce qui a été rapporté de raid pour chaque objectif : une ligne « clé de l'objectif|objet=quantité ». */
    fun lireApports(): Apports =
        lignes(APPORTS).mapNotNull { ligne ->
            val (cle, objet) = ligne.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
            val (id, quantite) = objet.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            quantite.toIntOrNull()?.takeIf { it > 0 }?.let { Triple(cle, id, it) }
        }.groupBy({ it.first }, { it.second to it.third }).mapValues { it.value.toMap() }

    fun ecrireApports(apports: Apports) {
        val lignes = apports.flatMap { (cle, objets) -> objets.map { (id, n) -> "$cle|$id=$n" } }
        prefs.edit().putString(APPORTS, lignes.joinToString("\n")).apply()
    }

    /** Objets ajoutés à la main dans l'onglet Raid (identifiant → quantité voulue), dans l'ordre où ils ont été ajoutés. */
    fun lireRaid(): Map<String, Int> =
        lignes(RAID).mapNotNull { ligne ->
            val (id, quantite) = ligne.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            quantite.toIntOrNull()?.takeIf { it > 0 }?.let { id to it }
        }.toMap()

    fun ecrireRaid(liste: Map<String, Int>) {
        prefs.edit().putString(RAID, liste.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    /** Pour chaque objet ajouté à la main dans l'onglet Raid : combien le joueur en a rapporté depuis. */
    fun lireTrouvesRaid(): Map<String, Int> =
        lignes(TROUVES_RAID).mapNotNull { ligne ->
            val (id, quantite) = ligne.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            quantite.toIntOrNull()?.takeIf { it > 0 }?.let { id to it }
        }.toMap()

    fun ecrireTrouvesRaid(trouves: Map<String, Int>) {
        prefs.edit().putString(TROUVES_RAID, trouves.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    fun lireObjectifs(): List<Objectif> =
        prefs.getString(OBJECTIFS, "").orEmpty().lines().mapNotNull(::decoder)

    fun ecrireObjectifs(objectifs: List<Objectif>) {
        prefs.edit().putString(OBJECTIFS, objectifs.joinToString("\n") { it.cle }).apply()
    }

    fun lireProgression(): Progression = Progression(
        niveauxEtablis = lignes(NIVEAUX).mapNotNull { ligne ->
            val (id, niveau) = ligne.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            niveau.toIntOrNull()?.let { id to it }
        }.toMap(),
        quetesTerminees = lignes(QUETES_TERMINEES).toSet(),
        etablisSuivis = lignes(ETABLIS_SUIVIS).toSet(),
        etapesProjets = lignes(ETAPES_PROJETS).mapNotNull { ligne ->
            val (id, n) = ligne.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            n.toIntOrNull()?.let { id to it }
        }.toMap(),
        projetsSuivis = lignes(PROJETS_SUIVIS).toSet(),
        quetesIndiquees = prefs.getBoolean(QUETES_INDIQUEES, false),
    )

    fun ecrireProgression(p: Progression) {
        prefs.edit()
            .putString(NIVEAUX, p.niveauxEtablis.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString(QUETES_TERMINEES, p.quetesTerminees.sorted().joinToString("\n"))
            .putString(ETABLIS_SUIVIS, p.etablisSuivis.sorted().joinToString("\n"))
            // Ancien format (tous les établis suivis sauf ceux-ci) : abandonné, rien n'est suivi par défaut.
            .remove(ETABLIS_IGNORES)
            .putString(ETAPES_PROJETS, p.etapesProjets.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString(PROJETS_SUIVIS, p.projetsSuivis.sorted().joinToString("\n"))
            .putBoolean(QUETES_INDIQUEES, p.quetesIndiquees)
            .apply()
    }

    var region: Region
        get() = Region.entries.firstOrNull { it.name == prefs.getString(REGION, null) } ?: Region.EUROPE
        set(valeur) = prefs.edit().putString(REGION, valeur.name).apply()

    private fun lignes(cle: String): List<String> = prefs.getString(cle, "").orEmpty().lines().filter { it.isNotBlank() }

    private fun decoder(ligne: String): Objectif? {
        val parties = ligne.split(':')
        return when {
            parties.size == 3 && parties[0] == "etabli" ->
                parties[2].toIntOrNull()?.let { Objectif.NiveauDEtabli(parties[1], it) }
            parties.size == 2 && parties[0] == "quete" -> Objectif.QueteATerminer(parties[1])
            parties.size == 3 && parties[0] == "projet" ->
                parties[2].toIntOrNull()?.let { Objectif.EtapeDeProjet(parties[1], it) }
            else -> null
        }
    }

    private companion object {
        const val RESERVE = "reserve"
        const val APPORTS = "apports"
        const val RAID = "raid"
        const val TROUVES_RAID = "raidTrouves"
        const val OBJECTIFS = "objectifs"
        const val NIVEAUX = "niveauxEtablis"
        const val QUETES_TERMINEES = "quetesTerminees"
        const val ETABLIS_SUIVIS = "etablisSuivis"
        const val ETABLIS_IGNORES = "etablisIgnores"
        const val ETAPES_PROJETS = "etapesProjets"
        const val PROJETS_SUIVIS = "projetsSuivis"
        const val QUETES_INDIQUEES = "quetesIndiquees"
        const val REGION = "region"
    }
}
