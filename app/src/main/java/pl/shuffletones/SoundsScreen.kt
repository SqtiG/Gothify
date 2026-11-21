package pl.shuffletones

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SoundsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by Store.data.collectAsState()
    val playingKey by SoundPlayer.playingKey.collectAsState()

    var busy by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SoundItem?>(null) }
    var deleting by remember { mutableStateOf<SoundItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                val ok = Store.importSounds(context, uris)
                busy = false
                context.toast(
                    if (ok == uris.size) {
                        "Dodano dźwięki: $ok"
                    } else {
                        "Dodano $ok z ${uris.size} – reszta nie jest obsługiwanym plikiem audio"
                    },
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (data.sounds.isEmpty()) {
            EmptyHint(
                "Brak dźwięków",
                "Dotknij +, aby wgrać własne pliki dźwiękowe (mp3, ogg, wav, m4a, flac…). Potem ułóż je w playlisty.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(data.sounds, key = { it.id }) { sound ->
                    val isPlaying = playingKey == sound.id
                    ListItem(
                        headlineContent = { Text(sound.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(formatDuration(sound.durationMs)) },
                        leadingContent = {
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
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = sound }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Zmień nazwę")
                                }
                                IconButton(onClick = { deleting = sound }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Usuń")
                                }
                            }
                        },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { picker.launch(arrayOf("audio/*", "application/ogg")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Dodaj dźwięki")
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    renaming?.let { sound ->
        TextInputDialog(
            title = "Zmień nazwę",
            initial = sound.name,
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                Store.renameSound(sound.id, newName)
                renaming = null
            },
        )
    }

    deleting?.let { sound ->
        ConfirmDialog(
            title = "Usunąć dźwięk?",
            message = "„${sound.name}” zostanie usunięty z aplikacji i ze wszystkich playlist.",
            confirmLabel = "Usuń",
            onDismiss = { deleting = null },
            onConfirm = {
                if (playingKey == sound.id) SoundPlayer.stop()
                Store.deleteSound(sound.id)
                deleting = null
            },
        )
    }
}
