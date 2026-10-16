package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/** Calendrier officiel des conditions de la carte, publié par Embark sur son site. */
private const val CALENDRIER_OFFICIEL = "https://arcraiders.com/map-conditions"

@Composable
fun EcranCartes(vm: CarnetViewModel) {
    val donnees = vm.catalogue.donnees
    val liens = LocalUriHandler.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bandeau("Cartes", Signal.Cyan)
                Text("Conditions de la carte", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Le calendrier officiel d'Embark indique les conditions en cours et à venir, selon ta région. " +
                        "Les minuteurs et les alertes arrivent dans une prochaine version.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                Button(
                    onClick = { runCatching { liens.openUri(CALENDRIER_OFFICIEL) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Signal.Cyan, contentColor = Signal.SurCouleur),
                ) {
                    Text("Ouvrir le calendrier officiel")
                }
                Text("Cartes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
            }
        }
        items(donnees.cartes, key = { "carte-${it.id}" }) { carte -> Ligne(carte.nom) }
        item { Text("Conditions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp)) }
        items(donnees.conditions.sortedWith(compareBy({ !it.majeure }, { it.nom })), key = { "condition-${it.id}" }) { condition ->
            Ligne(condition.nom)
        }
    }
}

@Composable
private fun Ligne(nom: String) {
    Surface(color = Signal.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            nom,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
