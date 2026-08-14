package de.beyerl.babytracker.export

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes events into a minimal, dependency-free `.xlsx` (OOXML) workbook.
 *
 * An xlsx file is just a ZIP of XML parts, so we emit the smallest valid set of
 * parts using inline strings. This avoids pulling in Apache POI, which would
 * bloat the APK by several megabytes and inflate the method count.
 */
object ExcelExporter {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val headers = listOf("Datum", "Start", "Ende", "Kategorie", "Dauer (Min.)", "Notiz")

    /** Serializes [events] (sorted by start time) as an xlsx stream into [out]. */
    fun write(events: List<Event>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putEntry("[Content_Types].xml", CONTENT_TYPES)
            zip.putEntry("_rels/.rels", ROOT_RELS)
            zip.putEntry("xl/workbook.xml", WORKBOOK)
            zip.putEntry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putEntry("xl/worksheets/sheet1.xml", sheet(events))
        }
    }

    private fun sheet(events: List<Event>): String {
        val rows = StringBuilder()
        rows.append(row(1, headers.map { CellValue.Text(it) }))
        events.sortedBy { it.startTime }.forEachIndexed { i, e ->
            val rowIndex = i + 2
            val start = Instant.ofEpochMilli(e.startTime).atZone(zone)
            val endMillis = e.endTime
            val end = endMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
            val durationMin = endMillis?.let { (it - e.startTime).coerceAtLeast(0) / 60000L }
            rows.append(
                row(
                    rowIndex,
                    listOf(
                        CellValue.Text(start.format(dateFmt)),
                        CellValue.Text(start.format(timeFmt)),
                        CellValue.Text(end?.format(timeFmt) ?: ""),
                        CellValue.Text(label(e.type)),
                        durationMin?.let { CellValue.Number(it) } ?: CellValue.Text(""),
                        CellValue.Text(e.note ?: ""),
                    ),
                ),
            )
        }
        return SHEET_HEAD + rows + SHEET_TAIL
    }

    private fun label(type: EventType): String = when (type) {
        EventType.STOOL -> "Stuhlgang"
        EventType.PEE -> "Pinkeln"
        EventType.FEED -> "Füttern"
        EventType.SLEEP -> "Schlaf"
    }

    private sealed interface CellValue {
        data class Text(val value: String) : CellValue
        data class Number(val value: Long) : CellValue
    }

    private fun row(index: Int, cells: List<CellValue>): String {
        val sb = StringBuilder("<row r=\"$index\">")
        cells.forEachIndexed { col, cell ->
            val ref = colName(col) + index
            when (cell) {
                is CellValue.Text ->
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(cell.value)).append("</t></is></c>")
                is CellValue.Number ->
                    sb.append("<c r=\"").append(ref).append("\"><v>").append(cell.value).append("</v></c>")
            }
        }
        sb.append("</row>")
        return sb.toString()
    }

    /** 0 -> "A", 25 -> "Z", 26 -> "AA", ... (spreadsheet column labels). */
    private fun colName(zeroBased: Int): String {
        var n = zeroBased
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, 'A' + (n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Baby Tracker" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""

    private const val SHEET_HEAD =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>"""

    private const val SHEET_TAIL = """</sheetData></worksheet>"""
}
