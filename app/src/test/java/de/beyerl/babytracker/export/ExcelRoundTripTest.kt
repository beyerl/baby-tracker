package de.beyerl.babytracker.export

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.data.SleepMarker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The importer uses android.util.Xml, hence Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExcelRoundTripTest {

    private fun millis(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun exportThenImport_keepsEventsAndSleepMarkers() {
        val events = listOf(
            Event(type = EventType.FEED, startTime = millis("2026-09-14T06:10"), note = "links"),
            Event(
                type = EventType.SLEEP,
                startTime = millis("2026-09-14T19:30"),
                endTime = millis("2026-09-14T23:10"),
                sleepMarker = SleepMarker.BEDTIME,
            ),
            Event(
                type = EventType.SLEEP,
                startTime = millis("2026-09-14T23:40"),
                endTime = millis("2026-09-15T06:45"),
                sleepMarker = SleepMarker.WAKE_UP,
            ),
            Event(type = EventType.SLEEP, startTime = millis("2026-09-15T13:00"), endTime = millis("2026-09-15T14:30")),
            Event(
                type = EventType.SLEEP,
                startTime = millis("2026-09-15T20:00"),
                endTime = millis("2026-09-16T07:00"),
                sleepMarker = SleepMarker.BOTH,
            ),
        )

        val out = ByteArrayOutputStream()
        ExcelExporter.write(events, out)
        val imported = ExcelImporter.read(ByteArrayInputStream(out.toByteArray()))

        val expected = events.sortedBy { it.startTime }
        assertEquals(expected.size, imported.size)
        expected.zip(imported).forEach { (e, i) ->
            assertEquals(e.type, i.type)
            assertEquals(e.startTime, i.startTime)
            assertEquals(e.endTime, i.endTime)
            assertEquals(e.note, i.note)
            assertEquals(e.sleepMarker, i.sleepMarker)
        }
    }

    @Test
    fun import_fileWithoutMarkerColumn_marksSleepNone() {
        val sheet = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
            row(1, "Datum", "Start", "Ende", "Kategorie", "Dauer (Min.)", "Notiz") +
            row(2, "2026-09-14", "19:30", "06:45", "Schlaf", "675", "") +
            "</sheetData></worksheet>"
        val file = ByteArrayOutputStream()
        ZipOutputStream(file).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray())
            zip.closeEntry()
        }

        val imported = ExcelImporter.read(ByteArrayInputStream(file.toByteArray())).single()

        assertEquals(EventType.SLEEP, imported.type)
        assertEquals(millis("2026-09-15T06:45"), imported.endTime)
        assertEquals(SleepMarker.NONE, imported.sleepMarker)
    }

    private fun row(index: Int, vararg cells: String): String =
        cells.mapIndexed { col, value -> """<c r="${'A' + col}$index" t="inlineStr"><is><t>$value</t></is></c>""" }
            .joinToString("", prefix = """<row r="$index">""", postfix = "</row>")
}
