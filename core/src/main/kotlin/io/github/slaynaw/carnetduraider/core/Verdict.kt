package io.github.slaynaw.carnetduraider.core

/** Que faire d'un objet trouvé : la réponse à « je garde, je recycle ou je vends ? ». */
sealed interface Verdict {
    /** Il en manque encore pour les objectifs suivis. */
    data class Garder(val manquant: Int, val besoin: Int, val pour: List<Besoin>) : Verdict

    /** Les objectifs sont couverts ; [surplus] unités peuvent partir (0 : pile ce qu'il faut). */
    data class Suffisant(val surplus: Int, val besoin: Int, val pour: List<Besoin>, val conseil: Sortie) : Verdict

    /** Aucun objectif suivi n'en demande. */
    data class Inutile(val conseil: Sortie) : Verdict
}

/** Meilleure façon de se séparer d'un objet, selon ce que rapporte chaque option en pièces. */
enum class Sortie { VENDRE, RECYCLER }

class Conseiller(private val catalogue: Catalogue, private val planificateur: Planificateur) {

    fun verdict(objetId: String, possede: Int, objectifs: List<Objectif>): Verdict {
        val pour = planificateur.besoinsPourObjet(objetId, objectifs)
        val besoin = pour.sumOf { it.quantite }
        val sortie = meilleureSortie(objetId)
        return when {
            besoin == 0 -> Verdict.Inutile(sortie)
            possede < besoin -> Verdict.Garder(besoin - possede, besoin, pour)
            else -> Verdict.Suffisant(possede - besoin, besoin, pour, sortie)
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
