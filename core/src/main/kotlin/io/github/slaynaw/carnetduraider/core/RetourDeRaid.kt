package io.github.slaynaw.carnetduraider.core

/**
 * Bilan d'un retour de raid : les objets de la liste qui ont atteint le nombre demandé ([atteints], avec
 * leur nouveau compte), les objectifs dont tout a été rapporté, terminés d'eux-mêmes ([objectifsTermines]),
 * et [termine] quand toute la liste est trouvée.
 */
data class BilanRaid(val atteints: List<ObjetATrouver>, val objectifsTermines: List<Objectif>, val termine: Boolean) {
    /** Rien à montrer : aucun objet n'a atteint son nombre, aucun objectif n'est terminé. */
    val vide: Boolean get() = atteints.isEmpty() && objectifsTermines.isEmpty()
}

/**
 * Le retour de raid est le seul endroit où le joueur note des objets : le butin va aux objectifs qui le
 * demandent ([Planificateur.repartir]) et se décompte de la liste du raid.
 */
object RetourDeRaid {

    /**
     * Compare la liste du raid à l'ouverture du retour ([avant]) et après la saisie du butin ([apres], avant
     * que les objectifs complets ne soient terminés) ; [termines] : les objectifs terminés ensuite.
     */
    fun bilan(avant: List<ObjetATrouver>, apres: List<ObjetATrouver>, termines: List<Objectif>): BilanRaid {
        val maintenant = apres.associateBy { it.objetId }
        val atteints = avant.filter { !it.trouve }.mapNotNull { a -> (maintenant[a.objetId] ?: a.copy(trouves = a.voulu)).takeIf { it.trouve } }
        return BilanRaid(atteints, termines, termine = avant.any { !it.trouve } && apres.all { it.trouve })
    }

    /** Ce qui a été rapporté pour des objectifs terminés n'a plus à être gardé. */
    fun sansLesTermines(apports: Apports, termines: List<Objectif>): Apports = apports - termines.map { it.cle }.toSet()
}
