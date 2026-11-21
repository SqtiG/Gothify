@file:OptIn(ExperimentalMaterial3Api::class)

package pl.shuffletones

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Sygnały z Activity do Compose (np. po powrocie z ustawień systemowych odświeżamy status uprawnień). */
object UiSignals {
    val resumeTick = mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UiSignals.resumeTick.intValue += 1
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showSettings || openPlaylistId != null) {
        if (showSettings) showSettings = false else openPlaylistId = null
    }

    val playlistId = openPlaylistId
    when {
        showSettings -> SettingsScreen(onBack = { showSettings = false })
        playlistId != null -> PlaylistDetailScreen(playlistId = playlistId, onBack = { openPlaylistId = null })
        else -> MainTabs(
            tab = tab,
            onTab = { tab = it },
            onOpenPlaylist = { openPlaylistId = it },
            onSettings = { showSettings = true },
        )
    }
}

@Composable
private fun MainTabs(
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val titles = listOf("Dźwięki", "Playlisty", "Powiadomienia")
    val icons = listOf(Icons.Default.MusicNote, Icons.Default.LibraryMusic, Icons.Default.Notifications)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gothify") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia i pomoc")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { onTab(index) },
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(title) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AccessBanner()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> SoundsScreen()
                    1 -> PlaylistsScreen(onOpen = onOpenPlaylist)
                    else -> RulesScreen()
                }
            }
        }
    }
}

/** Czerwony baner, dopóki użytkownik nie przyzna aplikacji dostępu do powiadomień. */
@Composable
private fun AccessBanner() {
    val context = LocalContext.current
    val tick = UiSignals.resumeTick.intValue
    val enabled = remember(tick) { isNotificationAccessEnabled(context) }
    if (enabled) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Aplikacja nie ma dostępu do powiadomień",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Bez tego nie usłyszysz swoich dźwięków. Włącz dostęp dla „Gothify” na liście systemowej.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row {
                Button(onClick = { openNotificationAccessSettings(context) }) { Text("Przyznaj dostęp") }
                TextButton(onClick = { openAppDetails(context) }) { Text("Info o aplikacji") }
            }
            Text(
                "Przełącznik wyszarzony? Wejdź w „Info o aplikacji”, dotknij ⋮ (menu w prawym górnym rogu) i wybierz „Zezwól na ustawienia z ograniczeniami”. Potem wróć i przyznaj dostęp.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
