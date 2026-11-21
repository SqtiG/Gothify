# Gothify – własne dźwięki powiadomień z losowaniem z playlist

Aplikacja na Androida (8.0+; projektowana z myślą o Samsungu Galaxy A26 i S23, ale nie używa niczego
specyficznego dla Samsunga, więc powinna działać na większości telefonów z Androidem). Wgrywasz własne pliki dźwiękowe, układasz je w playlisty i przypisujesz
playlistę do aplikacji (np. do aplikacji SMS). Przy każdym powiadomieniu z tej aplikacji telefon
losuje inny dźwięk z playlisty.

## Jak to działa technicznie (i dlaczego trzeba wyciszyć oryginał)

Android nie pozwala jednej aplikacji zmienić dźwięku powiadomień innej aplikacji ani losować go przy każdym
powiadomieniu (dźwięk jest przypisany do „kanału powiadomień” na stałe). Dlatego:

1. Aplikacja dostaje **Dostęp do powiadomień** (`NotificationListenerService`) i widzi, że przyszło powiadomienie.
2. Jeśli dla tej aplikacji jest reguła, **sama odtwarza wylosowany dźwięk** (jako dźwięk powiadomienia –
   głośność powiadomień, tryb cichy/wibracji i „Nie przeszkadzać” działają normalnie).
3. **Oryginalny dźwięk aplikacji musisz wyłączyć** w jej ustawieniach powiadomień (kategoria powiadomień →
   dźwięk „Brak”; same powiadomienia zostaw włączone, żeby działały wyskakujące okienka), inaczej usłyszysz oba.
   W oknie reguły jest do tego przycisk „Wycisz oryginalny dźwięk aplikacji”, a na liście reguł pojawia się
   ostrzeżenie ⚠, jeśli aplikacja nadal gra swój dźwięk.

Aplikacja nie wymaga internetu i nie ma żadnych uprawnień poza dostępem do powiadomień, który przyznajesz ręcznie.

## Jak zbudować APK

### Opcja A – bez instalowania czegokolwiek (GitHub Actions)
1. Załóż prywatne repozytorium na GitHubie i wgraj do niego całą zawartość tego folderu (razem z `.github`).
2. Wejdź w zakładkę **Actions → Build APK → Run workflow** (uruchomi się też samo po każdym pushu).
3. Po kilku minutach otwórz zakończone uruchomienie i pobierz artefakt **Gothify-APK** (`app-debug.apk`).

### Opcja B – Android Studio
1. Zainstaluj Android Studio, wybierz **Open** i wskaż ten folder; poczekaj na synchronizację Gradle.
2. **Build → Build APK(s)** albo podłącz telefon (włączone debugowanie USB) i kliknij **Run**.

## Instalacja i pierwsze uruchomienie na telefonie

1. Skopiuj APK na telefon, otwórz go i zezwól na instalację z nieznanego źródła.
2. Uruchom aplikację – czerwony baner poprowadzi Cię do przyznania **Dostępu do powiadomień**.
   Jeśli przełącznik jest wyszarzony (Android 13+ blokuje to dla APK spoza sklepu):
   **Ustawienia → Aplikacje → Gothify → ⋮ → Zezwól na ustawienia z ograniczeniami**, potem wróć i przyznaj dostęp.
3. **Bateria (Samsung):** Ustawienia → Bateria → Limity użycia w tle → „Nigdy nieuśpione aplikacje” → dodaj „Gothify”.
   Bez tego One UI może uśpić usługę i dźwięki przestaną grać.
4. Zakładka **Dźwięki** → **+** → wybierz pliki (mp3, ogg, wav, m4a, flac…).
5. Zakładka **Playlisty** → **+** → nazwij playlistę (np. „SMS-y”) i zaznacz dźwięki.
6. Zakładka **Powiadomienia** → **+** → wybierz aplikację (jest skrót do domyślnej aplikacji SMS i lista aplikacji,
   które ostatnio wysłały powiadomienie) → wybierz playlistę → **Wycisz oryginalny dźwięk aplikacji**.
7. Przycisk **Test** w oknie reguły i ikona losowania w playliście pozwalają od razu usłyszeć efekt.

## Szczegóły działania

- Dźwięki są kopiowane do pamięci aplikacji, więc działają nawet po usunięciu oryginalnych plików.
- Losowanie nie powtarza tego samego dźwięku dwa razy z rzędu (można wyłączyć w ustawieniach).
- Pojedynczy dźwięk jest przycinany do ustawionej długości (domyślnie 15 s).
- Pomijane są powiadomienia: trwające (np. odtwarzacz muzyki), usługi pierwszoplanowe, postęp,
  podsumowania grup oraz aktualizacje, przy których system sam nie odtwarza dźwięku.
- Reguła jest per aplikacja (np. osobna dla SMS-ów, komunikatora i poczty), z osobną playlistą.

## Struktura projektu

```
app/src/main/java/pl/shuffletones/
  NotificationSoundService.kt  – nasłuch powiadomień, dopasowanie reguły, losowanie
  SoundPlayer.kt               – odtwarzanie (MediaPlayer, fokus audio, limit długości)
  Store.kt / Models.kt         – dane (JSON w pamięci aplikacji), import plików, losowanie
  MainActivity.kt              – motyw, nawigacja, baner uprawnień
  SoundsScreen.kt / PlaylistsScreen.kt / RulesScreen.kt / SettingsScreen.kt – ekrany (Jetpack Compose)
```

## Ograniczenia

- Reguła działa na poziomie aplikacji, nie pojedynczych kategorii powiadomień (np. osobno SMS-y i MMS-y w tej samej aplikacji).
- Aplikacja musi działać w tle: producenci telefonów potrafią agresywnie usypiać aplikacje – patrz punkt o baterii.
- Jeśli oryginalny dźwięk nie zostanie wyciszony, zagra razem z wylosowanym.
