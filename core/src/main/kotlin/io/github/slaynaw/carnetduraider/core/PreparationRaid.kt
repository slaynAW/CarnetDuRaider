package io.github.slaynaw.carnetduraider.core

/**
 * Conseil pour un raid : les cartes, puis les zones, où l'on a le plus de chances de trouver les objets
 * cherchés, d'après le butin des fichiers du jeu ([Obtention.cartes] et [Obtention.zones]).
 *
 * Chaque objet compte pour au plus 1 point par carte (son indice / 100 : 1 sur sa meilleure carte) : une
 * carte qui donne bien beaucoup d'objets de la liste passe devant une carte excellente pour un seul.
 * Ce n'est pas une probabilité : le jeu tire le butin sur ses serveurs.
 */
class PreparationRaid(private val catalogue: Catalogue) {

    fun conseiller(objets: Collection<String>): ConseilRaid {
        val cherches = objets.distinct().mapNotNull { catalogue.objet(it) }
        val sansButin = cherches.filter { it.obtention.cartes.isEmpty() }.map { it.id }
        val cartes = cherches.flatMap { o -> o.obtention.cartes.map { c -> c.carte to (o.id to c.indice) } }
            .groupBy({ it.first }, { it.second })
            .map { (carteId, parObjet) -> carteConseillee(carteId, parObjet, cherches) }
            .sortedWith(compareByDescending<CarteConseillee> { it.score }.thenByDescending { it.objets.size }.thenBy { it.carteId })
        return ConseilRaid(cartes, sansButin, parcours(cartes))
    }

    /**
     * Les quelques cartes qui, ensemble, donnent tous les objets de la liste : à chaque tour, la carte qui
     * apporte le plus de chances sur les objets pas encore couverts (au plus [CARTES_PARCOURS] cartes).
     */
    private fun parcours(cartes: List<CarteConseillee>): List<EtapeParcours> {
        val indices = cartes.associate { c -> c.carteId to c.indices }
        val restants = cartes.flatMap { it.objets }.toMutableSet()
        val etapes = mutableListOf<EtapeParcours>()
        while (restants.isNotEmpty() && etapes.size < CARTES_PARCOURS) {
            val (carteId, apport) = indices
                .filterKeys { id -> etapes.none { it.carteId == id } }
                .map { (id, parObjet) -> id to parObjet.filterKeys { it in restants } }
                .maxWithOrNull(compareBy<Pair<String, Map<String, Int>>> { it.second.values.sum() }.thenBy { it.second.size }.thenByDescending { it.first })
                ?: break
            if (apport.isEmpty()) break
            etapes += EtapeParcours(carteId, apport.entries.sortedByDescending { it.value }.map { it.key })
            restants -= apport.keys
        }
        return etapes
    }

    private fun carteConseillee(carteId: String, parObjet: List<Pair<String, Int>>, cherches: List<Objet>): CarteConseillee {
        val zones = cherches.flatMap { o -> o.obtention.zones.filter { it.carte == carteId }.map { z -> z.zone to (o.id to z.indice) } }
            .groupBy({ it.first }, { it.second })
            .map { (zone, l) -> ZoneConseillee(zone, l.sumOf { it.second } / 100.0, l.sortedByDescending { it.second }.map { it.first }) }
            .sortedWith(compareByDescending<ZoneConseillee> { it.objets.size }.thenByDescending { it.score }.thenBy { it.zone })
        return CarteConseillee(
            carteId = carteId,
            score = parObjet.sumOf { it.second } / 100.0,
            objets = parObjet.sortedByDescending { it.second }.map { it.first },
            meilleurePour = parObjet.filter { it.second == 100 }.map { it.first },
            indices = parObjet.toMap(),
            zones = zones.take(ZONES_CONSEILLEES),
        )
    }

    companion object {
        const val ZONES_CONSEILLEES = 4
        const val CARTES_PARCOURS = 3
    }
}

/**
 * [cartes] : de la meilleure à la moins bonne. [parcours] : les cartes à faire pour tout trouver, avec les
 * objets que chacune ajoute. [sansButin] : objets qu'aucune carte ne donne (à acheter, fabriquer, recycler…).
 */
data class ConseilRaid(
    val cartes: List<CarteConseillee>,
    val sansButin: List<String>,
    val parcours: List<EtapeParcours> = emptyList(),
)

/** Une carte du parcours et les [objets] de la liste qu'elle ajoute (pas déjà donnés par les précédentes). */
data class EtapeParcours(val carteId: String, val objets: List<String>)

/**
 * Une carte pour le raid : [objets] de la liste qu'on peut y trouver (du plus au moins favorable),
 * [meilleurePour] ceux dont c'est la meilleure carte, et les [zones] où chercher.
 */
data class CarteConseillee(
    val carteId: String,
    val score: Double,
    val objets: List<String>,
    val meilleurePour: List<String>,
    val zones: List<ZoneConseillee>,
    /** Indice (1 à 100) de chaque objet sur cette carte. */
    val indices: Map<String, Int> = emptyMap(),
)

/** Une zone de la carte et les objets de la liste qu'on y trouve le mieux. */
data class ZoneConseillee(val zone: String, val score: Double, val objets: List<String>)
