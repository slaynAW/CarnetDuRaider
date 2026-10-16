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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
fun CarnetApp(vm: CarnetViewModel = viewModel()) {
    var onglet by rememberSaveable { mutableStateOf(Onglet.ACCUEIL) }
    var objetOuvert by rememberSaveable { mutableStateOf<String?>(null) }
    var ajoutObjectif by rememberSaveable { mutableStateOf(false) }
    var recherche by rememberSaveable { mutableStateOf("") }

    fun aller(cible: Onglet) {
        objetOuvert = null
        ajoutObjectif = false
        onglet = cible
    }

    // Retour : d'abord l'écran ouvert par-dessus l'onglet, puis l'accueil.
    BackHandler(enabled = objetOuvert != null || ajoutObjectif || onglet != Onglet.ACCUEIL) {
        when {
            objetOuvert != null -> objetOuvert = null
            ajoutObjectif -> ajoutObjectif = false
            else -> onglet = Onglet.ACCUEIL
        }
    }

    Scaffold(
        containerColor = Signal.Fond,
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
                id != null -> EcranFiche(vm, id, onRetour = { objetOuvert = null })
                ajoutObjectif -> EcranAjoutObjectif(vm, onRetour = { ajoutObjectif = false })
                else -> when (onglet) {
                    Onglet.ACCUEIL -> EcranAccueil(vm, aller = ::aller)
                    Onglet.OBJETS -> EcranObjets(vm, recherche, { recherche = it }, ouvrir = { objetOuvert = it })
                    Onglet.CARTES -> EcranCartes(vm)
                    Onglet.PROGRESSION -> EcranProgression(
                        vm,
                        ajouterObjectif = { ajoutObjectif = true },
                        ouvrir = { objetOuvert = it },
                    )
                }
            }
        }
    }
}
