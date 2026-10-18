package io.github.slaynaw.carnetduraider.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Les quatre onglets, chacun avec la couleur de son domaine. */
enum class Onglet(val titre: String, val icone: ImageVector, val couleur: Color) {
    ACCUEIL("Accueil", Icons.Filled.Home, Signal.Texte),
    OBJETS("Objets", Icons.Filled.Search, Signal.Or),
    CARTES("Cartes", Icons.Filled.Place, Signal.Cyan),
    PROGRESSION("Progression", Icons.Filled.ShoppingCart, Signal.Vert),
}

@Composable
fun CarnetApp(ongletDemande: Onglet? = null, demandeTraitee: () -> Unit = {}, vm: CarnetViewModel = viewModel()) {
    var onglet by rememberSaveable { mutableStateOf(Onglet.ACCUEIL) }
    var objetOuvert by rememberSaveable { mutableStateOf<String?>(null) }
    var ajoutObjectif by rememberSaveable { mutableStateOf(false) }
    var recherche by rememberSaveable { mutableStateOf("") }
    var reglages by rememberSaveable { mutableStateOf(false) }
    var choixQuetes by rememberSaveable { mutableStateOf(false) }
    var retourRaid by rememberSaveable { mutableStateOf(false) }
    var preparerRaid by rememberSaveable { mutableStateOf(false) }
    var planOuvert by rememberSaveable { mutableStateOf<String?>(null) }
    var planRecherche by rememberSaveable { mutableStateOf("") }
    val calendrier by vm.calendrier.etat.collectAsStateWithLifecycle()
    val calendrierOfficiel = remember(calendrier.donnees) { calendrier.donnees?.let(vm::calendrierOfficiel) }
    val miseAJour by vm.miseAJour.etat.collectAsStateWithLifecycle()
    var dialogueMiseAJour by rememberSaveable { mutableStateOf(false) }
    var versionProposee by rememberSaveable { mutableStateOf("") }

    // À chaque retour dans l'appli : recherche d'une mise à jour (au plus toutes les 12 h) et
    // relecture du calendrier officiel des conditions de la carte (au plus toutes les heures).
    LifecycleStartEffect(Unit) {
        vm.miseAJour.rechercherSiNecessaire()
        vm.calendrier.actualiserSiNecessaire()
        onStopOrDispose { }
    }

    // Nouvelle version : proposée d'elle-même une fois (sauf « Plus tard » dans les dernières 24 h).
    LaunchedEffect(miseAJour.nouvelle?.version, miseAJour.reportee) {
        val version = miseAJour.nouvelle?.version ?: return@LaunchedEffect
        if (!miseAJour.reportee && versionProposee != version) {
            versionProposee = version
            dialogueMiseAJour = true
        }
    }

    fun aller(cible: Onglet) {
        objetOuvert = null
        ajoutObjectif = false
        reglages = false
        choixQuetes = false
        retourRaid = false
        preparerRaid = false
        planOuvert = null
        onglet = cible
    }

    // Ouverture depuis une notification : directement sur l'onglet demandé.
    LaunchedEffect(ongletDemande) {
        ongletDemande?.let {
            aller(it)
            demandeTraitee()
        }
    }

    // Retour : d'abord l'écran ouvert par-dessus l'onglet, puis l'accueil.
    BackHandler(enabled = objetOuvert != null || ajoutObjectif || choixQuetes || retourRaid || preparerRaid || planOuvert != null || reglages || onglet != Onglet.ACCUEIL) {
        when {
            planOuvert != null -> planOuvert = null
            objetOuvert != null -> objetOuvert = null
            ajoutObjectif -> ajoutObjectif = false
            choixQuetes -> choixQuetes = false
            retourRaid -> retourRaid = false
            preparerRaid -> preparerRaid = false
            reglages -> reglages = false
            else -> onglet = Onglet.ACCUEIL
        }
    }

    Scaffold(
        containerColor = Signal.Fond,
        topBar = {
            EnTete(reglagesOuverts = reglages) {
                objetOuvert = null
                ajoutObjectif = false
                choixQuetes = false
                retourRaid = false
                preparerRaid = false
                planOuvert = null
                reglages = true
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Signal.Surface) {
                Onglet.entries.forEach { o ->
                    NavigationBarItem(
                        selected = onglet == o,
                        onClick = { aller(o) },
                        icon = { Icon(o.icone, contentDescription = null) },
                        label = { Text(o.titre) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Signal.SurCouleur,
                            selectedTextColor = Signal.Texte,
                            indicatorColor = o.couleur,
                            unselectedIconColor = Signal.TexteDoux,
                            unselectedTextColor = Signal.TexteDoux,
                        ),
                    )
                }
            }
        },
    ) { marges ->
        Box(Modifier.fillMaxSize().padding(marges)) {
            val id = objetOuvert
            when {
                // Le plan passe devant la fiche : ouvert depuis une zone de butin, le retour ramène à la fiche.
                planOuvert != null -> EcranPlan(vm, planOuvert!!, planRecherche, onRetour = { planOuvert = null })
                id != null -> EcranFiche(
                    vm,
                    id,
                    onRetour = { objetOuvert = null },
                    ouvrir = { objetOuvert = it },
                    ouvrirZone = { carte, zone ->
                        planRecherche = zone
                        planOuvert = carte
                    },
                )
                ajoutObjectif -> EcranAjoutObjectif(vm, onRetour = { ajoutObjectif = false })
                choixQuetes -> EcranQuetesEnCours(vm, onRetour = { choixQuetes = false })
                retourRaid -> EcranRetourRaid(vm, onRetour = { retourRaid = false })
                preparerRaid -> EcranPreparerRaid(
                    vm,
                    onRetour = { preparerRaid = false },
                    ouvrir = { objetOuvert = it },
                    ouvrirZone = { carte, zone ->
                        planRecherche = zone
                        planOuvert = carte
                    },
                )
                reglages -> EcranReglages(
                    vm,
                    vm.miseAJour,
                    miseAJour,
                    afficherMiseAJour = { dialogueMiseAJour = true },
                    onRetour = { reglages = false },
                )
                else -> when (onglet) {
                    Onglet.ACCUEIL -> EcranAccueil(
                        vm,
                        aller = ::aller,
                        nouvelleVersion = miseAJour.nouvelle?.version,
                        calendrier = calendrierOfficiel,
                        retourDeRaid = { retourRaid = true },
                        preparerRaid = { preparerRaid = true },
                        afficherMiseAJour = { dialogueMiseAJour = true },
                    )
                    Onglet.OBJETS -> EcranObjets(vm, recherche, { recherche = it }, ouvrir = { objetOuvert = it })
                    Onglet.CARTES -> EcranCartes(vm, ouvrirPlan = {
                        planRecherche = ""
                        planOuvert = it
                    })
                    Onglet.PROGRESSION -> EcranProgression(
                        vm,
                        ajouterObjectif = { ajoutObjectif = true },
                        choisirQuetes = { choixQuetes = true },
                        retourDeRaid = { retourRaid = true },
                        preparerRaid = { preparerRaid = true },
                        ouvrir = { objetOuvert = it },
                    )
                }
            }
        }
    }

    if (dialogueMiseAJour && miseAJour.nouvelle != null) {
        DialogueMiseAJour(
            miseAJour,
            plusTard = {
                dialogueMiseAJour = false
                vm.miseAJour.reporter()
            },
            installer = vm.miseAJour::installer,
        )
    }
}
