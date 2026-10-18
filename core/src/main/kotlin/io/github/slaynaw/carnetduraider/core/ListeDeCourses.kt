package io.github.slaynaw.carnetduraider.core

/** Ce que le joueur veut atteindre : un niveau d'établi ou une quête. */
sealed interface Objectif {
    data class NiveauDEtabli(val etabliId: String, val niveau: Int) : Objectif
    data class QueteATerminer(val queteId: String) : Objectif

    /** [etape] : rang de l'étape dans le projet, à partir de 0. */
    data class EtapeDeProjet(val projetId: String, val etape: Int) : Objectif
}

/** Une ligne de la liste de courses : un objet, ce qu'il faut, ce qu'on a, et pour quels objectifs. */
data class LigneDeCourses(
    val objetId: String,
    val nom: String,
    val besoin: Int,
    val possede: Int,
    val pour: List<Besoin>,
) {
    val manquant: Int get() = (besoin - possede).coerceAtLeast(0)
    val complet: Boolean get() = possede >= besoin
}

/** Une raison de garder un objet : tel objectif en demande tant. */
data class Besoin(val objectif: Objectif, val libelle: String, val quantite: Int)

data class ListeDeCourses(val lignes: List<LigneDeCourses>) {
    val objetsManquants: Int get() = lignes.sumOf { it.manquant }

    /** Part des objets déjà rassemblés, de 0 à 1 (chaque unité compte pareil). */
    val progression: Double
        get() {
            val total = lignes.sumOf { it.besoin }
            return if (total == 0) 1.0 else lignes.sumOf { minOf(it.possede, it.besoin) }.toDouble() / total
        }
}

/** Calcule les besoins en objets des objectifs suivis, d'après les données du jeu. */
class Planificateur(private val catalogue: Catalogue) {

    /** Libellé d'un objectif, avec les noms officiels du jeu (« Station utilitaire · niveau 1 »). */
    fun libelle(objectif: Objectif): String = when (objectif) {
        is Objectif.NiveauDEtabli ->
            "${catalogue.etabli(objectif.etabliId)?.nom ?: Catalogue.NOM_A_VERIFIER} · niveau ${objectif.niveau}"
        is Objectif.QueteATerminer ->
            "Quête «\u00a0${catalogue.quete(objectif.queteId)?.nom ?: Catalogue.NOM_A_VERIFIER}\u00a0»"
        is Objectif.EtapeDeProjet -> catalogue.projet(objectif.projetId).let { projet ->
            "${projet?.nom ?: Catalogue.NOM_A_VERIFIER} · ${nomEtape(projet, objectif.etape)}"
        }
    }

    /** Nom officiel de l'étape, ou « étape 2 » (rang dans le projet) s'il n'est pas vérifié. */
    fun nomEtape(projet: Projet?, etape: Int): String = projet?.etapes?.getOrNull(etape)?.nom ?: "étape ${etape + 1}"

    fun besoins(objectif: Objectif): List<Quantite> = when (objectif) {
        is Objectif.NiveauDEtabli -> catalogue.etabli(objectif.etabliId)
            ?.niveaux?.firstOrNull { it.niveau == objectif.niveau }?.cout.orEmpty()
        is Objectif.QueteATerminer -> catalogue.quete(objectif.queteId)?.objets.orEmpty()
        is Objectif.EtapeDeProjet -> catalogue.projet(objectif.projetId)?.etapes?.getOrNull(objectif.etape)?.cout.orEmpty()
    }

    /** Pourquoi garder cet objet : chaque objectif qui en demande, avec la quantité. */
    fun besoinsPourObjet(objetId: String, objectifs: List<Objectif>): List<Besoin> =
        objectifs.mapNotNull { objectif ->
            val q = besoins(objectif).filter { it.id == objetId }.sumOf { it.quantite }
            if (q > 0) Besoin(objectif, libelle(objectif), q) else null
        }

    /**
     * Liste de courses : tous les objets demandés par les objectifs, regroupés par objet, les plus
     * en retard d'abord. [reserve] donne ce que le joueur possède déjà (identifiant → quantité).
     */
    fun listeDeCourses(objectifs: List<Objectif>, reserve: Map<String, Int>): ListeDeCourses {
        val ids = objectifs.flatMap { o -> besoins(o).map { it.id } }.distinct()
        val lignes = ids.map { id ->
            val pour = besoinsPourObjet(id, objectifs)
            LigneDeCourses(
                objetId = id,
                nom = catalogue.nomObjet(id),
                besoin = pour.sumOf { it.quantite },
                possede = reserve[id] ?: 0,
                pour = pour,
            )
        }
        return ListeDeCourses(lignes.sortedWith(compareBy({ it.complet }, { -it.manquant }, { it.nom })))
    }
}
