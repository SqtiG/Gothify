package pl.shuffletones

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Jedyne źródło prawdy: dźwięki, playlisty, reguły i ustawienia.
 * Zapis do data.json w pamięci wewnętrznej; współdzielone przez UI i usługę nasłuchującą.
 */
object Store {
    private const val TAG = "Store"
    private val lock = Any()

    private var initialized = false
    private lateinit var dataFile: File
    private lateinit var seenFile: File

    lateinit var soundsDir: File
        private set

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _seen = MutableStateFlow<Map<String, SeenApp>>(emptyMap())
    val seen: StateFlow<Map<String, SeenApp>> = _seen.asStateFlow()

    /** playlistId -> id ostatnio odtworzonego dźwięku (żeby nie powtarzać dwa razy z rzędu). */
    private val lastPlayed = HashMap<String, String>()

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            val dir = context.applicationContext.filesDir
            dataFile = File(dir, "data.json")
            seenFile = File(dir, "seen.json")
            soundsDir = File(dir, "sounds").also { it.mkdirs() }
            _data.value = readData()
            _seen.value = readSeen()
            initialized = true
        }
    }

    fun soundFile(sound: SoundItem): File = File(soundsDir, sound.fileName)

    // ------------------------------------------------------------------ dźwięki

    suspend fun importSounds(context: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var ok = 0
        for (uri in uris) {
            if (importOne(context, uri)) ok++
        }
        ok
    }

    private fun importOne(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        var display = "dźwięk"
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val value = c.getString(index)
                        if (!value.isNullOrBlank()) display = value
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się odczytać nazwy pliku", e)
        }

        val ext = display.substringAfterLast('.', "").lowercase()
        val safeExt = if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) ext else "audio"
        val baseName = if (display.contains('.')) display.substringBeforeLast('.') else display
        val id = UUID.randomUUID().toString()
        val fileName = "$id.$safeExt"
        val dest = File(soundsDir, fileName)

        return try {
            val input = resolver.openInputStream(uri)
            if (input == null) {
                false
            } else {
                input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
                val duration = readDuration(dest)
                if (duration == null) {
                    dest.delete()
                    false
                } else {
                    val item = SoundItem(id, baseName.ifBlank { "Dźwięk" }, fileName, duration)
                    update { it.copy(sounds = it.sounds + item) }
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import nie powiódł się", e)
            dest.delete()
            false
        }
    }

    /** Zwraca czas trwania w ms albo null, jeśli plik nie jest rozpoznawalnym audio. */
    private fun readDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun renameSound(id: String, name: String) = update { d ->
        d.copy(sounds = d.sounds.map { s -> if (s.id == id) s.copy(name = name.trim().ifEmpty { s.name }) else s })
    }

    fun deleteSound(id: String) {
        val sound = _data.value.sounds.firstOrNull { it.id == id }
        update { d ->
            d.copy(
                sounds = d.sounds.filterNot { it.id == id },
                playlists = d.playlists.map { p -> p.copy(soundIds = p.soundIds.filter { sid -> sid != id }) },
            )
        }
        if (sound != null) runCatching { soundFile(sound).delete() }
    }

    // ------------------------------------------------------------------ playlisty

    fun addPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        val finalName = name.trim().ifEmpty { "Playlista" }
        update { it.copy(playlists = it.playlists + Playlist(id, finalName, emptyList())) }
        return id
    }

    fun renamePlaylist(id: String, name: String) = update { d ->
        d.copy(playlists = d.playlists.map { p -> if (p.id == id) p.copy(name = name.trim().ifEmpty { p.name }) else p })
    }

    fun deletePlaylist(id: String) = update { d ->
        d.copy(
            playlists = d.playlists.filterNot { it.id == id },
            rules = d.rules.map { r -> if (r.playlistId == id) r.copy(playlistId = null) else r },
        )
    }

    fun setSoundInPlaylist(playlistId: String, soundId: String, included: Boolean) = update { d ->
        d.copy(
            playlists = d.playlists.map { p ->
                if (p.id != playlistId) {
                    p
                } else {
                    val without = p.soundIds.filter { it != soundId }
                    p.copy(soundIds = if (included) without + soundId else without)
                }
            },
        )
    }

    // ------------------------------------------------------------------ reguły

    fun upsertRule(packageName: String, appLabel: String, playlistId: String?) = update { d ->
        val existing = d.rules.firstOrNull { it.packageName == packageName }
        if (existing == null) {
            d.copy(rules = d.rules + Rule(UUID.randomUUID().toString(), packageName, appLabel, playlistId, true))
        } else {
            d.copy(
                rules = d.rules.map { r ->
                    if (r.id == existing.id) r.copy(appLabel = appLabel, playlistId = playlistId ?: r.playlistId) else r
                },
            )
        }
    }

    fun ruleIdFor(packageName: String): String? =
        _data.value.rules.firstOrNull { it.packageName == packageName }?.id

    fun setRuleEnabled(id: String, enabled: Boolean) = update { d ->
        d.copy(rules = d.rules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r })
    }

    fun setRulePlaylist(id: String, playlistId: String?) = update { d ->
        d.copy(rules = d.rules.map { r -> if (r.id == id) r.copy(playlistId = playlistId) else r })
    }

    fun deleteRule(id: String) = update { d -> d.copy(rules = d.rules.filterNot { it.id == id }) }

    // ------------------------------------------------------------------ ustawienia

    fun setMaxPlaySeconds(seconds: Int) = update { d -> d.copy(settings = d.settings.copy(maxPlaySeconds = seconds)) }

    fun setAvoidRepeat(value: Boolean) = update { d -> d.copy(settings = d.settings.copy(avoidRepeat = value)) }

    // ------------------------------------------------------------------ losowanie i odtwarzanie

    /** Losuje dźwięk z playlisty (pomija pliki, których już nie ma, i – opcjonalnie – poprzednio zagrany). */
    fun pickRandomSound(playlistId: String): SoundItem? = synchronized(lock) {
        val d = _data.value
        val playlist = d.playlists.firstOrNull { it.id == playlistId }
        val candidates = playlist?.soundIds.orEmpty()
            .mapNotNull { sid -> d.sounds.firstOrNull { it.id == sid } }
            .filter { soundFile(it).exists() }
        if (candidates.isEmpty()) {
            null
        } else {
            val last = lastPlayed[playlistId]
            val pool = if (d.settings.avoidRepeat && candidates.size > 1) {
                candidates.filter { it.id != last }
            } else {
                candidates
            }
            val pick = pool.random()
            lastPlayed[playlistId] = pick.id
            pick
        }
    }

    /** Losuje i odtwarza dźwięk z playlisty jako dźwięk powiadomienia. Zwraca false, gdy nie ma czego grać. */
    fun playRandom(context: Context, playlistId: String): Boolean {
        val sound = pickRandomSound(playlistId) ?: return false
        SoundPlayer.play(context, sound.id, soundFile(sound), _data.value.settings.maxPlaySeconds, true)
        return true
    }

    // ------------------------------------------------------------------ ostatnio widziane aplikacje

    fun noteSeen(packageName: String, label: String, originalSoundOn: Boolean) {
        synchronized(lock) {
            val old = _seen.value[packageName]
            val changed = old == null || old.originalSoundOn != originalSoundOn || old.label != label
            var map = _seen.value + (packageName to SeenApp(packageName, label, System.currentTimeMillis(), originalSoundOn))
            if (map.size > 40) {
                map = map.values.sortedByDescending { it.lastSeen }.take(40).associateBy { it.packageName }
            }
            _seen.value = map
            if (changed) writeAtomic(seenFile, seenToJson(map).toString())
        }
    }

    // ------------------------------------------------------------------ zapis / odczyt

    private fun update(transform: (AppData) -> AppData) {
        synchronized(lock) {
            val updated = transform(_data.value)
            _data.value = updated
            writeAtomic(dataFile, dataToJson(updated).toString())
        }
    }

    private fun writeAtomic(target: File, text: String) {
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Zapis nie powiódł się: ${target.name}", e)
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun dataToJson(d: AppData): JSONObject {
        val root = JSONObject()

        val sounds = JSONArray()
        d.sounds.forEach { s ->
            sounds.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("fileName", s.fileName)
                    .put("durationMs", s.durationMs),
            )
        }
        root.put("sounds", sounds)

        val playlists = JSONArray()
        d.playlists.forEach { p ->
            val ids = JSONArray()
            p.soundIds.forEach { ids.put(it) }
            playlists.put(JSONObject().put("id", p.id).put("name", p.name).put("soundIds", ids))
        }
        root.put("playlists", playlists)

        val rules = JSONArray()
        d.rules.forEach { r ->
            rules.put(
                JSONObject()
                    .put("id", r.id)
                    .put("packageName", r.packageName)
                    .put("appLabel", r.appLabel)
                    .put("playlistId", r.playlistId ?: JSONObject.NULL)
                    .put("enabled", r.enabled),
            )
        }
        root.put("rules", rules)

        root.put(
            "settings",
            JSONObject()
                .put("maxPlaySeconds", d.settings.maxPlaySeconds)
                .put("avoidRepeat", d.settings.avoidRepeat),
        )
        return root
    }

    private fun readData(): AppData {
        if (!dataFile.exists()) return AppData()
        return try {
            val root = JSONObject(dataFile.readText())

            val sounds = root.optJSONArray("sounds").objects().map { o ->
                SoundItem(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    fileName = o.getString("fileName"),
                    durationMs = o.optLong("durationMs", 0L),
                )
            }
            val playlists = root.optJSONArray("playlists").objects().map { o ->
                val ids = o.optJSONArray("soundIds")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    soundIds = if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) },
                )
            }
            val rules = root.optJSONArray("rules").objects().map { o ->
                val pkg = o.getString("packageName")
                Rule(
                    id = o.getString("id"),
                    packageName = pkg,
                    appLabel = o.optString("appLabel", pkg),
                    playlistId = if (o.isNull("playlistId")) null else o.getString("playlistId"),
                    enabled = o.optBoolean("enabled", true),
                )
            }
            val s = root.optJSONObject("settings")
            val settings = if (s == null) {
                AppSettings()
            } else {
                AppSettings(
                    maxPlaySeconds = s.optInt("maxPlaySeconds", 15),
                    avoidRepeat = s.optBoolean("avoidRepeat", true),
                )
            }
            AppData(sounds, playlists, rules, settings)
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się wczytać danych – zapisuję kopię uszkodzonego pliku", e)
            runCatching { dataFile.copyTo(File(dataFile.parentFile, "data.json.bad"), overwrite = true) }
            AppData()
        }
    }

    private fun seenToJson(map: Map<String, SeenApp>): JSONArray {
        val arr = JSONArray()
        map.values.forEach { a ->
            arr.put(
                JSONObject()
                    .put("packageName", a.packageName)
                    .put("label", a.label)
                    .put("lastSeen", a.lastSeen)
                    .put("originalSoundOn", a.originalSoundOn),
            )
        }
        return arr
    }

    private fun readSeen(): Map<String, SeenApp> {
        if (!seenFile.exists()) return emptyMap()
        return try {
            JSONArray(seenFile.readText()).objects().associate { o ->
                val pkg = o.getString("packageName")
                pkg to SeenApp(
                    packageName = pkg,
                    label = o.optString("label", pkg),
                    lastSeen = o.optLong("lastSeen", 0L),
                    originalSoundOn = o.optBoolean("originalSoundOn", false),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się wczytać listy widzianych aplikacji", e)
            emptyMap()
        }
    }
}
