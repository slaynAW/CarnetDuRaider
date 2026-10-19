package io.github.slaynaw.carnetduraider.core

/**
 * Que faire d'un objet trouvé : la réponse à « je garde, je recycle ou je vends ? ».
 *
 * Un objet que le joueur veut (pour ses objectifs, ou ajouté à sa liste de raid) n'est jamais proposé à
 * la vente. Un objet qui sert à quelque chose (fabrication, établis, quêtes, clé, équipement…) non plus :
 * l'appli dit à quoi il sert, et ce qu'il rapporterait si le joueur n'en a pas l'usage. Seul un objet qui ne
 * sert à rien d'autre est à vendre ou à recycler.
 */
sealed interface Verdict {
    /** Il en manque encore pour les objectifs suivis. */
    data class Garder(val manquant: Int, val besoin: Int, val pour: List<Besoin>) : Verdict

    /** Le joueur l'a ajouté à sa liste de raid : il en veut [voulu], il en a rapporté [trouves]. */
    data class Cherche(val voulu: Int, val trouves: Int) : Verdict

    /** Les objectifs sont couverts ; [surplus] en plus de ce qu'il faut. */
    data class Suffisant(val surplus: Int, val besoin: Int, val pour: List<Besoin>) : Verdict

    /** Aucun objectif ne le demande, mais il sert à autre chose ([usages]). */
    data class Utile(val usages: UsagesObjet, val conseil: Sortie) : Verdict

    /** Il ne sert à rien d'autre : à vendre ou à recycler. */
    data class Inutile(val conseil: Sortie) : Verdict
}

/** Meilleure façon de se séparer d'un objet, selon ce que rapporte chaque option en pièces. */
enum class Sortie { VENDRE, RECYCLER }

class Conseiller(private val catalogue: Catalogue, private val planificateur: Planificateur, private val usages: Usages) {

    fun verdict(
        objetId: String,
        possede: Int,
        objectifs: List<Objectif>,
        progression: Progression = Progression(),
        listeRaid: Map<String, Int> = emptyMap(),
        trouvesRaid: Map<String, Int> = emptyMap(),
    ): Verdict {
        val pour = planificateur.besoinsPourObjet(objetId, objectifs)
        val besoin = pour.sumOf { it.quantite }
        val voulu = listeRaid[objetId]
        return when {
            besoin > possede -> Verdict.Garder(besoin - possede, besoin, pour)
            voulu != null -> Verdict.Cherche(voulu, trouvesRaid[objetId] ?: 0)
            besoin > 0 -> Verdict.Suffisant(possede - besoin, besoin, pour)
            else -> {
                val u = usages.pour(objetId, progression)
                if (u.aucun) Verdict.Inutile(meilleureSortie(objetId)) else Verdict.Utile(u, meilleureSortie(objetId))
            }
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
