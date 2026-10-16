package io.github.slaynaw.carnetduraider.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Heure actuelle (millisecondes), rafraîchie toutes les 30 secondes pour les minuteurs. */
@Composable
fun maintenant(): Long {
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            t = System.currentTimeMillis()
        }
    }
    return t
}

private val heure = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val jour = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/** « 16:00 », « demain 02:00 », « ven. 17 oct. 16:00 » (heure du téléphone). */
fun heureLocale(instant: Long, maintenant: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(instant).atZone(zone)
    val aujourdhui = Instant.ofEpochMilli(maintenant).atZone(zone).toLocalDate()
    val prefixe = when (date.toLocalDate()) {
        aujourdhui -> ""
        aujourdhui.plusDays(1) -> "demain "
        else -> date.format(jour) + " "
    }
    return prefixe + date.format(heure)
}

/** « 25 min », « 2 h 10 », « 3 h » ; jamais moins d'une minute. */
fun duree(ms: Long): String {
    val minutes = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m\u00a0min"
        m == 0L -> "$h\u00a0h"
        else -> "$h\u00a0h\u00a0${m.toString().padStart(2, '0')}"
    }
}
