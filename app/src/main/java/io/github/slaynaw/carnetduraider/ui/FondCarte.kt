package io.github.slaynaw.carnetduraider.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Capture en jeu d'un lieu marquant de la carte (`assets/fonds/<id>.webp`), ou null si elle n'en a pas. */
private suspend fun chargerFond(context: Context, carteId: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        context.assets.open("fonds/$carteId.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (e: IOException) {
        null
    }
}

/** La capture en jeu de la carte [carteId] (null le temps de la charger, ou si elle n'en a pas). */
@Composable
fun rememberFondCarte(carteId: String?): ImageBitmap? {
    val context = LocalContext.current
    val fond by produceState<ImageBitmap?>(null, carteId) { value = carteId?.let { chargerFond(context, it) } }
    return fond
}

/**
 * La capture en jeu en fond d'une carte mise en avant (« Meilleure carte », « Ton prochain raid »), assombrie
 * à gauche pour que le texte clair posé dessus reste lisible (« ombre latérale »).
 */
@Composable
fun BoxScope.FondEnJeu(image: ImageBitmap) {
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    Box(
        Modifier.matchParentSize().background(
            Brush.horizontalGradient(
                0f to Signal.Fond.copy(alpha = 0.94f),
                0.52f to Signal.Fond.copy(alpha = 0.72f),
                1f to Signal.Fond.copy(alpha = 0.1f),
            ),
        ),
    )
}
