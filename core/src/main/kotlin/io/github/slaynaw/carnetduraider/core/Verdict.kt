package io.github.slaynaw.carnetduraider.core

/**
 * Que faire d'un objet trouvé : la réponse à « je garde, je recycle ou je vends ? ».
 *
 * Un objet n'est utile que s'il sert à un objectif en cours (et qu'il en manque encore), ou à un build du
 * joueur (dans le build, ou pour le fabriquer). Tout le reste est à vendre ou à recycler, selon ce qui
 * rapporte le plus ; l'appli dit quand même à quoi il peut servir ([Usages]).
 */
sealed interface Verdict {
    /** Les objectifs en cours en demandent encore [manquant] (sur [besoin]) ; il sert peut-être aussi à des [builds]. */
    data class Garder(
        val manquant: Int,
        val besoin: Int,
        val pour: List<Besoin>,
        val builds: List<UsageBuild> = emptyList(),
    ) : Verdict

    /** Il sert à un build du joueur. */
    data class PourBuild(val builds: List<UsageBuild>) : Verdict

    /** Aucun objectif en cours ni aucun build n'en a besoin : à vendre ou à recycler. */
    data class Inutile(val conseil: Sortie) : Verdict
}

/** Meilleure façon de se séparer d'un objet, selon ce que rapporte chaque option en pièces. */
enum class Sortie { VENDRE, RECYCLER }

class Conseiller(private val catalogue: Catalogue, private val planificateur: Planificateur) {

    /**
     * [objectifs] : objectifs en cours ; [apports] : ce qui a déjà été rapporté pour eux ; [builds] : objets à
     * garder pour les builds ([Builds.aGarder]).
     */
    fun verdict(
        objetId: String,
        objectifs: List<Objectif>,
        apports: Apports = emptyMap(),
        builds: Map<String, List<UsageBuild>> = emptyMap(),
    ): Verdict {
        val pour = planificateur.besoinsPourObjet(objetId, objectifs, apports)
        val manque = pour.sumOf { it.manque }
        val pourBuilds = builds[objetId].orEmpty()
        return when {
            manque > 0 -> Verdict.Garder(manque, pour.sumOf { it.quantite }, pour, pourBuilds)
            pourBuilds.isNotEmpty() -> Verdict.PourBuild(pourBuilds)
            else -> Verdict.Inutile(meilleureSortie(objetId))
        }
    }

    /**
     * Recycler quand les matériaux obtenus valent plus que l'objet lui-même, vendre sinon.
     * Un objet qui ne se recycle pas se vend.
     */
    fun meilleureSortie(objetId: String): Sortie {
        val objet = catalogue.objet(objetId) ?: return Sortie.VENDRE
        if (objet.recyclage.isEmpty()) return Sortie.VENDRE
        return if (catalogue.valeurRecyclage(objet) > objet.valeur) Sortie.RECYCLER else Sortie.VENDRE
    }
}
