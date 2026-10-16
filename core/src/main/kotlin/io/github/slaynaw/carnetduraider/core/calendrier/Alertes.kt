package io.github.slaynaw.carnetduraider.core.calendrier

/** Réglages des alertes : conditions de la carte suivies, et combien de minutes avant le début prévenir. */
data class ReglagesAlertes(
    val conditions: Set<String> = emptySet(),
    val avanceMinutes: Int = 10,
) {
    companion object {
        val AVANCES = listOf(5, 10, 15, 30)
    }
}

/** Une alerte : prévenir à [quand] (millisecondes) que [creneau] va commencer. */
data class Alerte(val creneau: Creneau, val quand: Long) {
    /** Identifie le créneau, pour ne jamais prévenir deux fois du même. */
    val cle: String = "${creneau.debut}|${creneau.carteId ?: creneau.carte}|${creneau.conditionId}"
}

/**
 * Calcul des alertes à partir du calendrier officiel (gardé sur le téléphone) : quelles conditions
 * annoncer maintenant, et quand se réveiller pour la suivante.
 */
object Alertes {
    /** Une alarme peut arriver un peu en avance (Android regroupe les réveils) : marge acceptée. */
    const val TOLERANCE_MS = 60_000L

    /** Alertes des créneaux suivis qui n'ont pas encore commencé, dans l'ordre. */
    fun aVenir(calendrier: Calendrier, region: Region, reglages: ReglagesAlertes, maintenant: Long): List<Alerte> =
        calendrier.creneaux(region)
            .filter { it.conditionId != null && it.conditionId in reglages.conditions && it.debut > maintenant }
            .map { Alerte(it, it.debut - reglages.avanceMinutes * 60_000L) }
            .sortedBy { it.quand }

    /** Alertes à afficher maintenant : leur heure est venue et elles n'ont pas encore été montrées. */
    fun aMontrer(alertes: List<Alerte>, maintenant: Long, dejaMontrees: Set<String>): List<Alerte> =
        alertes.filter { it.quand <= maintenant + TOLERANCE_MS && it.cle !in dejaMontrees }

    /** Heure du prochain réveil, ou `null` s'il n'y a plus rien à annoncer dans le calendrier connu. */
    fun prochainReveil(alertes: List<Alerte>, maintenant: Long, dejaMontrees: Set<String>): Long? =
        alertes.filter { it.cle !in dejaMontrees && it.quand > maintenant + TOLERANCE_MS }.minOfOrNull { it.quand }

    /** Clés à garder en mémoire : celles des créneaux récents (les autres ne reviendront plus). */
    fun nettoyer(dejaMontrees: Set<String>, maintenant: Long): Set<String> =
        dejaMontrees.filter { cle -> (cle.substringBefore('|').toLongOrNull() ?: 0) > maintenant - 24 * Calendrier.UNE_HEURE }.toSet()
}
