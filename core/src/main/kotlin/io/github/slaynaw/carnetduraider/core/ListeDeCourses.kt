package io.github.slaynaw.carnetduraider.core

/** Ce que le joueur veut atteindre : un niveau d'établi, une quête ou une étape de projet. */
sealed interface Objectif {
    data class NiveauDEtabli(val etabliId: String, val niveau: Int) : Objectif
    data class QueteATerminer(val queteId: String) : Objectif

    /** [etape] : rang de l'étape dans le projet, à partir de 0. */
    data class EtapeDeProjet(val projetId: String, val etape: Int) : Objectif
}

/** Clé stable d'un objectif (« etabli:utility_bench:1 », « quete:clearer_skies », « projet:…:0 »). */
val Objectif.cle: String
    get() = when (this) {
        is Objectif.NiveauDEtabli -> "etabli:$etabliId:$niveau"
        is Objectif.QueteATerminer -> "quete:$queteId"
        is Objectif.EtapeDeProjet -> "projet:$projetId:$etape"
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
 * Un objet à trouver pendant les raids, et son décompte : ce que demandent les objectifs suivis ([pour])
 * plus ce que le joueur a ajouté lui-même ([ajoute], 0 s'il ne l'a pas ajouté). [voulu] : combien il en
 * faut en tout ; [trouves] : combien ont déjà été rapportés.
 */
data class ObjetATrouver(
    val objetId: String,
    val trouves: Int,
    val voulu: Int,
    val pour: List<Besoin>,
    val ajoute: Int = 0,
) {
    val ajouteParLeJoueur: Boolean get() = ajoute > 0

    /** Combien il en reste à trouver. */
    val reste: Int get() = (voulu - trouves).coerceAtLeast(0)
    val trouve: Boolean get() = trouves >= voulu
}

/** Ce qui a été rapporté : pour les objectifs ([apports]) et pour les objets ajoutés par le joueur ([trouvesRaid]). */
data class Rapporte(val apports: Apports, val trouvesRaid: Map<String, Int>)

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

    /**
     * Toute la liste du raid, objets déjà trouvés compris : ce que demandent les objectifs (les plus en
     * retard d'abord), avec ce que le joueur en a ajouté lui-même ([ajouts] : identifiant → quantité voulue ;
     * [trouvesAjouts] : combien il en a rapporté), puis ses autres ajouts.
     */
    fun listeDuRaid(
        objectifs: List<Objectif>,
        apports: Apports,
        ajouts: Map<String, Int>,
        trouvesAjouts: Map<String, Int> = emptyMap(),
    ): List<ObjetATrouver> {
        fun trouvesDuJoueur(id: String) = minOf(trouvesAjouts[id] ?: 0, ajouts[id] ?: 0)
        val lignes = listeDeCourses(objectifs, apports).lignes
        val pourObjectifs = lignes.map { l ->
            val ajoute = ajouts[l.objetId] ?: 0
            ObjetATrouver(l.objetId, l.apporte + trouvesDuJoueur(l.objetId), l.besoin + ajoute, l.pour, ajoute)
        }
        val dejaLa = lignes.map { it.objetId }.toSet()
        val autres = ajouts.filterKeys { it !in dejaLa }
            .map { (id, voulu) -> ObjetATrouver(id, trouvesDuJoueur(id), voulu, emptyList(), voulu) }
        return pourObjectifs + autres
    }

    /** Ce qu'il reste à trouver : la liste du raid sans les objets déjà tous rapportés. */
    fun aTrouver(
        objectifs: List<Objectif>,
        apports: Apports,
        ajouts: Map<String, Int>,
        trouvesAjouts: Map<String, Int> = emptyMap(),
    ): List<ObjetATrouver> = listeDuRaid(objectifs, apports, ajouts, trouvesAjouts).filter { !it.trouve }

    /**
     * Répartit le butin [saisis] (identifiant → quantité rapportée) entre ce qui le demande encore : d'abord
     * l'objectif le plus près d'être terminé (le moins d'objets qui lui manquent), puis les suivants ; un
     * objet ajouté par le joueur ([ajouts]) compte comme un objectif. Ce qui dépasse n'est gardé nulle part.
     */
    fun repartir(objectifs: List<Objectif>, ajouts: Map<String, Int>, avant: Rapporte, saisis: Map<String, Int>): Rapporte {
        val apports = avant.apports.mapValues { it.value.toMutableMap() }.toMutableMap()
        val trouvesRaid = avant.trouvesRaid.toMutableMap()
        val destinataires = objectifs.distinct().map { o -> Destinataire(o.cle, manque(o, avant.apports).toMutableMap()) } +
            ajouts.mapNotNull { (id, voulu) ->
                (voulu - (avant.trouvesRaid[id] ?: 0)).takeIf { it > 0 }?.let { Destinataire(null, mutableMapOf(id to it)) }
            }
        // Ordre fixé avant la répartition : le plus près d'être terminé d'abord, puis l'ordre de la liste.
        val ordre = destinataires.sortedBy { it.manque.values.sum() }
        for ((id, quantite) in saisis) {
            var reste = quantite
            for (d in ordre) {
                if (reste <= 0) break
                val pris = minOf(reste, d.manque[id] ?: 0)
                if (pris <= 0) continue
                d.manque[id] = (d.manque[id] ?: 0) - pris
                reste -= pris
                if (d.cle == null) {
                    trouvesRaid[id] = (trouvesRaid[id] ?: 0) + pris
                } else {
                    val apporte = apports.getOrPut(d.cle) { mutableMapOf() }
                    apporte[id] = (apporte[id] ?: 0) + pris
                }
            }
        }
        return Rapporte(apports.mapValues { it.value.toMap() }.filterValues { it.isNotEmpty() }, trouvesRaid.toMap())
    }

    /** Un objectif ([cle]) ou un objet ajouté par le joueur ([cle] nulle), et ce qui lui manque. */
    private class Destinataire(val cle: String?, val manque: MutableMap<String, Int>)
}
