package io.github.slaynaw.carnetduraider.core

/** Ce qu'une recherche trouve sur le plan d'une carte. */
data class ResultatPlan(val zones: List<ZonePlan>, val sorties: List<SortiePlan>, val arcs: List<ArcPlan>) {
    val vide: Boolean get() = zones.isEmpty() && sorties.isEmpty() && arcs.isEmpty()
}

/**
 * Recherche sur le plan d'une carte, sans tenir compte des accents, des articles ni du pluriel :
 * « les trappes », « bondisseurs », « trappe gare », « ascenseurs », « arc »…
 *
 * Chaque mot cherché doit commencer un mot du nom officiel, ou désigner la famille de l'élément
 * (« sortie », « trappe », « ascenseur », « métro », « conduit », « téléphérique », « arc », « zone »).
 */
object RecherchePlan {
    private val motsVides = setOf("le", "la", "les", "l", "de", "des", "du", "d", "un", "une", "a", "au", "aux", "et", "ou", "en", "sur")

    private val familleSorties = setOf("sortie", "extraction", "evacuation")
    private val famillesType = mapOf(
        TypeSortie.TRAPPE to setOf("trappe"),
        TypeSortie.ASCENSEUR to setOf("ascenseur"),
        TypeSortie.METRO to setOf("metro", "station"),
        TypeSortie.CONDUIT to setOf("conduit", "aeration"),
        TypeSortie.TELEPHERIQUE to setOf("telepherique", "gondole", "terminal"),
    )
    private val familleArc = setOf("arc", "ennemi", "robot")
    private val familleZones = setOf("zone", "lieu")

    /** `null` si la recherche est vide (rien à filtrer : le plan s'affiche normalement). */
    fun chercher(plan: Plan, requete: String): ResultatPlan? {
        val mots = mots(requete)
        if (mots.isEmpty()) return null
        return ResultatPlan(
            zones = plan.zones.filter { correspond(mots, it.nom, familleZones) },
            sorties = plan.sorties.filter { correspond(mots, it.nom, familleSorties + famillesType.getValue(it.type)) },
            arcs = plan.arcs.filter { correspond(mots, it.nom, familleArc) },
        )
    }

    private fun correspond(cherches: List<String>, nom: String?, famille: Set<String>): Boolean {
        val motsNom = nom?.let(::mots).orEmpty()
        return cherches.all { m -> m in famille || famille.any { it.startsWith(m) && m.length >= 3 } || motsNom.any { it.startsWith(m) } }
    }

    /** Mots normalisés, sans articles, au singulier. */
    internal fun mots(texte: String): List<String> =
        Catalogue.normaliser(texte).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() && it !in motsVides }
            .map { if (it.length > 3 && (it.endsWith('s') || it.endsWith('x'))) it.dropLast(1) else it }
}
