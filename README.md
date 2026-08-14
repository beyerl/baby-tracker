# Baby Tracker

Eine kleine Android-App (Kotlin / Jetpack Compose) zum Erfassen der täglichen
Aktivitäten eines Babys: **Stuhlgang, Pinkeln, Füttern** und **Schlaffenster**.

## Features

- **Monatsansicht** (Start): Kalender mit farbkodierten Zählern pro Tag und Kategorie.
- **Tagesansicht**: chronologische Liste aller Einträge mit Uhrzeit, Tageszusammenfassung
  inkl. Anzahl Schlaffenster.
- Schnelles Erfassen per Kategorie-Button (Standardzeit = jetzt), Zeit anpassbar.
- Schlaffenster mit Von/Bis-Zeit (auch über Mitternacht).
- Einträge bearbeiten und löschen.
- Vollständig **offline**, lokale Speicherung via Room.

## Tech-Stack

- Kotlin, Jetpack Compose (Material 3)
- MVVM (ViewModel + StateFlow), Navigation Compose
- Room (SQLite), KSP
- min SDK 26, target/compile SDK 34, Java 17

## Bauen

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## CI / Deployment

GitHub Actions (`.github/workflows/android.yml`) baut bei jedem Push auf `main` die
Debug-APK, lädt sie als Artefakt hoch und veröffentlicht sie als rollierendes
`latest`-Release. Für ein Git-Tag `v*` wird ein reguläres Release erstellt.

> Play-Store-Deployment ist nicht enthalten – dafür wären ein Upload-Keystore und
> Play-Console-Credentials als Repository-Secrets nötig.

## Herkunft

Spezifikation abgeleitet aus einer Sprachnotiz – siehe `docs/`.
