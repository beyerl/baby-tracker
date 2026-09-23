# Baby Tracker

Eine kleine Android-App (Kotlin / Jetpack Compose) zum Erfassen der täglichen
Aktivitäten eines Babys: **Stuhlgang, Pinkeln, Füttern** und **Schlaffenster**.

## Features

- **Monatsansicht** (Start): Kalender mit farbkodierten Zählern pro Tag für Füttern und
  Schlaf (Stuhlgang und Pinkeln stehen in der Tagesansicht).
- **Tagesansicht**: chronologische Liste aller Einträge mit Uhrzeit, Tageszusammenfassung
  inkl. Anzahl Schlaffenster.
- Schnelles Erfassen per Kategorie-Button (Standardzeit = jetzt), Zeit anpassbar.
- Schlaffenster mit Von/Bis-Zeit (auch über Mitternacht).
- **Schlaf-Markierung** per Radiobutton: *Aufwachzeit*, *Schlafenszeit*,
  *Schlafens- und Aufwachzeit* (durchgeschlafene Nacht in einem Eintrag) oder
  *weder noch* (Standard) – Grundlage der Schlaf-Statistiken. Sichtbar in der
  Tagesansicht.
- Einträge bearbeiten und löschen.
- **Excel-Export** (`.xlsx`) aller erfassten Daten über den Download-Button in der
  Monatsansicht – Speicherort per System-Dialog wählbar, ohne Zusatzberechtigungen.
- **Excel-Import** (`.xlsx`) über den Upload-Button – Daten wahlweise **hinzufügen**
  oder **ersetzen**. Liest das eigene Exportformat (Spalten Datum, Start, Ende,
  Kategorie, Notiz, Markierung); Dateien ohne Markierungs-Spalte bleiben lesbar.
- **Auswertung** in Reitern, alle mit gemeinsamem Zeitraum (Von/Bis-Datepicker,
  Standard: aktueller Monat):
  - **Einträge**: Liniendiagramm der Einträge pro Tag für alle vier Kategorien
    (einzelne Linien, nicht gestapelt) mit klickbarer Legende zum Ein-/Ausblenden
    einzelner Kategorien.
  - **Schlafenszeiten**: Uhrzeit des Aufwachens und des Einschlafens pro Tag aus
    den Schlaf-Markierungen (Einschlafen nach Mitternacht zählt zum Vorabend).
  - **Nachtschlaf**: geschlafene Zeit pro Nacht – Summe aller Schlaf-Einträge
    von der Schlafenszeit bis zur Aufwachzeit am Folgetag – und **Wachphasen
    nachts** (Zeit von Schlafens- bis Aufwachzeit minus geschlafene Zeit), mit
    Wochendurchschnitten (Mo–So) darunter.
  - **Gesamtschlaf**: alle Schlafzeiten eines Kalendertags 00:00–24:00 –
    Nachtschlaf-Anteile und alle Nickerchen (Schlaf über Mitternacht wird
    aufgeteilt, nur abgeschlossene Tage), mit Wochendurchschnitt (Mo–So) darunter.
  - **Wachzeit**: 24 h minus alle Schlafzeiten eines Kalendertags (Schlaf über
    Mitternacht wird aufgeteilt, nur abgeschlossene Tage), mit
    Wochendurchschnitt (Mo–So) darunter.
  - **Fütterungen**: durchschnittlicher Abstand zwischen zwei Fütterungen pro Tag
    (gezählt am Tag der späteren Fütterung; Abstände über 16 h gelten als
    Erfassungslücke) und Durchschnitt über den gesamten Zeitraum.
- Vollständig **offline**, lokale Speicherung via Room.

## Tech-Stack

- Kotlin, Jetpack Compose (Material 3)
- MVVM (ViewModel + StateFlow), Navigation Compose
- Room (SQLite), KSP
- min SDK 26, target/compile SDK 34, Java 17
- Tests: JUnit 4, Robolectric (Room-Migration, Excel-Roundtrip)

## Bauen

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew testDebugUnitTest   # Unit-Tests
```

## CI / Deployment

GitHub Actions (`.github/workflows/android.yml`) baut bei jedem Push auf `main` die
Debug-APK und veröffentlicht sie als rollierendes `latest`-Release. Für ein Git-Tag
`v*` wird ein reguläres Release erstellt.

> Play-Store-Deployment ist nicht enthalten – dafür wären ein Upload-Keystore und
> Play-Console-Credentials als Repository-Secrets nötig.

## Herkunft

Spezifikation abgeleitet aus einer Sprachnotiz – siehe `docs/`.
