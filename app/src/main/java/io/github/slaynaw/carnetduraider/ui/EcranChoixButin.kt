package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.ButinRecherche
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/**
 * Ajouter un objet au butin recherché : tout le catalogue, avec une recherche. Toucher un objet l'ajoute
 * (un de plus s'il y est déjà) et ramène à l'onglet Raid, où l'on règle la quantité voulue.
 */
@Composable
fun EcranChoixButin(vm: CarnetViewModel, onRetour: () -> Unit) {
    var recherche by rememberSaveable { mutableStateOf("") }
    val resultats = remember(recherche) {
        if (recherche.isBlank()) vm.catalogue.tous else vm.catalogue.rechercher(recherche, limite = 200)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Bandeau("Raid", Signal.Rouge)
                }
                Text(ButinRecherche.TITRE, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Choisis un objet à rapporter. Il s'ajoute à ce qu'il te faut et se décompte au retour de raid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    placeholder = { Text("Cherche un objet…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Rouge) },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal.Rouge, cursorColor = Signal.Rouge),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (resultats.isEmpty()) {
                    Text("Aucun objet ne correspond.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                }
            }
        }
        items(resultats, key = { it.id }) { objet ->
            val deja = vm.butinRecherche[objet.id]
            Surface(
                onClick = {
                    vm.ajouterAuButin(objet.id)
                    onRetour()
                },
                color = Signal.Surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconeObjet(objet.id, objet.rarete, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(objet.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val details = listOfNotNull(objet.rarete?.libelle, objet.categorie).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                    }
                    if (deja != null) Text("Déjà\u00a0: $deja", style = MaterialTheme.typography.labelMedium, color = Signal.Rouge)
                }
            }
        }
    }
}
