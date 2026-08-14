# Spezifikation: Baby-Tracker (Android / Kotlin)

- **Status:** Entwurf (v0.1) – abgeleitet aus Sprachnotiz vom 2026-08-14
- **Quelle:** [`transcript-2026-08-14.md`](./transcript-2026-08-14.md)
- **Plattform:** Android (Kotlin)

## 1. Zielsetzung

Eine einfache App, mit der Eltern die täglichen Aktivitäten ihres Babys erfassen und
überblicken können: **Stuhlgang, Pinkeln, Füttern** und **Schlafphasen**. Der Fokus
liegt auf schnellem Erfassen ("jetzt ist es passiert") und einem klaren Überblick auf
Tages- und Monatsebene.

## 2. Kernkonzepte

### 2.1 Ereignis-Kategorien

| Kategorie   | Familiensprache | Typ            | Farbe (Vorschlag) |
|-------------|-----------------|----------------|-------------------|
| Stuhlgang   | Gaki            | Zeitpunkt      | Gelb              |
| Pinkeln     | Lulu            | Zeitpunkt      | Grün              |
| Füttern     | Essen / Brust   | Zeitpunkt      | Blau              |
| Schlaf      | Schlaffenster   | Zeitraum       | Lila (zusätzlich) |

- **Zeitpunkt-Ereignisse** (Stuhlgang, Pinkeln, Füttern): haben genau eine Uhrzeit.
- **Zeitraum-Ereignisse** (Schlaf): haben einen Start (Einschlafen) und ein Ende
  (Aufwachen). Nur der Schlaf wird erfasst – Wachfenster werden bewusst **nicht**
  separat getrackt.

Die drei Zeitpunkt-Kategorien werden durchgängig **farblich unterschieden** (frei
konfigurierbare, aber sinnvoll vordefinierte Farben).

## 3. Screens / Navigation

```
Monatsansicht (Start)  ──►  Tagesansicht  ──►  Ereignis erfassen/bearbeiten (Dialog/Sheet)
```

### 3.1 Monatsansicht (Startbildschirm)

- Kalenderraster für den aktuellen Monat, Blättern zu Vor-/Folgemonaten.
- Pro Tag werden die **Zähler je Kategorie farbkodiert** angezeigt
  (z. B. `💛3 💚5 💙7`), also *wie oft* an dem Tag – nicht *wann*.
- Optional: kleiner Indikator für Anzahl der Schlaffenster des Tages.
- Tippen auf einen Tag öffnet die **Tagesansicht**.

### 3.2 Tagesansicht

- Chronologische Liste aller Ereignisse ("Posten") des Tages, jeweils mit **Uhrzeit**
  und farblich markierter Kategorie.
- Schlaffenster werden als Zeitraum mit Dauer dargestellt (z. B. `13:20–15:05 · 1h45`).
- **Tagesübersicht / Zusammenfassung** oben: Anzahl je Kategorie sowie
  **Anzahl der Schlaffenster** (und optional Gesamtschlafdauer) für den Tag.
- Schnelles Hinzufügen neuer Ereignisse über prominente Aktions-Buttons
  ("Gaki", "Lulu", "Füttern", "Schlaf").
- Bearbeiten/Löschen bestehender Einträge.

### 3.3 Ereignis erfassen (Bottom Sheet / Dialog)

- Kategorie wählen (bzw. vorbelegt durch den angetippten Button).
- **Zeitpunkt-Ereignis:** Uhrzeit (Default = jetzt), optionale Notiz.
- **Schlaf-Ereignis:** Startzeit + Endzeit (Default = jetzt), optionale Notiz.
  Alternativ ein laufender Schlaf, der später mit "Aufgewacht" beendet wird.

## 4. Datenmodell (Room)

```kotlin
enum class EventType { STOOL, PEE, FEED, SLEEP }

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType,
    val startTime: Instant,        // Zeitpunkt bzw. Schlaf-Beginn
    val endTime: Instant? = null,  // nur bei SLEEP gesetzt
    val note: String? = null,
    val createdAt: Instant = Instant.now()
)
```

- Ein einziger `events`-Table deckt alle Kategorien ab (`endTime` nur bei `SLEEP`).
- Aggregationen (Zähler pro Tag/Kategorie) laufen als Room-`@Query`
  (`GROUP BY date(startTime), type`).
- Zeit als UTC-`Instant` speichern, in lokaler Zeitzone anzeigen.

## 5. Architektur

- **Sprache:** Kotlin
- **UI:** Jetpack Compose (Material 3), Compose Navigation
- **Muster:** MVVM (`ViewModel` + `StateFlow`/`UiState`)
- **Persistenz:** Room (lokal, offline-first), keine Cloud im MVP
- **Nebenläufigkeit:** Kotlin Coroutines / Flow
- **DI:** Hilt (optional, aber empfohlen)
- **Min SDK:** 26 (wegen `java.time`); Target: aktuelles SDK

Schichten: `ui` (Compose Screens + ViewModels) → `domain` (Use Cases, optional) →
`data` (Room DAO + Repository).

## 6. MVP-Umfang

**Enthalten:**
1. Ereignisse der drei Zeitpunkt-Kategorien mit Uhrzeit erfassen.
2. Schlaffenster (Start/Ende) erfassen.
3. Tagesansicht mit Liste + Tageszusammenfassung.
4. Monatsansicht mit farbkodierten Tageszählern.
5. Lokale Speicherung (Room), offline nutzbar.
6. Einträge bearbeiten/löschen.

**Bewusst außen vor (Backlog):**
- Mehrere Kinder / Profile.
- Cloud-Sync, Multi-Device, geteilte Erfassung durch beide Eltern in Echtzeit.
- Statistiken/Diagramme über längere Zeiträume, Export (CSV/PDF).
- Erinnerungen/Benachrichtigungen.
- Weitere Kategorien (Medikamente, Temperatur, Gewicht ...).

## 7. Offene Punkte / Annahmen

- **Farben** (Gelb/Grün/Blau) sind ein Vorschlag aus dem Gespräch – finale Zuordnung
  offen; sollten ggf. einstellbar sein.
- **Ein Kind** angenommen (im Gespräch nur ein Baby erwähnt).
- **Füttern:** im Transkript "Brust" erwähnt – MVP behandelt Füttern als einen Typ;
  spätere Differenzierung (Brust/Flasche/Menge) möglich.
- **Schlaf-Erfassung:** Entscheidung im Gespräch: nur Schlaffenster (Einschlafen +
  Aufwachen), keine separaten Wachfenster.
- **Zeitzonen/Datumsgrenze:** Ereignisse werden dem lokalen Kalendertag zugeordnet.

---

_Automatisch erzeugt aus der Sprachnotiz; bitte inhaltlich gegenprüfen und anpassen._
