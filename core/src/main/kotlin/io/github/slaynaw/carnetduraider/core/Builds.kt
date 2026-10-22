package io.github.slaynaw.carnetduraider.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Les cases d'un build, chacune pour un objet d'un [emplacement] : module, bouclier, deux armes, soins,
 * grenades, puis six « autres » (tyrolienne, pièges…). La première des autres garde le nom enregistré de
 * l'ancienne case « Divers » : un build d'avant la 0.11 la retrouve en « Autre 1 ».
 */
@Serializable
enum class CaseBuild(val libelle: String, val emplacement: Emplacement) {
    MODULE("Module", Emplacement.MODULE),
    BOUCLIER("Bouclier", Emplacement.BOUCLIER),
    ARME_1("Arme 1", Emplacement.ARME),
    ARME_2("Arme 2", Emplacement.ARME),
    SOINS("Soins", Emplacement.SOINS),
    GRENADES("Grenades", Emplacement.GRENADE),

    @SerialName("DIVERS")
    AUTRE_1("Autre 1", Emplacement.DIVERS),
    AUTRE_2("Autre 2", Emplacement.DIVERS),
    AUTRE_3("Autre 3", Emplacement.DIVERS),
    AUTRE_4("Autre 4", Emplacement.DIVERS),
    AUTRE_5("Autre 5", Emplacement.DIVERS),
    AUTRE_6("Autre 6", Emplacement.DIVERS),
    ;

    /** Une des six cases « Autres », affichées ensemble sous ce titre. */
    val autre: Boolean get() = emplacement == Emplacement.DIVERS
}

/** Un build du joueur : son [nom] (vide tant qu'il n'en a pas donné) et l'objet choisi pour chaque case. */
@Serializable
data class Build(val nom: String = "", val objets: Map<CaseBuild, String> = emptyMap()) {
    val vide: Boolean get() = objets.isEmpty()
}

/** Une raison de garder un objet pour un build : il est dans le build [build], ou sert à fabriquer [objetId]. */
data class UsageBuild(val build: String, val objetId: String, val direct: Boolean)

/**
 * Section « Mes builds » : [NOMBRE] builds au plus. Les objets choisis, et tout ce qu'il faut pour les
 * fabriquer ou les améliorer (de proche en proche), sont à garder : ils ne sont plus proposés à la vente.
 */
class Builds(private val catalogue: Catalogue) {

    /** Nom affiché d'un build : celui donné par le joueur, ou « Build 2 ». */
    fun nom(build: Build, rang: Int): String = build.nom.trim().ifEmpty { "Build ${rang + 1}" }

    /** Objets possibles pour une case, par ordre alphabétique. */
    fun choix(case: CaseBuild): List<Objet> = catalogue.tous.filter { it.emplacement == case.emplacement }

    /**
     * Ce qu'il faut pour fabriquer l'objet : ingrédients de ses recettes et, pour une arme améliorée, l'arme
     * d'avant et le coût de l'amélioration ; puis, de proche en proche, ce qu'il faut pour ceux-là.
     */
    fun materiaux(objetId: String): Set<String> {
        val vus = LinkedHashSet<String>()
        val aVoir = ArrayDeque(directs(objetId))
        while (aVoir.isNotEmpty()) {
            val id = aVoir.removeFirst()
            if (id != objetId && vus.add(id)) aVoir.addAll(directs(id))
        }
        return vus
    }

    private fun directs(objetId: String): List<String> {
        val obtention = catalogue.objet(objetId)?.obtention ?: return emptyList()
        return obtention.fabrications.flatMap { f -> f.ingredients.map { it.id } } +
            listOfNotNull(obtention.amelioration?.depuis) +
            obtention.amelioration?.ingredients.orEmpty().map { it.id }
    }

    /** Objets à garder pour les builds : identifiant → les builds et objets pour lesquels il sert. */
    fun aGarder(builds: List<Build>): Map<String, List<UsageBuild>> {
        val usages = LinkedHashMap<String, MutableList<UsageBuild>>()
        fun ajouter(id: String, usage: UsageBuild) {
            val liste = usages.getOrPut(id) { mutableListOf() }
            if (usage !in liste) liste += usage
        }
        builds.forEachIndexed { rang, build ->
            val nom = nom(build, rang)
            CaseBuild.entries.mapNotNull { build.objets[it] }.distinct().forEach { objetId ->
                ajouter(objetId, UsageBuild(nom, objetId, direct = true))
                materiaux(objetId).forEach { ajouter(it, UsageBuild(nom, objetId, direct = false)) }
            }
        }
        return usages
    }

    companion object {
        const val NOMBRE = 6
        private val json = Json { ignoreUnknownKeys = true }
        private val serialiseur = ListSerializer(Build.serializer())

        /** Les [NOMBRE] builds, enregistrés en JSON. */
        fun encoder(builds: List<Build>): String = json.encodeToString(serialiseur, completer(builds))

        /** Relit les builds enregistrés ; un texte illisible ou absent donne des builds vides. */
        fun decoder(texte: String?): List<Build> {
            val lus = texte?.let { runCatching { json.decodeFromString(serialiseur, it) }.getOrNull() }.orEmpty()
            return completer(lus)
        }

        /** Exactement [NOMBRE] builds : les manquants sont vides, ceux en trop sont ignorés. */
        fun completer(builds: List<Build>): List<Build> = (builds + List(NOMBRE) { Build() }).take(NOMBRE)
    }
}
