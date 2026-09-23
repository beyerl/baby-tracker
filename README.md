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
- **Auswertung** in Reitern (Pillen-Auswahl), alle mit gemeinsamem Zeitraum
  (Von/Bis-Datepicker, Standard: aktueller Monat). Diagramme im Napper-Stil in
  Karten: geglättete Linie mit Flächenfüllung, gepunktete Durchschnittslinie,
  „Durchschnittlich: …"-Pille darunter; bis 10 Tage mit Wochentag je Tag:
  - **Einträge**: Liniendiagramm der Einträge pro Tag für alle vier Kategorien
    (einzelne Linien, nicht gestapelt) mit klickbarer Legende zum Ein-/Ausblenden
    einzelner Kategorien.
  - **Prognose** (unabhängig vom Zeitraum): Vorhersage für heute aus den letzten
    7 Tagen – das n-te Nickerchen bzw. die n-te Fütterung zum durchschnittlichen
    Abstand ab dem Aufwachen (Nickerchen mit durchschnittlicher Dauer), Anzahl =
    Median der Tage. Bereits erfasste Einträge ersetzen die Prognose, die übrigen
    behalten ihren Abstand zum letzten Eintrag. Darstellung als Ring wie in Napper
    (Aufwachzeit → Schlafenszeit, erfasst = ausgefüllt, Prognose = gepunktet) mit
    Countdown zum nächsten Ereignis, Nickerchen-Gesamtdauer und Tagesablauf.
  - **Schlafenszeiten**: Uhrzeit des Aufwachens und des Einschlafens pro Tag aus
    den Schlaf-Markierungen (Einschlafen nach Mitternacht zählt zum Vorabend).
  - **Nachtschlaf**: geschlafene Zeit pro Nacht – Summe aller Schlaf-Einträge
    von der Schlafenszeit bis zur Aufwachzeit am Folgetag – und **Wachphasen
    nachts** (Zeit von Schlafens- bis Aufwachzeit minus geschlafene Zeit), mit
    Wochendurchschnitten (Mo–So) darunter.
  - **Gesamtschlaf**: alle Schlafzeiten eines Kalendertags 00:00–24:00 –
    Nachtschlaf-Anteile und alle Nickerchen (Schlaf über Mitternacht wird
    aufgeteilt, nur abgeschlossene Tage), mit Wochendurchschnitt (Mo–So) darunter.
  - **Schlafmuster**: eine Zeile pro Tag mit Aufwachzeit, Nickerchen (farbig nach
    Reihenfolge: erstes … fünftes+) und Schlafenszeit auf einer Uhrzeit-Achse; per
    Schalter *Relativ zum Aufwachen anzeigen*. Nickerchen = Schlaf ohne Markierung
    außerhalb der Nacht, nach der Aufwach- und vor der Schlafenszeit des Tages.
  - **Wachzeit**: 24 h minus alle Schlafzeiten eines Kalendertags (Schlaf über
    Mitternacht wird aufgeteilt, nur abgeschlossene Tage), mit
    Wochendurchschnitt (Mo–So) darunter.
  - **Fütterungen**: durchschnittlicher Abstand zwischen zwei Fütterungen pro Tag
    (gezählt am Tag der späteren Fütterung; Abstände über 16 h gelten als
    Erfassungslücke) und Durchschnitt über den gesamten Zeitraum.
- **Dunkles Design** im Stil der Napper-App (Nachtblau, Lavendel-Akzent), unabhängig
  von System-Theme und Material-You-Farben.
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
