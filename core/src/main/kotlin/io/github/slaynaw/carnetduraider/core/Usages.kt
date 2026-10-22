package io.github.slaynaw.carnetduraider.core

/** Un niveau d'établi qui demande l'objet. */
data class UsageAmelioration(val etabli: Etabli, val niveau: Int, val quantite: Int)

/** Une quête qui demande l'objet. */
data class UsageQuete(val quete: Quete, val quantite: Int)

/** Une étape de projet (rang [etape], à partir de 0) qui demande l'objet. */
data class UsageProjet(val projet: Projet, val etape: Int, val quantite: Int)

/**
 * Tout ce à quoi un objet peut servir dans le jeu : ce que disent les fichiers du jeu ([utilisations]), plus
 * les améliorations d'établis, quêtes et étapes de projets qui le demandent. C'est une explication : seuls
 * les objectifs en cours et les builds décident s'il faut le garder ([Conseiller]).
 */
data class UsagesObjet(
    val description: String?,
    val utilisations: Utilisations,
    val ameliorations: List<UsageAmelioration>,
    val quetes: List<UsageQuete>,
    val projets: List<UsageProjet>,
) {
    /** Vrai s'il ne sert à rien d'autre qu'à être vendu ou recyclé. */
    val aucun: Boolean
        get() = utilisations == Utilisations() && ameliorations.isEmpty() && quetes.isEmpty() && projets.isEmpty()

    /** Résumé court, du plus utile au moins utile : « fabrication, établis, quêtes ». */
    fun resume(): List<String> = buildList {
        val u = utilisations
        if (u.cle) add("clé")
        if (u.equipement) add("équipement")
        if (ameliorations.isNotEmpty()) add("établis")
        if (quetes.isNotEmpty()) add("quêtes")
        if (projets.isNotEmpty()) add("projets")
        if (u.fabrication.isNotEmpty()) add("fabrication")
        if (u.amplification.isNotEmpty()) add("amplification")
        if (u.aApprendre) add("à apprendre")
        if (u.apparence) add("apparence")
        if (u.recherches.isNotEmpty()) add("recherche")
        if (u.avantPoste.isNotEmpty()) add("Avant-poste")
        if (u.echanges.isNotEmpty() || u.echangesValeur.isNotEmpty()) add("échanges")
    }
}

/** Calcule à quoi peut servir un objet, d'après les données du jeu. */
class Usages(private val catalogue: Catalogue) {

    fun pour(objetId: String): UsagesObjet {
        val objet = catalogue.objet(objetId)
        val donnees = catalogue.donnees
        val ameliorations = donnees.etablis.flatMap { e ->
            e.niveaux.mapNotNull { n ->
                n.cout.filter { it.id == objetId }.sumOf { it.quantite }.takeIf { it > 0 }?.let { UsageAmelioration(e, n.niveau, it) }
            }
        }
        val quetes = donnees.quetes.mapNotNull { q ->
            q.objets.filter { it.id == objetId }.sumOf { it.quantite }.takeIf { it > 0 }?.let { UsageQuete(q, it) }
        }
        val projets = donnees.projets.flatMap { p ->
            p.etapes.withIndex().mapNotNull { (i, etape) ->
                etape.cout.filter { it.id == objetId }.sumOf { it.quantite }.takeIf { it > 0 }?.let { UsageProjet(p, i, it) }
            }
        }
        return UsagesObjet(objet?.description, objet?.utilisations ?: Utilisations(), ameliorations, quetes, projets)
    }
}
