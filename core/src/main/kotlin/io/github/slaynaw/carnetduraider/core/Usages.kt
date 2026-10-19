package io.github.slaynaw.carnetduraider.core

/** Un niveau d'établi qui demande l'objet. */
data class UsageAmelioration(val etabli: Etabli, val niveau: Int, val quantite: Int)

/** Une quête qui demande l'objet. */
data class UsageQuete(val quete: Quete, val quantite: Int)

/** Une étape de projet (rang [etape], à partir de 0) qui demande l'objet. */
data class UsageProjet(val projet: Projet, val etape: Int, val quantite: Int)

/**
 * Tout ce à quoi un objet peut encore servir : ce que disent les fichiers du jeu ([utilisations]),
 * plus les améliorations d'établis, quêtes et étapes de projets pas encore faites qui le demandent.
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

/** Calcule à quoi sert un objet, compte tenu de la progression du joueur (ce qui est fait ne compte plus). */
class Usages(private val catalogue: Catalogue, private val suivi: SuiviProgression) {

    fun pour(objetId: String, progression: Progression = Progression()): UsagesObjet {
        val objet = catalogue.objet(objetId)
        val donnees = catalogue.donnees
        val ameliorations = donnees.etablis.flatMap { e ->
            val actuel = suivi.niveau(progression, e)
            e.niveaux.filter { it.niveau > actuel }.mapNotNull { n ->
                n.cout.filter { it.id == objetId }.sumOf { it.quantite }.takeIf { it > 0 }?.let { UsageAmelioration(e, n.niveau, it) }
            }
        }
        val quetes = donnees.quetes.filter { it.id !in progression.quetesTerminees }.mapNotNull { q ->
            q.objets.filter { it.id == objetId }.sumOf { it.quantite }.takeIf { it > 0 }?.let { UsageQuete(q, it) }
        }
        val projets = donnees.projets.flatMap { p ->
            val faites = progression.etapesProjets[p.id] ?: 0
            p.etapes.withIndex().drop(faites).mapNotNull { (i, etape) ->
                etape.cout.filter { it.id == objetId }.sumOf { it.quantite }.takeIf { it > 0 }?.let { UsageProjet(p, i, it) }
            }
        }
        return UsagesObjet(objet?.description, objet?.utilisations ?: Utilisations(), ameliorations, quetes, projets)
    }
}
