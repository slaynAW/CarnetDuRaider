package io.github.slaynaw.carnetduraider.core

/**
 * Bilan d'un retour de raid : les objets de la liste à trouver qui ont atteint le nombre demandé pendant
 * ce retour ([atteints], avec leur nouveau compte), et [termine] quand toute la liste est trouvée.
 */
data class BilanRaid(val atteints: List<ObjetATrouver>, val termine: Boolean)

/**
 * Le retour de raid décompte ce que le joueur cherchait : la réserve pour les objets des objectifs, et,
 * pour les objets qu'il a ajoutés lui-même, ce qu'il en a rapporté depuis.
 */
object RetourDeRaid {

    /**
     * Compte [ecart] objets rapportés (ou retirés, s'il est négatif) pour un objet que le joueur cherche
     * ([raid] : identifiant → quantité voulue). Renvoie les nouveaux comptes de [trouves].
     */
    fun compter(raid: Map<String, Int>, trouves: Map<String, Int>, objetId: String, ecart: Int): Map<String, Int> {
        if (objetId !in raid || ecart == 0) return trouves
        val n = ((trouves[objetId] ?: 0) + ecart).coerceAtLeast(0)
        return if (n == 0) trouves - objetId else trouves + (objetId to n)
    }

    /**
     * Compare la liste à trouver à l'ouverture du retour de raid ([avant]) et après la saisie du butin
     * ([apres]). Un objet demandé par les objectifs quitte la liste quand il est complet : il compte comme
     * atteint, avec ce qu'il y a maintenant dans la [reserve].
     */
    fun bilan(avant: List<ObjetATrouver>, apres: List<ObjetATrouver>, reserve: Map<String, Int>): BilanRaid {
        val maintenant = apres.associateBy { it.objetId }
        val atteints = avant.filter { !it.trouve }.mapNotNull { a ->
            val etat = maintenant[a.objetId] ?: a.copy(trouves = maxOf(reserve[a.objetId] ?: 0, a.voulu))
            etat.takeIf { it.trouve }
        }
        return BilanRaid(atteints, termine = avant.any { !it.trouve } && apres.all { it.trouve })
    }

    /** Les objets ajoutés à la main et trouvés quittent la liste : renvoie la liste et les comptes qui restent. */
    fun sansLesTrouves(raid: Map<String, Int>, trouves: Map<String, Int>): Pair<Map<String, Int>, Map<String, Int>> {
        val restants = raid.filter { (id, voulu) -> (trouves[id] ?: 0) < voulu }
        return restants to trouves.filterKeys { it in restants }
    }
}
