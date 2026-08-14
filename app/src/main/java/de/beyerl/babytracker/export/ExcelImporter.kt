package de.beyerl.babytracker.export

import android.util.Xml
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * Reads events back from an `.xlsx` produced by [ExcelExporter] (and lightly
 * edited variants). Parses the OOXML zip with the platform pull parser, so it
 * stays dependency-free like the exporter.
 *
 * Expected columns: Datum | Start | Ende | Kategorie | Dauer (Min.) | Notiz.
 * Rows whose date/time/category can't be parsed (header, blanks) are skipped.
 * Note: if the file was re-saved by Excel it may store dates as numeric serials
 * with a style rather than "yyyy-MM-dd" text; those rows are then skipped.
 */
object ExcelImporter {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun read(input: InputStream): List<Event> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory &&
                    (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/"))
                ) {
                    parts[name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val shared = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetName = parts.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .minOrNull() ?: return emptyList()

        return rowsToEvents(parseSheet(parts.getValue(sheetName), shared))
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = ArrayList<String>()
        val parser = newParser(bytes)
        var current: StringBuilder? = null
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) current?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> {
                        result.add(current?.toString() ?: "")
                        current = null
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    /** Parses the worksheet into rows keyed by 0-based column index. */
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Map<Int, String>> {
        val rows = ArrayList<Map<Int, String>>()
        val parser = newParser(bytes)
        var cells: HashMap<Int, String>? = null
        var colIndex = -1
        var cellType: String? = null
        var inValue = false
        var inInlineText = false
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> cells = HashMap()
                    "c" -> {
                        colIndex = columnOf(parser.getAttributeValue(null, "r"))
                        cellType = parser.getAttributeValue(null, "t")
                    }
                    "v" -> {
                        inValue = true
                        text.setLength(0)
                    }
                    "t" -> {
                        inInlineText = true
                        text.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> if (inValue || inInlineText) text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> {
                        inValue = false
                        val raw = text.toString()
                        val value = if (cellType == "s") {
                            shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                        } else {
                            raw
                        }
                        if (colIndex >= 0) cells?.put(colIndex, value)
                    }
                    "t" -> if (inInlineText) {
                        inInlineText = false
                        if (colIndex >= 0) cells?.put(colIndex, text.toString())
                    }
                    "c" -> {
                        colIndex = -1
                        cellType = null
                    }
                    "row" -> {
                        cells?.let { rows.add(it) }
                        cells = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun rowsToEvents(rows: List<Map<Int, String>>): List<Event> {
        val events = ArrayList<Event>()
        for (row in rows) {
            val date = row[0]?.trim().orEmpty()
                .let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() } ?: continue
            val start = row[1]?.trim().orEmpty()
                .let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() } ?: continue
            val type = categoryOf(row[3]?.trim().orEmpty()) ?: continue

            val startDt = date.atTime(start)
            val startMillis = startDt.atZone(zone).toInstant().toEpochMilli()

            val endMillis = row[2]?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() }
                ?.let { endTime ->
                    var endDt = date.atTime(endTime)
                    if (!endDt.isAfter(startDt)) endDt = endDt.plusDays(1) // overnight
                    endDt.atZone(zone).toInstant().toEpochMilli()
                }

            events.add(
                Event(
                    type = type,
                    startTime = startMillis,
                    endTime = endMillis,
                    note = row[5]?.trim()?.ifBlank { null },
                ),
            )
        }
        return events
    }

    private fun categoryOf(label: String): EventType? = when (label.lowercase()) {
        "stuhlgang", "stool" -> EventType.STOOL
        "pinkeln", "pee" -> EventType.PEE
        "füttern", "fuettern", "feed" -> EventType.FEED
        "schlaf", "sleep" -> EventType.SLEEP
        else -> null
    }

    /** "A" -> 0, "B" -> 1, "AA" -> 26, using the leading letters of a cell ref. */
    private fun columnOf(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var col = 0
        for (c in ref) {
            val upper = c.uppercaseChar()
            if (upper in 'A'..'Z') col = col * 26 + (upper - 'A' + 1) else break
        }
        return col - 1
    }

    private fun newParser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }
}
