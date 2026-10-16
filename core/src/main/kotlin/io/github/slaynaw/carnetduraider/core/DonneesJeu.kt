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
    /** Objets à livrer ou à obtenir pour la terminer. */
    val objets: List<Quantite>,
)

@Serializable
data class Carte(val id: String, val nom: String)

@Serializable
data class Condition(val id: String, val nom: String, val majeure: Boolean = false)
