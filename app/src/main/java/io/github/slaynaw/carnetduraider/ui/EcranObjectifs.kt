package io.github.slaynaw.carnetduraider.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Etabli
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Projet
import io.github.slaynaw.carnetduraider.core.Quantite
import io.github.slaynaw.carnetduraider.core.cle
import io.github.slaynaw.carnetduraider.ui.theme.Prompt
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ce que le joueur peut indiquer en cours, chacun avec sa liste complète. */
enum class ChoixEnCours(val bouton: String, val court: String, val titre: String, val groupe: String) {
    QUETES("Indiquer mes quêtes en cours", "Quêtes", "Mes quêtes en cours", "Quêtes"),
    ATELIERS("Indiquer mes ateliers en cours", "Ateliers", "Mes ateliers en cours", "Ateliers"),
    PROJETS("Indiquer mes projets en cours", "Projets", "Mes projets en cours", "Projets"),
    EXPEDITIONS("Indiquer mes expéditions en cours", "Expéditions", "Mes expéditions en cours", "Expéditions"),
}

/** Le groupe d'un objectif dans l'onglet Objectifs (le butin recherché n'y est jamais). */
private fun groupe(vm: CarnetViewModel, objectif: Objectif): ChoixEnCours? = when (objectif) {
    is Objectif.NiveauDEtabli -> ChoixEnCours.ATELIERS
    is Objectif.EtapeDeProjet -> if (vm.catalogue.projet(objectif.projetId)?.expedition == true) ChoixEnCours.EXPEDITIONS else ChoixEnCours.PROJETS
    is Objectif.QueteATerminer -> ChoixEnCours.QUETES
    else -> null
}

/** Ordre des groupes dans la liste : ateliers, projets, Expéditions, puis quêtes. */
private val ORDRE_GROUPES = listOf(ChoixEnCours.ATELIERS, ChoixEnCours.PROJETS, ChoixEnCours.EXPEDITIONS, ChoixEnCours.QUETES)

/** Nombre d'éléments indiqués en cours pour ce choix. */
private fun nombreEnCours(vm: CarnetViewModel, choix: ChoixEnCours): Int = when (choix) {
    ChoixEnCours.QUETES -> vm.suivi.quetesEnCours(vm.progression).size
    ChoixEnCours.ATELIERS -> vm.catalogue.donnees.etablis.count { vm.suivi.niveauEnCours(vm.progression, it) != null }
    ChoixEnCours.PROJETS -> vm.catalogue.donnees.projets.count { !it.expedition && vm.suivi.etapeEnCours(vm.progression, it) != null }
    ChoixEnCours.EXPEDITIONS -> vm.catalogue.donnees.projets.count { it.expedition && vm.suivi.etapeEnCours(vm.progression, it) != null }
}

/**
 * Onglet Objectifs : tout est considéré comme déjà fait, sauf ce que le joueur indique en cours (quatre
 * tuiles avec leur nombre en cours, chacune vers la liste complète). Dessous, ce qu'il reste à trouver en
 * raid, puis ce qui est en cours, par groupe (ateliers, projets, Expéditions, quêtes), avec les objets
 * demandés en pastilles ; tout se valide tout seul au retour de raid, et la suite s'ajoute.
 */
@Composable
fun EcranObjectifs(vm: CarnetViewModel, choisir: (ChoixEnCours) -> Unit, voirRaid: () -> Unit) {
    val objectifs = vm.objectifs
    val liste = vm.listeDeCourses()
    val groupes = objectifs.groupBy { groupe(vm, it) }

    EcranDeSection(
        Onglet.OBJECTIFS,
        if (objectifs.isEmpty()) "Rien en cours" else pluriel(objectifs.size, "objectif en cours", "objectifs en cours"),
    ) { defilement, haut ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = defilement,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = haut + 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Les quatre tuiles, sur une ligne.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoixEnCours.entries.forEach { choix ->
                        TuileEnCours(choix, nombreEnCours(vm, choix), onClick = { choisir(choix) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (objectifs.isEmpty()) {
                item {
                    Text(
                        "Tout ce que tu n'indiques pas est considéré comme déjà fait. Touche une tuile pour indiquer ce que tu as en cours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Signal.TexteDoux,
                    )
                }
            } else {
                item {
                    Surface(onClick = voirRaid, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (liste.objetsATrouver == 0) "Rien à trouver en raid" else "${pluriel(liste.objetsATrouver, "objet", "objets")} à trouver en raid",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val total = liste.lignes.sumOf { it.besoin }
                                if (total > 0) {
                                    Text("${liste.lignes.sumOf { it.apporte }} sur $total rapportés", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                                }
                            }
                            Text(
                                "Préparer le raid ›",
                                style = MaterialTheme.typography.labelLarge,
                                color = Signal.SurCouleur,
                                modifier = Modifier.background(Signal.Rouge, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
                ORDRE_GROUPES.forEach { g ->
                    val dedans = groupes[g].orEmpty()
                    if (dedans.isNotEmpty()) {
                        item(key = "groupe-${g.name}") { Etiquette(g.groupe, Signal.Vert, Modifier.padding(top = 6.dp)) }
                        items(dedans, key = { "objectif-${it.cle}" }) { objectif -> CarteObjectif(vm, objectif) }
                    }
                }
            }
        }
    }
}

/** Une des quatre tuiles : le nombre d'éléments en cours, en grand, et vers la liste complète. */
@Composable
private fun TuileEnCours(choix: ChoixEnCours, enCours: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = Signal.Surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.heightIn(min = 76.dp).semantics(mergeDescendants = true) {
            contentDescription = "${choix.bouton}, $enCours en cours"
        },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                "$enCours",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.ExtraBold,
                color = if (enCours == 0) Signal.TexteDiscret else Signal.Vert,
            )
            Text(choix.court, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun detailCout(cout: List<Quantite>, catalogue: Catalogue): String =
    cout.joinToString(" · ") { "${it.quantite} × ${catalogue.nomObjet(it.id)}" }

/** Ce que demande l'objectif, avec ce qui a déjà été rapporté : « Composants plastiques 20/50 · Alliage ARC 6/6 ». */
private fun detailAvancement(vm: CarnetViewModel, objectif: Objectif): String {
    val apporte = vm.apports[objectif.cle].orEmpty()
    return vm.planificateur.besoins(objectif).joinToString(" · ") {
        "${vm.catalogue.nomObjet(it.id)} ${minOf(apporte[it.id] ?: 0, it.quantite)}/${it.quantite}"
    }
}

/**
 * Un objectif en cours : ce qu'il demande, en pastilles (icône et « rapportés/voulus »). Il se termine tout
 * seul quand tout est rapporté ; « C'est fait » le valide s'il est fait autrement en jeu (ou s'il n'y a rien
 * à rapporter), avec « Annuler » ensuite : d'abord le balayage traverse la carte, puis l'étape suivante
 * prend sa place (ou l'objectif s'en va s'il était le dernier). La même carte dans l'onglet Objectifs et dans
 * l'onglet Raid : ce sont les mêmes objectifs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarteObjectif(vm: CarnetViewModel, objectif: Objectif) {
    val libelle = vm.planificateur.libelle(objectif)
    val besoins = vm.planificateur.besoins(objectif)
    val autres = (objectif as? Objectif.EtapeDeProjet)
        ?.let { vm.catalogue.projet(it.projetId)?.etapes?.getOrNull(it.etape)?.autresObjectifs } ?: 0
    val commercant = (objectif as? Objectif.QueteATerminer)?.let { vm.catalogue.quete(it.queteId)?.commercant }
    val apporte = vm.apports[objectif.cle].orEmpty()
    val voulu = besoins.sumOf { it.quantite }
    val rapporte = besoins.sumOf { minOf(apporte[it.id] ?: 0, it.quantite) }
    val portee = rememberCoroutineScope()
    val passage = remember(objectif.cle) { Animatable(1f) }
    val cloture = remember(objectif.cle) { Animatable(0f) }
    val opacite = remember(objectif.cle) { Animatable(1f) }
    var valide by remember(objectif.cle) { mutableStateOf(false) }
    // La dernière étape (ou le dernier niveau) a droit à sa fin : la clôture, après le balayage.
    val derniere = remember(objectif) { vm.planificateur.derniereEtape(objectif) }
    val anime = animationsActives()
    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = opacite.value }) {
        Box {
            Column(
                Modifier
                    .dessinBalayage(actif = { valide }, avancee = { passage.value })
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(libelle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val detail = commercant ?: if (besoins.isEmpty()) "Rien à rapporter\u00a0: valide-le quand c'est fait en jeu." else null
                        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux) }
                    }
                    if (voulu > 0) {
                        Text("$rapporte/$voulu", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Signal.Vert)
                    }
                }
                if (voulu > 0) {
                    Barre(rapporte.toFloat() / voulu, Signal.Vert, epaisseur = 4.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        besoins.forEach { b ->
                            val n = minOf(apporte[b.id] ?: 0, b.quantite)
                            PastilleObjet(vm, b.id, "$n/${b.quantite}", complet = n >= b.quantite)
                        }
                    }
                }
                if (autres > 0) {
                    Text("Et d'autres conditions à remplir en jeu.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = {
                            valide = true
                            portee.launch {
                                // Validé même si l'écran se ferme pendant l'animation.
                                try {
                                    passage.snapTo(0f)
                                    passage.animateTo(1f, tween(BALAYAGE_MS, easing = LinearEasing))
                                    if (derniere) {
                                        cloture.animateTo(1f, tween(CLOTURE_MS, easing = LinearEasing))
                                        // « TERMINÉ » reste affiché un moment avant que l'objectif s'en aille.
                                        if (anime) delay(TENUE_MS)
                                    }
                                    // Un petit fondu avant que la carte s'en aille.
                                    if (anime) opacite.animateTo(0f, tween(FONDU_MS, easing = LinearEasing))
                                } finally {
                                    vm.valider(objectif)
                                }
                            }
                        },
                        enabled = !valide,
                        border = BorderStroke(1.5.dp, Signal.Vert),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Signal.Vert),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.semantics { contentDescription = "C'est fait\u00a0: $libelle" },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("C'est fait", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (derniere && cloture.value > 0f) Cloture(cloture.value, nomFin(vm, objectif))
        }
    }
}

/** Durée de la clôture, après le balayage, quand on valide la dernière étape. */
private const val CLOTURE_MS = 1550

/** Durée pendant laquelle « TERMINÉ » reste affiché, après la clôture. */
private const val TENUE_MS = 1_000L

/** Fondu de la carte validée, juste avant qu'elle s'en aille. */
private const val FONDU_MS = 280

/** Les bandes du logo glissent en place, vives au départ et douces à l'arrivée. */
private val EasingBandes = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

/** Les quatre couleurs du logo, de gauche à droite. */
private val CouleursLogo = listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge)

/** Nom de ce qui se termine (l'établi ou le projet entier) et ce qui a été fait : « Grimper la montagne · 4 étapes sur 4 ». */
private fun nomFin(vm: CarnetViewModel, objectif: Objectif): String = when (objectif) {
    is Objectif.NiveauDEtabli -> vm.catalogue.etabli(objectif.etabliId)?.let { "${it.nom} · ${pluriel(it.niveaux.size, "niveau", "niveaux")} sur ${it.niveaux.size}" }
    is Objectif.EtapeDeProjet -> vm.catalogue.projet(objectif.projetId)?.let { "${it.nom} · ${pluriel(it.etapes.size, "étape", "étapes")} sur ${it.etapes.size}" }
    else -> null
} ?: vm.planificateur.libelle(objectif)

/**
 * La clôture, quand on valide la dernière étape, à la manière d'ARC Raiders : un bandeau sombre recouvre la
 * carte, « TERMINÉ » s'y resserre en vert au-dessus de ce qui a été fait ([fait]), une fine ligne verte
 * s'étire dessous, puis les quatre bandes du logo glissent en place une à une (la signature de l'appli).
 * [avancee] va de 0 à 1 sur [CLOTURE_MS] ; rien avec « Supprimer les animations » (la carte est validée d'un coup).
 */
@Composable
private fun BoxScope.Cloture(avancee: Float, fait: String) {
    fun etape(debut: Int, duree: Int) = ((avancee * CLOTURE_MS - debut) / duree).coerceIn(0f, 1f)
    val bande = EasingPassage.transform(etape(0, 360))
    val serre = etape(240, 560)
    val trait = FastOutSlowInEasing.transform(etape(1010, 500))
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                clipRect(right = size.width * bande) {
                    drawRect(Signal.Fond)
                    drawRect(Signal.Vert, size = Size(3.dp.toPx(), size.height))
                }
            }
            .clearAndSetSemantics { contentDescription = "Terminé\u00a0: $fait" },
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TERMINÉ",
                    style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (10f - 8f * FastOutSlowInEasing.transform(serre)).sp),
                    color = Signal.Vert,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer { alpha = serre },
                )
                Box(Modifier.padding(top = 4.dp).fillMaxWidth(trait.coerceAtLeast(0.001f)).height(1.5.dp).background(Signal.Vert.copy(alpha = if (trait > 0f) 1f else 0f)))
                Text(
                    fait,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Signal.TexteDoux,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = etape(360, 300) },
                )
            }
            // Les quatre bandes du logo, l'une après l'autre.
            Canvas(Modifier.size(width = 52.dp, height = 30.dp)) {
                val u = size.height / 20f
                CouleursLogo.forEachIndexed { i, couleur ->
                    val t = EasingBandes.transform(etape(660 + 100 * i, 320))
                    if (t > 0f) {
                        val x = i * 7.5f * u + (1f - t) * 14.dp.toPx()
                        val chemin = Path().apply {
                            moveTo(x, 20f * u)
                            lineTo(x + 5f * u, 20f * u)
                            lineTo(x + 12.2f * u, 0f)
                            lineTo(x + 7.2f * u, 0f)
                            close()
                        }
                        drawPath(chemin, couleur, alpha = t)
                    }
                }
            }
        }
    }
}

/** Un objet demandé, en pastille : son icône et « rapportés/voulus » (le nom est lu par TalkBack). */
@Composable
private fun PastilleObjet(vm: CarnetViewModel, objetId: String, texte: String, complet: Boolean) {
    val nom = vm.catalogue.nomObjet(objetId)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (complet) Signal.SurfaceVerte else Signal.Releve)
            .padding(start = 3.dp, end = 9.dp, top = 3.dp, bottom = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$nom\u00a0: $texte" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 24.dp)
        Text(texte, style = MaterialTheme.typography.labelMedium, color = if (complet) Signal.Vert else Signal.Texte)
    }
}

/** En-tête des écrans de choix : retour, titre et [resume] sur une ligne, puis une courte explication. */
@Composable
private fun EnTeteChoix(titre: String, resume: String, explication: String, onRetour: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 0.dp)) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour aux objectifs") }
            Column(Modifier.weight(1f)) {
                Text(titre, style = MaterialTheme.typography.headlineSmall)
                if (resume.isNotEmpty()) Text(resume, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
        Text(explication, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
    }
}

/**
 * Une piste de choix, sur toute la largeur : « — » (rien en cours), chaque niveau ou étape, puis « ✓ »
 * (terminé). Seul le choix est plein de vert : la piste reste sobre.
 */
@Composable
private fun Piste(nom: String, choix: List<Pair<String, String>>, choisi: Int?, termine: Boolean, aucun: () -> Unit, choisir: (Int) -> Unit, terminer: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SegmentPiste("—", "$nom\u00a0: rien en cours", choisi == null && !termine, onClick = aucun)
        choix.forEachIndexed { i, (court, description) ->
            SegmentPiste(court, "$nom\u00a0: $description", choisi == i, onClick = { choisir(i) })
        }
        SegmentPiste("✓", "$nom\u00a0: terminé", termine, onClick = terminer)
    }
}

@Composable
private fun RowScope.SegmentPiste(texte: String, description: String, choisi: Boolean, onClick: () -> Unit) {
    Surface(
        selected = choisi,
        onClick = onClick,
        color = if (choisi) Signal.Vert else Signal.Releve,
        contentColor = if (choisi) Signal.SurCouleur else Signal.TexteDoux,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.weight(1f).height(44.dp).semantics {
            contentDescription = description
            role = Role.RadioButton
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(texte, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** Durée du balayage d'une carte qu'on vient de passer à « Terminé ». */
private const val BALAYAGE_MS = 740

/** Durée du passage de la ligne verte, dans le balayage. */
private const val PASSAGE_MS = 680

/** Durée du repli de la carte en une ligne, après le balayage. */
private const val REPLI_MS = 320

/** Le passage de la ligne : lent au départ et à l'arrivée. */
private val EasingPassage = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/** Teinte verte du fond pendant le balayage. */
private const val TEINTE = 0.12f

/**
 * Le balayage, dessiné sur un élément tant que [actif] : une fine ligne verte le traverse de gauche à droite
 * selon [avancee] (de 0 à 1, sur [BALAYAGE_MS]) et le fond se teinte de vert derrière elle ; ensuite, la teinte
 * s'efface selon [effacement] (de 1 à 0). Lu seulement au dessin : rien ne se recompose pendant l'animation.
 */
private fun Modifier.dessinBalayage(actif: () -> Boolean, avancee: () -> Float, effacement: () -> Float = { 0f }): Modifier = drawWithContent {
    // Avancée (de 0 à 1) d'une étape du balayage, de [debut] à [debut] + [duree] millisecondes.
    fun etape(debut: Int, duree: Int) = ((avancee() * BALAYAGE_MS - debut) / duree).coerceIn(0f, 1f)
    val enCours = actif()
    val teinte = if (enCours) TEINTE * etape(120, 480) else TEINTE * effacement()
    if (teinte > 0f) drawRect(Signal.Vert.copy(alpha = teinte))
    drawContent()
    if (enCours && avancee() < 1f) {
        val trainee = 56.dp.toPx()
        val trait = 2.dp.toPx()
        val x = (size.width + trainee) * EasingPassage.transform(etape(0, PASSAGE_MS))
        drawRect(
            Brush.horizontalGradient(listOf(Signal.Vert.copy(alpha = 0f), Signal.Vert.copy(alpha = 0.16f)), startX = x - trainee, endX = x),
            topLeft = Offset(x - trainee, 0f),
            size = Size(trainee, size.height),
        )
        drawRect(Signal.Vert.copy(alpha = 0.75f), topLeft = Offset(x - trait, 0f), size = Size(trait, size.height))
    }
}

/**
 * Carte d'un établi ou d'un projet : son nom, son état ([etat] : « Niveau 2 en cours »), puis [contenu] (sa
 * piste). Terminé, elle se réduit à une ligne sobre : le nom, « ✓ Terminé » et « Modifier », qui rouvre la
 * piste pour le remettre en cours.
 *
 * Quand elle passe à « Terminé » sous les yeux du joueur, une fine ligne verte la balaie de gauche à droite
 * (le balayage) et le fond se teinte de vert derrière elle ; puis la carte se replie en sa ligne, la teinte
 * s'efface et « ✓ Terminé » apparaît. Rien ne bouge à l'ouverture de la liste ; avec « Supprimer les
 * animations » d'Android, la carte se replie d'un coup.
 */
@Composable
private fun CarteChoix(nom: String, termine: Boolean, etat: String?, contenu: @Composable ColumnScope.() -> Unit) {
    val balayage = remember { Animatable(1f) }
    val effacement = remember { Animatable(0f) }
    var etaitTermine by remember { mutableStateOf(termine) }
    var ouvert by rememberSaveable(nom) { mutableStateOf(false) }
    LaunchedEffect(termine) {
        if (termine && !etaitTermine) {
            ouvert = false
            balayage.snapTo(0f)
            balayage.animateTo(1f, tween(BALAYAGE_MS, easing = LinearEasing))
            etaitTermine = true
            effacement.snapTo(1f)
            effacement.animateTo(0f, tween(REPLI_MS))
        }
        etaitTermine = termine
    }
    // Balayage en cours : tout juste terminée, la carte reste dépliée jusqu'à la fin du passage de la ligne.
    val balaie = termine && (!etaitTermine || balayage.isRunning)
    // Avancée du balayage (1 : rien à animer) ; tout juste terminée, elle part de 0 avant même que l'effet démarre.
    fun avancee() = if (termine && !etaitTermine && !balayage.isRunning) 0f else balayage.value
    Surface(
        color = Signal.Surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(REPLI_MS)),
    ) {
        Box(Modifier.dessinBalayage(actif = { balaie }, avancee = ::avancee, effacement = { effacement.value })) {
            if (termine && !balaie) {
                Column {
                    Row(
                        Modifier.heightIn(min = 52.dp).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            "✓ Terminé",
                            style = MaterialTheme.typography.labelLarge,
                            color = Signal.Vert,
                            modifier = Modifier.graphicsLayer { alpha = 1f - effacement.value },
                        )
                        TextButton(
                            onClick = { ouvert = !ouvert },
                            modifier = Modifier.semantics { contentDescription = if (ouvert) "Fermer\u00a0: $nom" else "Modifier\u00a0: $nom" },
                        ) { Text(if (ouvert) "Fermer" else "Modifier", color = Signal.TexteDoux) }
                    }
                    if (ouvert) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenu() }
                    }
                }
            } else {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (!termine) Etiquette(etat ?: "Pas en cours", if (etat != null) Signal.Vert else Signal.TexteDiscret)
                    }
                    contenu()
                }
            }
        }
    }
}

/**
 * Mes quêtes en cours : le joueur coche celles qui sont en cours dans son journal de quêtes. Toutes les
 * autres sont faites d'office, sauf ce qui vient après elles (la suite s'ajoutera toute seule).
 */
@Composable
fun EcranQuetesEnCours(vm: CarnetViewModel, onRetour: () -> Unit) {
    val histoire = vm.catalogue.donnees.quetes.filter { it.histoire }
    var choix by rememberSaveable { mutableStateOf(vm.suivi.quetesEnCours(vm.progression).map { it.id }.toSet()) }
    var recherche by rememberSaveable { mutableStateOf("") }
    val cle = Catalogue.normaliser(recherche)
    // Ordre figé à l'ouverture (cochées d'abord) : une quête cochée ne saute pas sous le doigt.
    val ordre = remember { histoire.sortedWith(compareBy({ it.id !in choix }, { it.commercant.orEmpty() }, { it.nom })) }
    val affichees = ordre.filter { cle.isBlank() || Catalogue.normaliser(it.nom).contains(cle) || Catalogue.normaliser(it.commercant.orEmpty()).contains(cle) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = PLACE_BOUTON_FIXE),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnTeteChoix(
                        ChoixEnCours.QUETES.titre,
                        "${pluriel(choix.size, "quête cochée", "quêtes cochées")}",
                        "Coche les quêtes en cours dans ton journal de quêtes. Les autres sont considérées comme faites\u00a0; " +
                            "celles qui viennent après s'ajouteront toutes seules.",
                        onRetour,
                    )
                    OutlinedTextField(
                        value = recherche,
                        onValueChange = { recherche = it },
                        label = { Text("Chercher une quête ou un commerçant") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(affichees, key = { it.id }) { quete ->
                val coche = quete.id in choix
                Surface(
                    onClick = { choix = if (coche) choix - quete.id else choix + quete.id },
                    color = if (coche) Signal.SurfaceVerte else Signal.Surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = coche,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Signal.Vert, checkmarkColor = Signal.SurCouleur),
                        )
                        Box(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(quete.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            val detail = listOfNotNull(quete.commercant, detailCout(quete.objets, vm.catalogue).takeIf { it.isNotEmpty() })
                            if (detail.isNotEmpty()) {
                                Text(detail.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
                            }
                        }
                    }
                }
            }
        }
        BoutonFixe(
            "Valider · ${pluriel(choix.size, "quête", "quêtes")}",
            Signal.Vert,
            onClick = {
                vm.indiquerQuetesEnCours(choix)
                onRetour()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Mes ateliers en cours : pour chaque établi de l'Atelier, le niveau en cours d'amélioration (les précédents
 * sont faits), rien ou « Terminé » (tous ses niveaux sont faits), sur une piste. Chaque choix s'applique
 * tout de suite ; l'ordre ne change jamais sous le doigt.
 */
@Composable
fun EcranAteliersEnCours(vm: CarnetViewModel, onRetour: () -> Unit) {
    val etablis = vm.catalogue.donnees.etablis.sortedBy { it.nom }
    val enCours = etablis.count { vm.suivi.niveauEnCours(vm.progression, it) != null }
    val termines = etablis.count { vm.suivi.etabliTermine(vm.progression, it) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            EnTeteChoix(
                ChoixEnCours.ATELIERS.titre,
                "$enCours en cours · ${pluriel(termines, "terminé", "terminés")}",
                "Touche le niveau que tu améliores. Quand tout est rapporté, le suivant s'ajoute tout seul.",
                onRetour,
            )
        }
        items(etablis, key = { it.id }) { etabli -> ChoixEtabli(vm, etabli) }
    }
}

@Composable
private fun ChoixEtabli(vm: CarnetViewModel, etabli: Etabli) {
    val enCours = vm.suivi.niveauEnCours(vm.progression, etabli)
    val termine = vm.suivi.etabliTermine(vm.progression, etabli)
    val niveaux = etabli.niveaux.sortedBy { it.niveau }
    CarteChoix(etabli.nom, termine, enCours?.let { "Niveau $it en cours" }) {
        Piste(
            etabli.nom,
            niveaux.map { "${it.niveau}" to "niveau ${it.niveau} en cours" },
            choisi = niveaux.indexOfFirst { it.niveau == enCours }.takeIf { it >= 0 },
            termine = termine,
            aucun = { vm.indiquerNiveauEnCours(etabli, null) },
            choisir = { vm.indiquerNiveauEnCours(etabli, niveaux[it].niveau) },
            terminer = { vm.indiquerEtabliTermine(etabli) },
        )
        enCours?.let { niveau ->
            val cout = etabli.niveaux.firstOrNull { it.niveau == niveau }?.cout.orEmpty()
            Text(
                "Il faut\u00a0: ${detailAvancement(vm, Objectif.NiveauDEtabli(etabli.id, niveau)).ifEmpty { detailCout(cout, vm.catalogue) }}",
                style = MaterialTheme.typography.bodySmall,
                color = Signal.TexteDoux,
            )
        }
    }
}

/**
 * Mes projets (ou mes Expéditions) en cours : pour chacun, l'étape en cours (les précédentes sont faites),
 * « Aucune » ou « Terminé » (toutes ses étapes sont faites). Chaque choix s'applique tout de suite.
 */
@Composable
fun EcranProjetsEnCours(vm: CarnetViewModel, expeditions: Boolean, onRetour: () -> Unit) {
    val choix = if (expeditions) ChoixEnCours.EXPEDITIONS else ChoixEnCours.PROJETS
    val projets = vm.catalogue.donnees.projets.filter { it.expedition == expeditions }
    val enCours = projets.count { vm.suivi.etapeEnCours(vm.progression, it) != null }
    val termines = projets.count { vm.suivi.projetTermine(vm.progression, it) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            EnTeteChoix(
                choix.titre,
                "$enCours en cours · ${pluriel(termines, "terminé", "terminés")}",
                "Touche l'étape en cours. Quand tout est rapporté, la suivante s'ajoute toute seule.",
                onRetour,
            )
        }
        items(projets, key = { it.id }) { projet -> ChoixProjet(vm, projet) }
    }
}

@Composable
private fun ChoixProjet(vm: CarnetViewModel, projet: Projet) {
    val enCours = vm.suivi.etapeEnCours(vm.progression, projet)
    val termine = vm.suivi.projetTermine(vm.progression, projet)
    CarteChoix(projet.nom, termine, enCours?.let { "Étape ${it + 1} en cours" }) {
        Piste(
            projet.nom,
            projet.etapes.indices.map { "${it + 1}" to "${vm.planificateur.nomEtape(projet, it)} en cours" },
            choisi = enCours,
            termine = termine,
            aucun = { vm.indiquerEtapeEnCours(projet, null) },
            choisir = { vm.indiquerEtapeEnCours(projet, it) },
            terminer = { vm.indiquerProjetTermine(projet) },
        )
        enCours?.let { etape ->
            val objectif = Objectif.EtapeDeProjet(projet.id, etape)
            Text(vm.planificateur.nomEtape(projet, etape), style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
            val detail = detailAvancement(vm, objectif)
            if (detail.isNotEmpty()) Text("Il faut\u00a0: $detail", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            if (projet.etapes[etape].autresObjectifs > 0) {
                Text("Et d'autres conditions à remplir en jeu.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
    }
}
