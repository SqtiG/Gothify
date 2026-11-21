@file:OptIn(ExperimentalMaterial3Api::class)

package pl.shuffletones

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by Store.data.collectAsState()
    val tick = UiSignals.resumeTick.intValue
    val accessEnabled = remember(tick) { isNotificationAccessEnabled(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia i pomoc") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Dostęp do powiadomień")
            Text(
                if (accessEnabled) {
                    "✓ Przyznany – aplikacja widzi powiadomienia i może grać Twoje dźwięki."
                } else {
                    "✗ Brak dostępu – bez niego aplikacja nic nie zagra."
                },
            )
            Button(onClick = { openNotificationAccessSettings(context) }) { Text("Otwórz ustawienia dostępu") }
            if (!accessEnabled) {
                Text(
                    "Przełącznik wyszarzony (częste przy aplikacjach instalowanych z pliku APK)? Otwórz „Info o aplikacji”, " +
                        "dotknij ⋮ w prawym górnym rogu i wybierz „Zezwól na ustawienia z ograniczeniami”.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { openAppDetails(context) }) { Text("Info o aplikacji") }
            }

            HorizontalDivider()

            SectionTitle("Działanie w tle")
            Text(
                "Żeby dźwięki grały zawsze, system nie może usypiać tej aplikacji. Wyłącz dla niej optymalizację baterii. " +
                    "Na Samsungu dodatkowo: Ustawienia → Bateria → Limity użycia w tle → „Nigdy nieuśpione aplikacje” → dodaj „Gothify”.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { openBatterySettings(context) }) { Text("Ustawienia optymalizacji baterii") }

            HorizontalDivider()

            SectionTitle("Odtwarzanie")
            Text("Maksymalna długość pojedynczego dźwięku powiadomienia:", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s", 0 to "Bez limitu").forEach { (seconds, label) ->
                    FilterChip(
                        selected = data.settings.maxPlaySeconds == seconds,
                        onClick = { Store.setMaxPlaySeconds(seconds) },
                        label = { Text(label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Nie losuj dwa razy z rzędu tego samego dźwięku",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = data.settings.avoidRepeat,
                    onCheckedChange = { Store.setAvoidRepeat(it) },
                )
            }
            Text(
                "Dźwięki grają głośnością powiadomień i respektują tryb cichy, wibracje oraz „Nie przeszkadzać”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            SectionTitle("Jak to działa")
            Text(
                "1. W zakładce „Dźwięki” wgraj własne pliki audio.\n" +
                    "2. W „Playlistach” utwórz playlisty i zaznacz, jakie dźwięki do nich należą.\n" +
                    "3. W „Powiadomieniach” wybierz aplikację (np. SMS-y) i przypisz jej playlistę.\n" +
                    "4. Dla tej aplikacji wycisz jej własny dźwięk powiadomień w ustawieniach systemu " +
                    "(przycisk „Wycisz oryginalny dźwięk aplikacji” w oknie reguły).\n\n" +
                    "Od tej chwili przy każdym nowym powiadomieniu z tej aplikacji ta aplikacja losuje dźwięk z playlisty " +
                    "i go odtwarza. Android nie pozwala podmienić dźwięku cudzej aplikacji bezpośrednio, dlatego oryginał " +
                    "musi być wyciszony – bez tego usłyszysz oba dźwięki naraz.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
