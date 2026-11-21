package pl.shuffletones

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String)

@Composable
fun RulesScreen() {
    val data by Store.data.collectAsState()
    val seen by Store.seen.collectAsState()

    var picking by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val editing = data.rules.firstOrNull { it.id == editingId }

    Box(Modifier.fillMaxSize()) {
        if (data.rules.isEmpty()) {
            EmptyHint(
                "Brak reguł",
                "Dotknij +, wybierz aplikację (np. aplikację do SMS-ów) i przypisz jej playlistę. " +
                    "Przy każdym powiadomieniu z tej aplikacji telefon wylosuje dźwięk z playlisty.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(data.rules, key = { it.id }) { rule ->
                    val playlist = data.playlists.firstOrNull { it.id == rule.playlistId }
                    val originalStillPlays = seen[rule.packageName]?.originalSoundOn == true
                    ListItem(
                        modifier = Modifier.clickable { editingId = rule.id },
                        leadingContent = { AppIcon(rule.packageName, 40.dp) },
                        headlineContent = { Text(rule.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(
                                    if (playlist != null) {
                                        "Playlista: ${playlist.name}"
                                    } else {
                                        "Nie wybrano playlisty – nic nie zagra"
                                    },
                                )
                                if (originalStillPlays) {
                                    Text(
                                        "⚠ Ta aplikacja nadal gra własny dźwięk – dotknij i wycisz go",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { Store.setRuleEnabled(rule.id, it) },
                            )
                        },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { picking = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Dodaj regułę")
        }
    }

    if (picking) {
        AppPickerDialog(
            onDismiss = { picking = false },
            onPicked = { app ->
                picking = false
                Store.upsertRule(app.packageName, app.label, data.playlists.firstOrNull()?.id)
                editingId = Store.ruleIdFor(app.packageName)
            },
        )
    }

    if (editing != null) {
        RuleEditDialog(
            rule = editing,
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun RuleEditDialog(rule: Rule, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val data by Store.data.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(rule.packageName, 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(rule.appLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Losuj dźwięk z playlisty:", style = MaterialTheme.typography.titleSmall)
                if (data.playlists.isEmpty()) {
                    Text(
                        "Nie masz jeszcze żadnych playlist. Utwórz je w zakładce „Playlisty”.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                data.playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { Store.setRulePlaylist(rule.id, playlist.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = rule.playlistId == playlist.id,
                            onClick = { Store.setRulePlaylist(rule.id, playlist.id) },
                        )
                        Text("${playlist.name} (${soundsCount(playlist.soundIds.size)})")
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { openAppNotificationSettings(context, rule.packageName) }) {
                    Text("Wycisz oryginalny dźwięk aplikacji")
                }
                Text(
                    "Otworzy ustawienia powiadomień tej aplikacji. Wejdź w kategorię powiadomień (np. „Wiadomości”) " +
                        "i ustaw jej dźwięk na „Brak”. Najlepiej zostaw same powiadomienia włączone, żeby wyskakujące okienka " +
                        "nadal działały. Jeśli zostawisz oryginalny dźwięk, usłyszysz dwa naraz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val id = rule.playlistId
                        if (id == null || !Store.playRandom(context, id)) {
                            context.toast("Wybierz playlistę, w której zaznaczono przynajmniej jeden dźwięk")
                        }
                    },
                ) { Text("Test") }
                TextButton(
                    onClick = {
                        Store.deleteRule(rule.id)
                        onDismiss()
                    },
                ) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPicked: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val seen by Store.seen.collectAsState()
    var query by remember { mutableStateOf("") }

    val apps by produceState(emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    val smsPackage = remember { runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull() }
    val smsEntry = apps.firstOrNull { it.packageName == smsPackage }
    val recent = seen.values
        .sortedByDescending { it.lastSeen }
        .take(10)
        .map { AppEntry(it.packageName, it.label) }
    val filtered = if (query.isBlank()) {
        apps
    } else {
        apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz aplikację") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Szukaj aplikacji…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (query.isBlank()) {
                        if (smsEntry != null) {
                            item(key = "h_sms") { PickerHeader("Domyślna aplikacja SMS") }
                            item(key = "sms") { PickerRow(smsEntry, onPicked) }
                        }
                        if (recent.isNotEmpty()) {
                            item(key = "h_recent") { PickerHeader("Ostatnio wysłały powiadomienie") }
                            items(recent, key = { "r_" + it.packageName }) { PickerRow(it, onPicked) }
                        }
                        item(key = "h_all") { PickerHeader("Wszystkie aplikacje") }
                    }
                    items(filtered, key = { "a_" + it.packageName }) { PickerRow(it, onPicked) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun PickerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(app: AppEntry, onPicked: (AppEntry) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPicked(app) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        AppIcon(app.packageName, 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
