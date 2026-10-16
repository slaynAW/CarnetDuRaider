package io.github.slaynaw.carnetduraider.core

import java.text.Normalizer

/** Accès rapide aux données du jeu : objets, établis et quêtes par identifiant, recherche par nom. */
class Catalogue(val donnees: DonneesJeu) {
    private val objetsParId = donnees.objets.associateBy { it.id }
    private val etablisParId = donnees.etablis.associateBy { it.id }
    private val quetesParId = donnees.quetes.associateBy { it.id }
    private val index = donnees.objets.map { it to normaliser(it.nom) }

    fun objet(id: String): Objet? = objetsParId[id]
    fun etabli(id: String): Etabli? = etablisParId[id]
    fun quete(id: String): Quete? = quetesParId[id]

    /**
     * Nom à afficher pour un objet. Un objet absent des données (nom français officiel non vérifié)
     * n'a jamais de nom inventé : on l'indique comme tel.
     */
    fun nomObjet(id: String): String = objetsParId[id]?.nom ?: NOM_A_VERIFIER

    /**
     * Recherche par nom, sans tenir compte des majuscules ni des accents. Les noms qui commencent par
     * la saisie passent avant ceux qui la contiennent seulement.
     */
    fun rechercher(saisie: String, limite: Int = 20): List<Objet> {
        val cle = normaliser(saisie)
        if (cle.isBlank()) return emptyList()
        return index
            .filter { (_, nom) -> nom.contains(cle) }
            .sortedWith(compareBy({ !it.second.startsWith(cle) }, { it.first.nom.length }, { it.first.nom }))
            .take(limite)
            .map { it.first }
    }

    /** Ce que rapporte le recyclage d'une unité, en pièces (valeur des matériaux obtenus). */
    fun valeurRecyclage(objet: Objet): Int =
        objet.recyclage.sumOf { (objetsParId[it.id]?.valeur ?: 0) * it.quantite }

    companion object {
        const val NOM_A_VERIFIER = "Nom à vérifier en jeu"

        fun normaliser(texte: String): String =
            Normalizer.normalize(texte.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace('’', '\'')
    }
}
