package pl.shuffletones

/** Wgrany plik dźwiękowy (kopia trzymana w pamięci wewnętrznej aplikacji). */
data class SoundItem(
    val id: String,
    val name: String,
    val fileName: String,
    val durationMs: Long,
)

/** Playlista = nazwana lista dźwięków, z której losujemy. */
data class Playlist(
    val id: String,
    val name: String,
    val soundIds: List<String>,
)

/** Reguła: powiadomienia z danej aplikacji => losuj dźwięk z playlisty. */
data class Rule(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val playlistId: String?,
    val enabled: Boolean,
)

data class AppSettings(
    /** Maksymalny czas odtwarzania pojedynczego dźwięku w sekundach; 0 = bez limitu. */
    val maxPlaySeconds: Int = 15,
    /** Nie losuj dwa razy z rzędu tego samego dźwięku (jeśli playlista ma więcej niż jeden). */
    val avoidRepeat: Boolean = true,
)

data class AppData(
    val sounds: List<SoundItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

/** Aplikacja, od której niedawno przyszło powiadomienie (do podpowiedzi przy dodawaniu reguły). */
data class SeenApp(
    val packageName: String,
    val label: String,
    val lastSeen: Long,
    /** true = ostatnie powiadomienie tej aplikacji nadal miało własny dźwięk systemowy. */
    val originalSoundOn: Boolean,
)
