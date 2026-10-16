package io.github.slaynaw.carnetduraider.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import io.github.slaynaw.carnetduraider.core.Rarete
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Icônes officielles des objets, tirées des fichiers du jeu et embarquées dans l'appli
 * (`assets/objets/<identifiant>.webp`, produites par `scripts/importer_donnees_jeu.py`) : elles
 * s'affichent sans connexion. Les dernières utilisées restent en mémoire.
 */
private object Icones {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun enMemoire(id: String): ImageBitmap? = cache.get(id)

    suspend fun charger(context: Context, id: String): ImageBitmap? = cache.get(id) ?: withContext(Dispatchers.IO) {
        try {
            context.assets.open("objets/$id.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()?.also { cache.put(id, it) }
        } catch (e: IOException) {
            null // pas d'icône pour cet objet : la tuile reste vide, aux couleurs de sa rareté
        }
    }
}

/** Icône d'un objet sur une tuile aux couleurs de sa rareté, comme dans l'inventaire du jeu. */
@Composable
fun IconeObjet(objetId: String, rarete: Rarete?, taille: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val image by produceState(Icones.enMemoire(objetId), objetId) { value = Icones.charger(context, objetId) }
    val couleur = Signal.rarete(rarete)
    val forme = RoundedCornerShape(taille * 0.22f)
    Box(
        modifier
            .size(taille)
            .clip(forme)
            .background(Brush.verticalGradient(listOf(couleur.copy(alpha = 0.08f), couleur.copy(alpha = 0.32f))))
            .border(taille * 0.025f, couleur.copy(alpha = 0.6f), forme),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize().padding(taille * 0.08f)) }
    }
}
