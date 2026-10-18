package io.github.slaynaw.carnetduraider.alertes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.slaynaw.carnetduraider.MainActivity
import io.github.slaynaw.carnetduraider.Memoire
import io.github.slaynaw.carnetduraider.R
import io.github.slaynaw.carnetduraider.calendrier.GestionnaireCalendrier
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.calendrier.Alerte
import io.github.slaynaw.carnetduraider.core.calendrier.Alertes
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.ReglagesAlertes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Alertes des conditions de la carte : quelques minutes avant le début d'une condition suivie,
 * une notification, même appli fermée et sans connexion (calendrier officiel gardé sur le
 * téléphone). Un seul réveil est programmé à la fois, celui de la prochaine alerte.
 */
class GestionnaireAlertes(
    context: Context,
    private val portee: CoroutineScope,
    private val calendrier: GestionnaireCalendrier,
    private val catalogue: () -> Catalogue,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("alertes", Context.MODE_PRIVATE)
    private val memoire = Memoire(app)
    private val verrou = Mutex()
    private val _reglages = MutableStateFlow(lireReglages())
    val reglages: StateFlow<ReglagesAlertes> = _reglages.asStateFlow()

    fun suivre(conditionId: String, suivie: Boolean) = changer(
        _reglages.value.copy(conditions = if (suivie) _reglages.value.conditions + conditionId else _reglages.value.conditions - conditionId),
    )

    fun changerAvance(minutes: Int) = changer(_reglages.value.copy(avanceMinutes = minutes))

    private fun changer(nouveaux: ReglagesAlertes) {
        _reglages.value = nouveaux
        prefs.edit()
            .putStringSet(CONDITIONS, nouveaux.conditions)
            .putInt(AVANCE, nouveaux.avanceMinutes)
            .apply()
        replanifier()
    }

    /** Relit les réglages (après la restauration d'une sauvegarde) et recalcule le prochain réveil. */
    fun recharger() {
        _reglages.value = lireReglages()
        replanifier()
    }

    /** Recalcule le prochain réveil (réglages, région ou calendrier changés). */
    fun replanifier() {
        portee.launch { traiter() }
    }

    /** Vrai si Android laisse l'appli afficher des notifications. */
    fun notificationsAutorisees(): Boolean = NotificationManagerCompat.from(app).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Vrai si les réveils peuvent être à la minute près (sinon Android peut les décaler de quelques minutes). */
    fun reveilsPrecis(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmes().canScheduleExactAlarms()

    /**
     * Affiche les alertes dont l'heure est venue, puis programme le réveil suivant. Appelé par le
     * réveil lui-même, au démarrage du téléphone, et quand les réglages ou le calendrier changent.
     */
    suspend fun traiter(maintenant: Long = System.currentTimeMillis()) = verrou.withLock {
        val reglages = _reglages.value
        if (reglages.conditions.isEmpty()) {
            annulerReveil()
            return@withLock
        }
        var donnees = calendrier.donneesDisponibles()
        val region = memoire.region
        // Calendrier bientôt épuisé : tentative de relecture (au plus une par heure, sans bloquer plus de 25 s).
        val bientotEpuise = donnees == null || Calendrier(donnees, catalogue()).connuJusqua(region) < maintenant + 12 * Calendrier.UNE_HEURE
        if (bientotEpuise && maintenant - (donnees?.luLe ?: 0) > Calendrier.UNE_HEURE) {
            withTimeoutOrNull(25_000) { calendrier.actualiser() }
            donnees = calendrier.donneesDisponibles()
        }
        if (donnees == null) {
            annulerReveil()
            return@withLock
        }
        val alertes = Alertes.aVenir(Calendrier(donnees, catalogue()), region, reglages, maintenant)
        var deja = Alertes.nettoyer(prefs.getStringSet(DEJA_MONTREES, emptySet()).orEmpty(), maintenant)
        val aMontrer = Alertes.aMontrer(alertes, maintenant, deja)
        if (aMontrer.isNotEmpty()) {
            notifier(aMontrer, maintenant)
            deja = deja + aMontrer.map { it.cle }
        }
        prefs.edit().putStringSet(DEJA_MONTREES, deja).apply()

        val reveil = Alertes.prochainReveil(alertes, maintenant, deja)
            // Plus rien dans le calendrier connu : réveil pour le relire avant qu'il ne s'épuise.
            ?: (Calendrier(donnees, catalogue()).connuJusqua(region) - 6 * Calendrier.UNE_HEURE).coerceAtLeast(maintenant + Calendrier.UNE_HEURE)
        programmer(reveil)
    }

    private fun notifier(alertes: List<Alerte>, maintenant: Long) {
        val permis = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permis || !NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            Log.i(JOURNAL, "${alertes.size} alerte(s) non affichée(s) : notifications refusées")
            return
        }
        creerCanal()
        val heure = SimpleDateFormat("HH:mm", Locale.FRENCH)
        val ouvrir = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.ONGLET, "CARTES").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Une notification par heure de début (plusieurs cartes peuvent commencer en même temps).
        alertes.groupBy { it.creneau.debut }.forEach { (debut, groupe) ->
            val minutes = ((debut - maintenant + 59_999) / 60_000).coerceAtLeast(1)
            val titre = if (groupe.size == 1) {
                "${groupe[0].creneau.condition} dans $minutes\u00a0min"
            } else {
                "${groupe.size} conditions dans $minutes\u00a0min"
            }
            val texte = groupe.joinToString("\n") { "${it.creneau.condition} · ${it.creneau.carte}" } + "\nÀ ${heure.format(Date(debut))}"
            val notification = NotificationCompat.Builder(app, CANAL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(titre)
                .setContentText(texte.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(ouvrir)
                .setAutoCancel(true)
                .setTimeoutAfter((groupe.maxOf { it.creneau.fin } - maintenant).coerceAtLeast(60_000))
                .build()
            try {
                NotificationManagerCompat.from(app).notify((debut / 60_000).toInt(), notification)
            } catch (e: SecurityException) {
                Log.w(JOURNAL, "notification refusée", e)
            }
        }
    }

    private fun creerCanal() {
        val canal = NotificationChannel(CANAL, "Conditions de la carte", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Prévient quelques minutes avant le début des conditions suivies."
        }
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun alarmes(): AlarmManager = app.getSystemService(AlarmManager::class.java)

    private fun intentionReveil(): PendingIntent = PendingIntent.getBroadcast(
        app,
        0,
        Intent(app, RecepteurAlertes::class.java).setAction(RecepteurAlertes.REVEIL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun programmer(quand: Long) {
        val alarmes = alarmes()
        val precis = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmes.canScheduleExactAlarms()
        try {
            if (precis) {
                alarmes.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, intentionReveil())
            } else {
                alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, intentionReveil())
            }
        } catch (e: SecurityException) {
            // Autorisation des réveils précis retirée entre-temps : réveil à quelques minutes près.
            alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, intentionReveil())
        }
        Log.i(JOURNAL, "prochain réveil dans ${(quand - System.currentTimeMillis()) / 60_000} min")
    }

    private fun annulerReveil() = alarmes().cancel(intentionReveil())

    private fun lireReglages() = ReglagesAlertes(
        conditions = prefs.getStringSet(CONDITIONS, emptySet()).orEmpty().toSet(),
        avanceMinutes = prefs.getInt(AVANCE, ReglagesAlertes().avanceMinutes),
    )

    private companion object {
        const val JOURNAL = "Alertes"
        const val CANAL = "conditions"
        const val CONDITIONS = "conditions"
        const val AVANCE = "avance"
        const val DEJA_MONTREES = "dejaMontrees"
    }
}
