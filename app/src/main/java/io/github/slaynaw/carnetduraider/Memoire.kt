package io.github.slaynaw.carnetduraider

import android.content.Context
import io.github.slaynaw.carnetduraider.core.Apports
import io.github.slaynaw.carnetduraider.core.Build
import io.github.slaynaw.carnetduraider.core.Builds
import io.github.slaynaw.carnetduraider.core.ButinRecherche
import io.github.slaynaw.carnetduraider.core.Progression
import io.github.slaynaw.carnetduraider.core.calendrier.Region

/**
 * Ce que le joueur a saisi (ce qu'il a en cours, ce qu'il a rapporté de raid pour ses objectifs, son butin
 * recherché, ses builds),
 * gardé sur le téléphone uniquement.
 * Format texte simple, une entrée par ligne, lisible et stable d'une version à l'autre.
 */
class Memoire(context: Context) {
    private val prefs = context.getSharedPreferences("carnet", Context.MODE_PRIVATE)

    /**
     * Version 0.10 : tout est fait d'office sauf ce que le joueur indique en cours. Ce qui avait été saisi
     * avec les versions précédentes (établis suivis, objectifs ajoutés à la main, réserve, liste de raid)
     * ne veut plus dire la même chose : les objectifs repartent de zéro, une seule fois. Les réglages restent.
     */
    fun nouveauDepart() {
        if (prefs.getInt(MODELE, 0) >= MODELE_ACTUEL) return
        val editeur = prefs.edit()
        listOf(RESERVE, APPORTS, RAID, TROUVES_RAID, OBJECTIFS, NIVEAUX, QUETES_TERMINEES, ETABLIS_SUIVIS, ETABLIS_IGNORES,
            ETAPES_PROJETS, PROJETS_SUIVIS, QUETES_INDIQUEES).forEach { editeur.remove(it) }
        editeur.putInt(MODELE, MODELE_ACTUEL).apply()
    }

    /** Les builds du joueur (section « Mes builds »). */
    fun lireBuilds(): List<Build> = Builds.decoder(prefs.getString(BUILDS, null))

    fun ecrireBuilds(builds: List<Build>) {
        prefs.edit().putString(BUILDS, Builds.encoder(builds)).apply()
    }

    /** Le « Butin recherché » : objets à rapporter sans objectif du jeu (une ligne « objet=quantité »). */
    fun lireButinRecherche(): Map<String, Int> = ButinRecherche.decoder(prefs.getString(BUTIN_RECHERCHE, null))

    fun ecrireButinRecherche(liste: Map<String, Int>) {
        prefs.edit().putString(BUTIN_RECHERCHE, ButinRecherche.encoder(liste)).apply()
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
            .putString(ETAPES_PROJETS, p.etapesProjets.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString(PROJETS_SUIVIS, p.projetsSuivis.sorted().joinToString("\n"))
            .putBoolean(QUETES_INDIQUEES, p.quetesIndiquees)
            .apply()
    }

    var region: Region
        get() = Region.entries.firstOrNull { it.name == prefs.getString(REGION, null) } ?: Region.EUROPE
        set(valeur) = prefs.edit().putString(REGION, valeur.name).apply()

    private fun lignes(cle: String): List<String> = prefs.getString(cle, "").orEmpty().lines().filter { it.isNotBlank() }

    private companion object {
        const val MODELE = "modele"
        const val MODELE_ACTUEL = 2
        const val BUILDS = "builds"
        const val BUTIN_RECHERCHE = "butinRecherche"

        // Clés des versions précédentes, effacées par nouveauDepart().
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
