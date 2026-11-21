package pl.shuffletones

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Prosty odtwarzacz: jeden dźwięk naraz (nowy zastępuje poprzedni).
 * Powiadomienia grają jako USAGE_NOTIFICATION, więc system respektuje głośność powiadomień,
 * tryb cichy/wibracji oraz "Nie przeszkadzać".
 */
object SoundPlayer {
    private const val TAG = "SoundPlayer"

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var stopRunnable: Runnable? = null

    private val _playingKey = MutableStateFlow<String?>(null)

    /** Klucz (id dźwięku) aktualnie odtwarzanego pliku albo null. */
    val playingKey: StateFlow<String?> = _playingKey.asStateFlow()

    /**
     * @param key identyfikator do pokazania w UI (id dźwięku)
     * @param maxSeconds limit czasu odtwarzania, 0 = bez limitu
     * @param asNotification true = jak prawdziwe powiadomienie, false = podgląd jak muzyka
     */
    fun play(context: Context, key: String, file: File, maxSeconds: Int, asNotification: Boolean) {
        val appContext = context.applicationContext
        main.post { startInternal(appContext, key, file, maxSeconds, asNotification) }
    }

    fun stop() {
        main.post { stopInternal() }
    }

    private fun startInternal(ctx: Context, key: String, file: File, maxSeconds: Int, asNotification: Boolean) {
        stopInternal()
        if (!file.exists()) return

        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = am

        val attrs = AudioAttributes.Builder()
            .setUsage(if (asNotification) AudioAttributes.USAGE_NOTIFICATION else AudioAttributes.USAGE_MEDIA)
            .setContentType(
                if (asNotification) AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_MUSIC,
            )
            .build()

        // Na czas dźwięku chwilowo przyciszamy muzykę/film w tle.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        am.requestAudioFocus(request)
        focusRequest = request

        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { p ->
                if (player === p) {
                    p.start()
                    _playingKey.value = key
                }
            }
            mp.setOnCompletionListener { p ->
                if (player === p) stopInternal()
            }
            mp.setOnErrorListener { p, what, extra ->
                Log.w(TAG, "Błąd odtwarzania: what=$what extra=$extra")
                if (player === p) stopInternal()
                true
            }
            mp.prepareAsync()

            if (maxSeconds > 0) {
                val r = Runnable { stopInternal() }
                stopRunnable = r
                main.postDelayed(r, maxSeconds * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się odtworzyć dźwięku", e)
            stopInternal()
        }
    }

    private fun stopInternal() {
        stopRunnable?.let { main.removeCallbacks(it) }
        stopRunnable = null

        val p = player
        player = null
        if (p != null) {
            runCatching { p.setOnCompletionListener(null) }
            runCatching { p.stop() }
            runCatching { p.release() }
        }

        val request = focusRequest
        focusRequest = null
        if (request != null) runCatching { audioManager?.abandonAudioFocusRequest(request) }

        _playingKey.value = null
    }
}
