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

/** Heure actuelle (millisecondes), rafraîchie toutes les [intervalle] millisecondes (minuteurs : 1 s). */
@Composable
fun maintenant(intervalle: Long = 30_000): Long {
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalle) {
        while (true) {
            // Aligné sur la seconde (ou l'intervalle) : tous les minuteurs changent ensemble.
            delay(intervalle - System.currentTimeMillis() % intervalle)
            t = System.currentTimeMillis()
        }
    }
    return t
}

/** Minuteur : « 4:05 », « 1:02:03 » (heures, minutes, secondes ; jamais négatif), « 2 j 5 h » au-delà d'un jour. */
fun chrono(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    val h = s / 3600
    if (h >= 24) return "${h / 24}\u00a0j ${h % 24}\u00a0h"
    val m = (s % 3600) / 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
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
