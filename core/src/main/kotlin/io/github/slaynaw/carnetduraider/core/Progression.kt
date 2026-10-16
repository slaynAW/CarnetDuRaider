package io.github.slaynaw.carnetduraider.core

/**
 * Où en est le joueur dans le jeu : niveau de chaque établi de l'Atelier et quêtes de l'histoire
 * terminées. C'est à partir de là que l'appli choisit elle-même les objectifs à suivre (le prochain
 * niveau de chaque établi, les quêtes en cours), au lieu de les ajouter un par un.
 */
data class Progression(
    /** Niveau actuel des établis déjà améliorés (les autres sont à leur niveau de départ). */
    val niveauxEtablis: Map<String, Int> = emptyMap(),
    val quetesTerminees: Set<String> = emptySet(),
    /** Établis dont le joueur ne veut pas suivre l'amélioration. */
    val etablisIgnores: Set<String> = emptySet(),
)

/** Règles de progression, d'après les données du jeu (enchaînement des quêtes, niveaux d'établis). */
class SuiviProgression(private val catalogue: Catalogue) {
    private val histoire = catalogue.donnees.quetes.filter { it.histoire }
    private val suites: Map<String, List<String>> = histoire
        .flatMap { q -> q.requiert.map { it to q.id } }
        .groupBy({ it.first }, { it.second })

    /** Niveau d'un établi avant toute amélioration (le Coquillard commence au niveau 1). */
    fun niveauDeDepart(etabli: Etabli): Int = (etabli.niveaux.minOfOrNull { it.niveau } ?: 1) - 1

    fun niveauMax(etabli: Etabli): Int = etabli.niveaux.maxOfOrNull { it.niveau } ?: niveauDeDepart(etabli)

    fun niveau(progression: Progression, etabli: Etabli): Int =
        (progression.niveauxEtablis[etabli.id] ?: niveauDeDepart(etabli)).coerceIn(niveauDeDepart(etabli), niveauMax(etabli))

    /** Prochaine amélioration de l'établi, ou rien s'il est au niveau maximum. */
    fun prochainNiveau(progression: Progression, etabli: Etabli): NiveauEtabli? {
        val actuel = niveau(progression, etabli)
        return etabli.niveaux.filter { it.niveau > actuel }.minByOrNull { it.niveau }
    }

    /** Quêtes de l'histoire disponibles : pas encore terminées, et toutes les précédentes le sont. */
    fun quetesEnCours(progression: Progression): List<Quete> = histoire.filter { q ->
        q.id !in progression.quetesTerminees && q.requiert.all { it in progression.quetesTerminees }
    }

    /** Objectifs choisis par l'appli : prochain niveau de chaque établi suivi, quêtes en cours qui demandent des objets. */
    fun objectifsAutomatiques(progression: Progression): List<Objectif> {
        val etablis = catalogue.donnees.etablis
            .filter { it.id !in progression.etablisIgnores }
            .mapNotNull { e -> prochainNiveau(progression, e)?.let { Objectif.NiveauDEtabli(e.id, it.niveau) } }
        val quetes = quetesEnCours(progression).filter { it.objets.isNotEmpty() }.map { Objectif.QueteATerminer(it.id) }
        return etablis + quetes
    }

    /**
     * Mise en place rapide : le joueur indique les quêtes affichées en cours dans son jeu. Toutes celles
     * qui mènent à elles sont terminées ; elles-mêmes, et tout ce qui vient après, ne le sont pas.
     */
    fun indiquerQuetesEnCours(progression: Progression, enCours: Collection<String>): Progression {
        val avant = enCours.flatMap { precedentes(it) }.toSet()
        val apres = enCours.flatMap { suivantes(it) + it }.toSet()
        return progression.copy(quetesTerminees = progression.quetesTerminees + avant - apres)
    }

    /** Quête terminée : elle, et celles qui y mènent. Les suivantes deviennent disponibles. */
    fun terminerQuete(progression: Progression, queteId: String): Progression =
        progression.copy(quetesTerminees = progression.quetesTerminees + precedentes(queteId) + queteId)

    /** Annule une quête marquée terminée par erreur (et celles qui en dépendent). */
    fun annulerQuete(progression: Progression, queteId: String): Progression =
        progression.copy(quetesTerminees = progression.quetesTerminees - suivantes(queteId) - queteId)

    fun changerNiveau(progression: Progression, etabli: Etabli, niveau: Int): Progression =
        progression.copy(niveauxEtablis = progression.niveauxEtablis + (etabli.id to niveau.coerceIn(niveauDeDepart(etabli), niveauMax(etabli))))

    fun suivreEtabli(progression: Progression, etabliId: String, suivi: Boolean): Progression =
        progression.copy(etablisIgnores = if (suivi) progression.etablisIgnores - etabliId else progression.etablisIgnores + etabliId)

    /** Retire de la réserve ce que le jeu a consommé (amélioration construite, objets livrés). */
    fun retirer(reserve: Map<String, Int>, cout: List<Quantite>): Map<String, Int> {
        val resultat = reserve.toMutableMap()
        for ((id, quantite) in cout) {
            val reste = (resultat[id] ?: 0) - quantite
            if (reste > 0) resultat[id] = reste else resultat.remove(id)
        }
        return resultat
    }

    /** Toutes les quêtes qui mènent à [queteId] (prérequis, de proche en proche). */
    fun precedentes(queteId: String): Set<String> = parcourir(queteId) { catalogue.quete(it)?.requiert.orEmpty() }

    /** Toutes les quêtes qui viennent après [queteId]. */
    fun suivantes(queteId: String): Set<String> = parcourir(queteId) { suites[it].orEmpty() }

    private fun parcourir(depart: String, voisins: (String) -> List<String>): Set<String> {
        val vus = LinkedHashSet<String>()
        val aVoir = ArrayDeque(voisins(depart))
        while (aVoir.isNotEmpty()) {
            val id = aVoir.removeFirst()
            if (vus.add(id)) aVoir.addAll(voisins(id))
        }
        return vus - depart
    }
}
