package io.github.slaynaw.carnetduraider.core

/**
 * « Butin recherché » : des objets que le joueur veut rapporter pour lui-même, sans objectif du jeu derrière
 * (objet → quantité voulue). Ils s'ajoutent à la liste du raid ([Objectif.ObjetRecherche]) : le butin noté
 * au retour de raid les remplit après les objectifs du jeu ([Planificateur.repartir]), et un objet dont tout
 * a été rapporté quitte la liste ([terminerLesComplets]). Tant qu'il en manque, l'objet est à garder.
 */
object ButinRecherche {
    /** Titre de la section, et raison affichée sous les objets qu'elle demande. */
    const val TITRE = "Butin recherché"

    /** Quantité voulue au plus pour un objet. */
    const val MAXIMUM = 999

    /** Les objets recherchés, comme objectifs de la liste du raid. */
    fun objectifs(liste: Map<String, Int>): List<Objectif.ObjetRecherche> =
        liste.filterValues { it > 0 }.map { (id, n) -> Objectif.ObjetRecherche(id, n) }

    /** Ajoute [quantite] de l'objet à ce qui est déjà recherché. */
    fun ajouter(liste: Map<String, Int>, objetId: String, quantite: Int = 1): Map<String, Int> =
        changer(liste, objetId, (liste[objetId] ?: 0) + quantite)

    /** Nouvelle quantité voulue pour l'objet ; 0 ou moins le retire de la liste. */
    fun changer(liste: Map<String, Int>, objetId: String, quantite: Int): Map<String, Int> =
        if (quantite <= 0) liste - objetId else liste + (objetId to quantite.coerceAtMost(MAXIMUM))

    /** Ce qui a déjà été rapporté pour cet objet recherché. */
    fun rapportes(apports: Apports, objetId: String): Int = apports[cle(objetId)]?.get(objetId) ?: 0

    /** Clé de l'objet recherché dans les apports, la même que [Objectif.cle]. */
    fun cle(objetId: String): String = Objectif.ObjetRecherche(objetId, 0).cle

    /**
     * Retire de la liste les objets dont tout a été rapporté, et ce qui avait été rapporté pour eux.
     * Renvoie la liste, les apports et les objets terminés.
     */
    fun terminerLesComplets(liste: Map<String, Int>, apports: Apports): Triple<Map<String, Int>, Apports, List<String>> {
        val termines = liste.filter { (id, n) -> rapportes(apports, id) >= n }.keys.toList()
        if (termines.isEmpty()) return Triple(liste, apports, termines)
        return Triple(liste - termines.toSet(), apports - termines.map(::cle).toSet(), termines)
    }

    /** Une ligne « objet=quantité » par objet recherché. */
    fun encoder(liste: Map<String, Int>): String = liste.entries.joinToString("\n") { "${it.key}=${it.value}" }

    /** Relit la liste enregistrée ; une ligne illisible est ignorée. */
    fun decoder(texte: String?): Map<String, Int> =
        texte.orEmpty().lines().mapNotNull { ligne ->
            val (id, n) = ligne.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            n.toIntOrNull()?.takeIf { it > 0 && id.isNotBlank() }?.let { id to it.coerceAtMost(MAXIMUM) }
        }.toMap()
}
