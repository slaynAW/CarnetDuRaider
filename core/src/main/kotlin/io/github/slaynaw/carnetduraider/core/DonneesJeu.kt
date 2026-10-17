package io.github.slaynaw.carnetduraider.core

import kotlinx.serialization.SerialName
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
    val obtention: Obtention = Obtention(),
)

/**
 * Comment obtenir un objet, d'après les fichiers du jeu. [cartes] et [zones] : où il y en a le plus dans le
 * butin (condition normale), avec un [ZoneButin.indice] de 1 à 100 relatif au meilleur endroit. Ce n'est pas
 * un taux de drop : le jeu tire le butin sur ses serveurs.
 */
@Serializable
data class Obtention(
    val cartes: List<CarteButin> = emptyList(),
    val zones: List<ZoneButin> = emptyList(),
    val achats: List<Achat> = emptyList(),
    val fabrications: List<Fabrication> = emptyList(),
    /** Quêtes et projets qui le donnent en récompense (noms officiels). */
    val recompenses: List<String> = emptyList(),
)

@Serializable
data class CarteButin(val carte: String, val indice: Int)

@Serializable
data class ZoneButin(val carte: String, val zone: String, val indice: Int)

/**
 * Offre d'un commerçant : [quantite] objets contre [pieces], des [objets] précis, ou des objets d'une valeur
 * totale de [valeurObjets] pièces. [limite] : nombre d'achats possibles (par jour si [parJour]).
 */
@Serializable
data class Achat(
    val commercant: String,
    val quantite: Int = 1,
    val pieces: Int = 0,
    val objets: List<Quantite> = emptyList(),
    val valeurObjets: Int = 0,
    val limite: Int? = null,
    val parJour: Boolean = false,
)

/** Recette : à l'[etabli] (nom officiel, niveau [niveau]) ou, sans établi, pendant un raid. [plan] : plan à débloquer. */
@Serializable
data class Fabrication(
    val ingredients: List<Quantite>,
    val quantite: Int = 1,
    val etabli: String? = null,
    val niveau: Int? = null,
    val plan: String? = null,
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
 * Plan simplifié d'une carte (image `assets/cartes/<id>.webp`, le plan en jeu) : zones, sorties et points
 * d'apparition des ARC, avec leur nom français officiel et leur position (0 à 1 sur la largeur et la hauteur).
 */
@Serializable
data class Plan(
    val zones: List<ZonePlan> = emptyList(),
    val sorties: List<SortiePlan> = emptyList(),
    val arcs: List<ArcPlan> = emptyList(),
)

/**
 * [detail] : 0 pour les grandes zones, 1 et 2 pour les lieux qui n'apparaissent qu'en zoomant, 3 pour les
 * autres lieux nommés (zones de butin), affichés seulement quand on les cherche.
 */
@Serializable
data class ZonePlan(val nom: String, val x: Float, val y: Float, val detail: Int = 0)

/** Sortie (point d'extraction) : [nom] officiel, ou `null` si elle n'en a pas à elle (terminal du téléphérique). */
@Serializable
data class SortiePlan(val type: TypeSortie, val x: Float, val y: Float, val nom: String? = null)

@Serializable
enum class TypeSortie {
    @SerialName("trappe") TRAPPE,
    @SerialName("ascenseur") ASCENSEUR,
    @SerialName("metro") METRO,
    @SerialName("conduit") CONDUIT,
    @SerialName("telepherique") TELEPHERIQUE,
}

/** Un ARC et les points où il peut apparaître sur la carte (`[x, y]`, toutes conditions confondues). */
@Serializable
data class ArcPlan(val nom: String, val points: List<List<Float>>)

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
