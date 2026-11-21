package pl.shuffletones

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Serce aplikacji. System powiadamia tę usługę o każdym nowym powiadomieniu innych aplikacji
 * (po przyznaniu "Dostępu do powiadomień"). Jeśli dla aplikacji jest reguła – losujemy dźwięk
 * z przypisanej playlisty i go odtwarzamy.
 *
 * Uwaga: Android nie pozwala zmienić ani wyciszyć dźwięku cudzej aplikacji, więc oryginalny dźwięk
 * trzeba wyciszyć w ustawieniach powiadomień tej aplikacji (aplikacja prowadzi do tych ustawień).
 */
class NotificationSoundService : NotificationListenerService() {

    private val handledKeys = HashMap<String, Long>()
    private val lastByPackage = HashMap<String, Long>()
    private val labelCache = HashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        Store.init(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Poproś system o ponowne podpięcie usługi (np. po jej zabiciu przez system).
        try {
            requestRebind(ComponentName(this, NotificationSoundService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind nie powiodło się", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        try {
            handle(sbn, rankingMap)
        } catch (t: Throwable) {
            Log.e(TAG, "Błąd obsługi powiadomienia", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        handledKeys.remove(key)
    }

    private fun handle(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        val flags = n.flags

        // Pomijamy powiadomienia, które nigdy nie powinny niczego grać.
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        if (n.category == Notification.CATEGORY_PROGRESS) return

        val ranking = Ranking()
        val hasRanking = rankingMap?.getRanking(sbn.key, ranking) == true
        if (hasRanking) {
            if (ranking.importance <= NotificationManager.IMPORTANCE_MIN) return
            // Respektuj tryb "Nie przeszkadzać": jeśli system nie dopuszcza tego powiadomienia, nie gramy.
            if (!ranking.matchesInterruptionFilter()) return
        }

        // Zapamiętaj aplikację (podpowiedzi przy dodawaniu reguły + ostrzeżenie o niewyciszonym oryginale).
        val originalSoundOn = hasRanking &&
            ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT &&
            ranking.channel?.sound != null
        Store.noteSeen(pkg, labelFor(pkg), originalSoundOn)

        val data = Store.data.value
        val rule = data.rules.firstOrNull { it.enabled && it.packageName == pkg } ?: return
        val playlistId = rule.playlistId ?: return

        val now = SystemClock.elapsedRealtime()
        val key = sbn.key

        // Aktualizacja istniejącego powiadomienia z "alert only once" – system też by nie zagrał.
        if ((flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0 && handledKeys.containsKey(key)) return

        // Zabezpieczenie przed serią powiadomień w ułamku sekundy.
        val last = lastByPackage[pkg]
        if (last != null && now - last < MIN_GAP_MS) return

        if (!Store.playRandom(applicationContext, playlistId)) return

        if (handledKeys.size > 300) handledKeys.clear()
        handledKeys[key] = now
        lastByPackage[pkg] = now
    }

    private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        private const val TAG = "NotificationSoundSvc"
        private const val MIN_GAP_MS = 700L
    }
}
