@file:OptIn(ExperimentalMaterial3Api::class)

package pl.shuffletones

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistsScreen(onOpen: (String) -> Unit) {
    val data by Store.data.collectAsState()
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (data.playlists.isEmpty()) {
            EmptyHint(
                "Brak playlist",
                "Dotknij +, aby utworzyć playlistę, np. „SMS-y” albo „Komunikator”, i zaznacz dźwięki, z których ma losować telefon.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(data.playlists, key = { it.id }) { playlist ->
                    val count = playlist.soundIds.count { sid -> data.sounds.any { it.id == sid } }
                    ListItem(
                        modifier = Modifier.clickable { onOpen(playlist.id) },
                        leadingContent = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(soundsCount(count)) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nowa playlista")
        }
    }

    if (creating) {
        TextInputDialog(
            title = "Nowa playlista",
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                val id = Store.addPlaylist(name)
                creating = false
                onOpen(id)
            },
        )
    }
}

@Composable
fun PlaylistDetailScreen(playlistId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val data by Store.data.collectAsState()
    val playingKey by SoundPlayer.playingKey.collectAsState()
    val playlist = data.playlists.firstOrNull { it.id == playlistId }

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Playlista zniknęła (np. została usunięta) – wracamy do listy.
    LaunchedEffect(playlist == null) {
        if (playlist == null) onBack()
    }
    if (playlist == null) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!Store.playRandom(context, playlist.id)) {
                                context.toast("Zaznacz najpierw przynajmniej jeden dźwięk w tej playliście")
                            }
                        },
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Wylosuj i odtwórz")
                    }
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Zmień nazwę")
                    }
                    IconButton(onClick = { deleting = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Usuń playlistę")
                    }
                },
            )
        },
    ) { padding ->
        if (data.sounds.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                EmptyHint("Brak dźwięków", "Najpierw wgraj dźwięki w zakładce „Dźwięki”.")
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    Text(
                        "Zaznacz dźwięki, z których telefon ma losować (zaznaczono: ${playlist.soundIds.size}). " +
                            "Ikona losowania u góry odtwarza przykładowy wylosowany dźwięk.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(data.sounds, key = { it.id }) { sound ->
                    val included = sound.id in playlist.soundIds
                    val isPlaying = playingKey == sound.id
                    ListItem(
                        modifier = Modifier.clickable {
                            Store.setSoundInPlaylist(playlist.id, sound.id, !included)
                        },
                        leadingContent = {
                            Checkbox(
                                checked = included,
                                onCheckedChange = { Store.setSoundInPlaylist(playlist.id, sound.id, it) },
                            )
                        },
                        headlineContent = { Text(sound.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(formatDuration(sound.durationMs)) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        SoundPlayer.stop()
                                    } else {
                                        SoundPlayer.play(context, sound.id, Store.soundFile(sound), 0, false)
                                    }
                                },
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Odtwórz",
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Zmień nazwę playlisty",
            initial = playlist.name,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                Store.renamePlaylist(playlist.id, name)
                renaming = false
            },
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = "Usunąć playlistę?",
            message = "Dźwięki zostaną w aplikacji, ale reguły powiadomień używające tej playlisty stracą przypisanie.",
            confirmLabel = "Usuń",
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                Store.deletePlaylist(playlist.id)
            },
        )
    }
}
