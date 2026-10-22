package io.github.slaynaw.carnetduraider.core

/**
 * Où en est le joueur dans le jeu. Tout est considéré comme déjà fait, sauf ce qu'il indique en cours :
 * ses quêtes, le niveau en cours de ses établis, l'étape en cours de ses projets et Expéditions. C'est à
 * partir de là que l'appli choisit les objectifs ; quand l'un d'eux est terminé, la suite s'ajoute seule
 * (niveau suivant, quête suivante, étape suivante).
 *
 * À l'installation, rien n'est en cours : aucun objectif, l'appli est vide.
 */
data class Progression(
    /** Niveau actuel des établis indiqués (les autres ne comptent pas). */
    val niveauxEtablis: Map<String, Int> = emptyMap(),
    val quetesTerminees: Set<String> = emptySet(),
    /** Établis dont un niveau est en cours (aucun par défaut). */
    val etablisSuivis: Set<String> = emptySet(),
    /** Nombre d'étapes terminées de chaque projet. */
    val etapesProjets: Map<String, Int> = emptyMap(),
    /** Projets et Expéditions en cours (aucun par défaut). */
    val projetsSuivis: Set<String> = emptySet(),
    /** Le joueur a indiqué ses quêtes en cours : elles, puis leurs suites, deviennent des objectifs. */
    val quetesIndiquees: Boolean = false,
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

    /** Niveau en cours de l'établi, ou `null` s'il n'est pas indiqué en cours (ou s'il est au maximum). */
    fun niveauEnCours(progression: Progression, etabli: Etabli): Int? =
        if (etabli.id in progression.etablisSuivis) prochainNiveau(progression, etabli)?.niveau else null

    /**
     * Le joueur indique le niveau en cours de l'établi (les précédents sont faits), ou `null` : rien en
     * cours pour cet établi.
     */
    fun indiquerNiveauEnCours(progression: Progression, etabli: Etabli, niveau: Int?): Progression {
        if (niveau == null) return progression.copy(etablisSuivis = progression.etablisSuivis - etabli.id)
        val fait = (niveau - 1).coerceIn(niveauDeDepart(etabli), niveauMax(etabli))
        return progression.copy(
            niveauxEtablis = progression.niveauxEtablis + (etabli.id to fait),
            etablisSuivis = progression.etablisSuivis + etabli.id,
        )
    }

    /** Quêtes de l'histoire en cours : pas encore terminées, et toutes les précédentes le sont. */
    fun quetesEnCours(progression: Progression): List<Quete> = if (!progression.quetesIndiquees) {
        emptyList()
    } else {
        histoire.filter { q -> q.id !in progression.quetesTerminees && q.requiert.all { it in progression.quetesTerminees } }
    }

    /** Rang de la prochaine étape du projet, ou `null` s'il est terminé. */
    fun prochaineEtape(progression: Progression, projet: Projet): Int? =
        (progression.etapesProjets[projet.id] ?: 0).takeIf { it < projet.etapes.size }

    /** Étape en cours du projet (rang, à partir de 0), ou `null` s'il n'est pas en cours (ou terminé). */
    fun etapeEnCours(progression: Progression, projet: Projet): Int? =
        if (projet.id in progression.projetsSuivis) prochaineEtape(progression, projet) else null

    /** Le joueur indique l'étape en cours du projet (les précédentes sont faites), ou `null` : pas en cours. */
    fun indiquerEtapeEnCours(progression: Progression, projet: Projet, etape: Int?): Progression {
        if (etape == null) return progression.copy(projetsSuivis = progression.projetsSuivis - projet.id)
        return progression.copy(
            etapesProjets = progression.etapesProjets + (projet.id to etape.coerceIn(0, projet.etapes.size)),
            projetsSuivis = progression.projetsSuivis + projet.id,
        )
    }

    /**
     * Les objectifs : niveau en cours de chaque établi indiqué, quêtes en cours (avec ou sans objets à
     * rapporter), étape en cours de chaque projet et Expédition indiqués.
     */
    fun objectifs(progression: Progression): List<Objectif> {
        val etablis = catalogue.donnees.etablis.mapNotNull { e ->
            niveauEnCours(progression, e)?.let { Objectif.NiveauDEtabli(e.id, it) }
        }
        val quetes = quetesEnCours(progression).map { Objectif.QueteATerminer(it.id) }
        val projets = catalogue.donnees.projets.mapNotNull { p ->
            etapeEnCours(progression, p)?.let { Objectif.EtapeDeProjet(p.id, it) }
        }
        return etablis + quetes + projets
    }

    /**
     * Le joueur indique les quêtes en cours dans son jeu. Tout le reste est considéré comme fait, sauf ce
     * qui vient après elles. Une quête cochée qui mène à une autre quête cochée est faite (l'autre est plus
     * loin dans l'histoire).
     */
    fun indiquerQuetesEnCours(progression: Progression, enCours: Collection<String>): Progression {
        val cochees = enCours.filter { id -> histoire.any { it.id == id } }.toSet()
        val faitesAvant = cochees.flatMap { precedentes(it) }.toSet()
        val enCoursVraiment = cochees - faitesAvant
        val aVenir = enCoursVraiment.flatMap { suivantes(it) }.toSet()
        val terminees = histoire.map { it.id }.toSet() - enCoursVraiment - aVenir
        return progression.copy(quetesTerminees = terminees, quetesIndiquees = true)
    }

    /** Quête terminée : elle, et celles qui y mènent. Les suivantes deviennent en cours. */
    fun terminerQuete(progression: Progression, queteId: String): Progression =
        progression.copy(quetesTerminees = progression.quetesTerminees + precedentes(queteId) + queteId)

    /**
     * Vrai si l'objectif est la prochaine chose à faire : niveau en cours de l'établi, quête en cours (pas
     * terminée, ses prérequis le sont), étape en cours du projet. Un objectif plus lointain ne l'est pas.
     */
    fun atteignable(progression: Progression, objectif: Objectif): Boolean = when (objectif) {
        is Objectif.NiveauDEtabli ->
            catalogue.etabli(objectif.etabliId)?.let { prochainNiveau(progression, it)?.niveau == objectif.niveau } ?: false
        is Objectif.QueteATerminer -> catalogue.quete(objectif.queteId)?.let { q ->
            q.id !in progression.quetesTerminees && q.requiert.all { it in progression.quetesTerminees }
        } ?: false
        is Objectif.EtapeDeProjet ->
            catalogue.projet(objectif.projetId)?.let { prochaineEtape(progression, it) == objectif.etape } ?: false
        // Le butin recherché ne fait pas partie de la progression du jeu.
        is Objectif.ObjetRecherche -> false
    }

    /** L'objectif est fait : niveau construit, quête terminée (et celles qui y mènent), étape de projet faite. */
    fun terminer(progression: Progression, objectif: Objectif): Progression = when (objectif) {
        is Objectif.NiveauDEtabli -> catalogue.etabli(objectif.etabliId)?.let { e ->
            val fait = maxOf(niveau(progression, e), objectif.niveau).coerceAtMost(niveauMax(e))
            progression.copy(niveauxEtablis = progression.niveauxEtablis + (e.id to fait))
        } ?: progression
        is Objectif.QueteATerminer -> terminerQuete(progression, objectif.queteId)
        is Objectif.EtapeDeProjet -> catalogue.projet(objectif.projetId)?.let { p ->
            val faites = maxOf(progression.etapesProjets[p.id] ?: 0, objectif.etape + 1).coerceAtMost(p.etapes.size)
            progression.copy(etapesProjets = progression.etapesProjets + (p.id to faites))
        } ?: progression
        is Objectif.ObjetRecherche -> progression
    }

    /**
     * Termine d'eux-mêmes les objectifs dont tout a été rapporté ([complet]), puis la suite si elle l'est
     * déjà aussi. Renvoie la progression et les objectifs terminés, dans l'ordre.
     */
    fun terminerLesComplets(progression: Progression, complet: (Objectif) -> Boolean): Pair<Progression, List<Objectif>> {
        var p = progression
        val termines = mutableListOf<Objectif>()
        while (true) {
            val suivant = objectifs(p).firstOrNull { it !in termines && atteignable(p, it) && complet(it) } ?: break
            p = terminer(p, suivant)
            termines += suivant
        }
        return p to termines
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
