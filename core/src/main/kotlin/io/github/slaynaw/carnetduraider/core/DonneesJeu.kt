package io.github.slaynaw.carnetduraider.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Données du jeu, avec les noms français officiels uniquement (voir CLAUDE.md).
 *
 * Le fichier `donnees-jeu.json` est produit par `scripts/importer_donnees_jeu.py` : ne pas le
 * modifier à la main, et ne jamais y ajouter de nom traduit par nos soins.
 */
@Serializable
data class DonneesJeu(
    val versionJeu: String,
    val source: String,
    val objets: List<Objet>,
    val etablis: List<Etabli>,
    val quetes: List<Quete>,
    val projets: List<Projet> = emptyList(),
    val cartes: List<Carte>,
    val conditions: List<Condition>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun lire(texte: String): DonneesJeu = json.decodeFromString(serializer(), texte)

        /** Données embarquées dans le module (ressource `donnees-jeu.json`). */
        fun embarquees(): DonneesJeu {
            val flux = DonneesJeu::class.java.getResourceAsStream("/donnees-jeu.json")
                ?: error("Ressource donnees-jeu.json introuvable")
            return flux.use { lire(it.readBytes().decodeToString()) }
        }
    }
}

@Serializable
data class Quantite(val id: String, val quantite: Int)

@Serializable
data class Objet(
    val id: String,
    val nom: String,
    /** Vide pour les objets de quête, qui n'ont pas de rareté dans le jeu. */
    val rarete: Rarete? = null,
    /** Catégorie telle qu'affichée dans le jeu (« Matériau de la Surface »…). */
    val categorie: String? = null,
    /** Valeur de vente unitaire, en pièces. */
    val valeur: Int = 0,
    val pile: Int = 1,
    val recyclage: List<Quantite> = emptyList(),
    val recuperation: List<Quantite> = emptyList(),
    /** Types de lieux où on le trouve (« Mécanique », « ARC »…). */
    val lieux: List<String> = emptyList(),
)

/** Raretés, avec leur libellé officiel dans la version française du jeu. */
@Serializable
enum class Rarete(val libelle: String) {
    COMMUN("Commun"),
    INHABITUEL("Inhabituel"),
    RARE("Rare"),
    EPIQUE("Épique"),
    LEGENDAIRE("Légendaire"),
}

@Serializable
data class Etabli(val id: String, val nom: String, val niveaux: List<NiveauEtabli>)

@Serializable
data class NiveauEtabli(val niveau: Int, val cout: List<Quantite>)

@Serializable
data class Quete(
    val id: String,
    val nom: String,
    val commercant: String? = null,
    /** Quête de l'histoire (principale ou secondaire) : fait partie de l'enchaînement suivi par l'appli. */
    val histoire: Boolean = false,
    /** Quêtes à terminer avant de pouvoir commencer celle-ci. */
    val requiert: List<String> = emptyList(),
    /** Objets à livrer ou à obtenir pour la terminer. */
    val objets: List<Quantite> = emptyList(),
)

/**
 * [cleCalendrier] : nom anglais officiel, utilisé seulement pour reconnaître la carte dans le
 * calendrier d'Embark (publié en anglais). Jamais affiché.
 */
@Serializable
data class Carte(val id: String, val nom: String, val cleCalendrier: String? = null, val plan: Plan = Plan())

/**
 * Plan simplifié d'une carte (image `assets/cartes/<id>.webp`, le plan en jeu) : zones et trappes de
 * sortie, avec leur nom français officiel et leur position (0 à 1 sur la largeur et la hauteur).
 */
@Serializable
data class Plan(val zones: List<ZonePlan> = emptyList(), val sorties: List<PointPlan> = emptyList())

/** [detail] : 0 pour les grandes zones, 1 et 2 pour les lieux qui n'apparaissent qu'en zoomant. */
@Serializable
data class ZonePlan(val nom: String, val x: Float, val y: Float, val detail: Int = 0)

@Serializable
data class PointPlan(val nom: String, val x: Float, val y: Float)

/** Projet du jeu (Expédition, projet de saison…) : des étapes successives, chacune avec ses objets à livrer. */
@Serializable
data class Projet(val id: String, val nom: String, val etapes: List<EtapeProjet>)

/**
 * [nom] : nom officiel de l'étape, ou `null` s'il n'est pas vérifié. [autresObjectifs] : conditions qui
 * ne sont pas des objets précis (valeur à atteindre, quêtes), à remplir en jeu.
 */
@Serializable
data class EtapeProjet(val nom: String? = null, val cout: List<Quantite> = emptyList(), val autresObjectifs: Int = 0)

@Serializable
data class Condition(val id: String, val nom: String, val majeure: Boolean = false, val cleCalendrier: String? = null)
