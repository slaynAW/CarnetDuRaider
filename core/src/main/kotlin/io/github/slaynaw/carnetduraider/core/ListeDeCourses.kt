package io.github.slaynaw.carnetduraider.core

/**
 * Ce que le joueur veut atteindre : un niveau d'établi, une quête ou une étape de projet, ou un objet de son
 * « Butin recherché » ([ObjetRecherche], sans objectif du jeu derrière).
 */
sealed interface Objectif {
    data class NiveauDEtabli(val etabliId: String, val niveau: Int) : Objectif
    data class QueteATerminer(val queteId: String) : Objectif

    /** [etape] : rang de l'étape dans le projet, à partir de 0. */
    data class EtapeDeProjet(val projetId: String, val etape: Int) : Objectif

    /** Un objet du « Butin recherché » : [quantite] à rapporter, pour le joueur lui-même ([ButinRecherche]). */
    data class ObjetRecherche(val objetId: String, val quantite: Int) : Objectif
}

/**
 * Clé stable d'un objectif (« etabli:utility_bench:1 », « quete:clearer_skies », « projet:…:0 »,
 * « butin:arc_alloy » : la quantité voulue n'en fait pas partie, ce qui a été rapporté la suit).
 */
val Objectif.cle: String
    get() = when (this) {
        is Objectif.NiveauDEtabli -> "etabli:$etabliId:$niveau"
        is Objectif.QueteATerminer -> "quete:$queteId"
        is Objectif.EtapeDeProjet -> "projet:$projetId:$etape"
        is Objectif.ObjetRecherche -> "butin:$objetId"
    }

/**
 * Ce que le joueur a rapporté de raid pour ses objectifs : clé de l'objectif ([cle]) → objets rapportés
 * (identifiant → quantité). Il n'y a pas de réserve à tenir : seul compte le butin noté au retour de raid.
 */
typealias Apports = Map<String, Map<String, Int>>

/** Une raison de garder un objet : tel objectif en demande [quantite], dont [apporte] déjà rapportés. */
data class Besoin(val objectif: Objectif, val libelle: String, val quantite: Int, val apporte: Int = 0) {
    /** Combien il en manque encore pour cet objectif. */
    val manque: Int get() = (quantite - apporte).coerceAtLeast(0)
}

/** Une ligne de la liste de courses : un objet, les objectifs qui le demandent, et ce qui a été rapporté. */
data class LigneDeCourses(val objetId: String, val nom: String, val pour: List<Besoin>) {
    val besoin: Int get() = pour.sumOf { it.quantite }
    val apporte: Int get() = pour.sumOf { minOf(it.apporte, it.quantite) }
    val manquant: Int get() = pour.sumOf { it.manque }
    val complet: Boolean get() = manquant == 0
}

data class ListeDeCourses(val lignes: List<LigneDeCourses>) {
    val objetsManquants: Int get() = lignes.sumOf { it.manquant }

    /** Nombre d'objets différents qu'il reste à trouver. */
    val objetsATrouver: Int get() = lignes.count { !it.complet }

    /** Part des objets déjà rapportés, de 0 à 1 (chaque unité compte pareil). */
    val progression: Double
        get() {
            val total = lignes.sumOf { it.besoin }
            return if (total == 0) 1.0 else lignes.sumOf { it.apporte }.toDouble() / total
        }
}

/**
 * Un objet à trouver pendant les raids, et son décompte : ce que demandent les objectifs en cours ([pour]).
 * [voulu] : combien il en faut en tout ; [trouves] : combien ont déjà été rapportés.
 */
data class ObjetATrouver(val objetId: String, val trouves: Int, val voulu: Int, val pour: List<Besoin>) {
    /** Combien il en reste à trouver. */
    val reste: Int get() = (voulu - trouves).coerceAtLeast(0)
    val trouve: Boolean get() = trouves >= voulu
}

/** Calcule les besoins en objets des objectifs en cours, d'après les données du jeu. */
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
        is Objectif.ObjetRecherche -> ButinRecherche.TITRE
    }

    /** Nom officiel de l'étape, ou « étape 2 » (rang dans le projet) s'il n'est pas vérifié. */
    fun nomEtape(projet: Projet?, etape: Int): String = projet?.etapes?.getOrNull(etape)?.nom ?: "étape ${etape + 1}"

    fun besoins(objectif: Objectif): List<Quantite> = when (objectif) {
        is Objectif.NiveauDEtabli -> catalogue.etabli(objectif.etabliId)
            ?.niveaux?.firstOrNull { it.niveau == objectif.niveau }?.cout.orEmpty()
        is Objectif.QueteATerminer -> catalogue.quete(objectif.queteId)?.objets.orEmpty()
        is Objectif.EtapeDeProjet -> catalogue.projet(objectif.projetId)?.etapes?.getOrNull(objectif.etape)?.cout.orEmpty()
        is Objectif.ObjetRecherche -> listOf(Quantite(objectif.objetId, objectif.quantite))
    }

    /** Ce qui manque encore à l'objectif (identifiant → quantité), d'après ce qui a été rapporté. */
    fun manque(objectif: Objectif, apports: Apports): Map<String, Int> {
        val apporte = apports[objectif.cle].orEmpty()
        return besoins(objectif).groupBy { it.id }
            .mapValues { (id, quantites) -> quantites.sumOf { it.quantite } - (apporte[id] ?: 0) }
            .filterValues { it > 0 }
    }

    /** Vrai quand tout ce que demande l'objectif a été rapporté (un objectif sans objet ne l'est jamais). */
    fun complet(objectif: Objectif, apports: Apports): Boolean = besoins(objectif).isNotEmpty() && manque(objectif, apports).isEmpty()

    /** Pourquoi garder cet objet : chaque objectif qui en demande, avec la quantité et ce qui a été rapporté. */
    fun besoinsPourObjet(objetId: String, objectifs: List<Objectif>, apports: Apports = emptyMap()): List<Besoin> =
        objectifs.mapNotNull { objectif ->
            val q = besoins(objectif).filter { it.id == objetId }.sumOf { it.quantite }
            if (q > 0) Besoin(objectif, libelle(objectif), q, apports[objectif.cle]?.get(objetId) ?: 0) else null
        }

    /**
     * Liste de courses : tous les objets demandés par les objectifs, regroupés par objet, les plus
     * en retard d'abord, avec ce qui a déjà été rapporté pour chaque objectif ([apports]).
     */
    fun listeDeCourses(objectifs: List<Objectif>, apports: Apports): ListeDeCourses {
        val ids = objectifs.flatMap { o -> besoins(o).map { it.id } }.distinct()
        val lignes = ids.map { id -> LigneDeCourses(id, catalogue.nomObjet(id), besoinsPourObjet(id, objectifs, apports)) }
        return ListeDeCourses(lignes.sortedWith(compareBy({ it.complet }, { -it.manquant }, { it.nom })))
    }

    /** Toute la liste du raid, objets déjà trouvés compris : ce que demandent les objectifs, les plus en retard d'abord. */
    fun listeDuRaid(objectifs: List<Objectif>, apports: Apports): List<ObjetATrouver> =
        listeDeCourses(objectifs, apports).lignes.map { l -> ObjetATrouver(l.objetId, l.apporte, l.besoin, l.pour) }

    /** Ce qu'il reste à trouver : la liste du raid sans les objets déjà tous rapportés. */
    fun aTrouver(objectifs: List<Objectif>, apports: Apports): List<ObjetATrouver> =
        listeDuRaid(objectifs, apports).filter { !it.trouve }

    /**
     * Répartit le butin [saisis] (identifiant → quantité rapportée) entre les objectifs qui le demandent
     * encore : les objectifs du jeu d'abord, le plus près d'être terminé (le moins d'objets qui lui manquent)
     * en premier, puis le « Butin recherché » avec ce qui reste. Ce qui dépasse n'est gardé nulle part.
     */
    fun repartir(objectifs: List<Objectif>, avant: Apports, saisis: Map<String, Int>): Apports {
        val apports = avant.mapValues { it.value.toMutableMap() }.toMutableMap()
        // Ordre fixé avant la répartition : objectifs du jeu, le plus près d'être terminé d'abord, puis le
        // butin recherché ; à égalité, l'ordre de la liste.
        val ordre = objectifs.distinct()
            .map { o -> Triple(o.cle, manque(o, avant).toMutableMap(), o is Objectif.ObjetRecherche) }
            .sortedWith(compareBy({ it.third }, { it.second.values.sum() }))
            .map { (cle, manque) -> cle to manque }
        for ((id, quantite) in saisis) {
            var reste = quantite
            for ((cle, manque) in ordre) {
                if (reste <= 0) break
                val pris = minOf(reste, manque[id] ?: 0)
                if (pris <= 0) continue
                manque[id] = (manque[id] ?: 0) - pris
                reste -= pris
                val apporte = apports.getOrPut(cle) { mutableMapOf() }
                apporte[id] = (apporte[id] ?: 0) + pris
            }
        }
        return apports.mapValues { it.value.toMap() }.filterValues { it.isNotEmpty() }
    }
}
