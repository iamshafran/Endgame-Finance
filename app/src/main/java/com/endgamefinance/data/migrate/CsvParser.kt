package com.endgamefinance.data.migrate

/**
 * Minimal RFC-4180 CSV parser: quoted fields, embedded commas, escaped quotes
 * (""), and newlines inside quotes. No dependencies — this app avoids
 * networking/parsing libraries on principle.
 */
object CsvParser {

    /** Parses the whole document into rows of fields. Blank lines are skipped. */
    fun parse(rawText: String): List<List<String>> {
        // Strip a UTF-8 BOM — real-world exports (incl. Bluecoins) often start
        // with one, which would silently corrupt the first header name.
        val text = rawText.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        val row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        fun endField() { row.add(field.toString()); field.clear() }
        fun endRow() {
            endField()
            if (row.size > 1 || row[0].isNotBlank()) rows.add(row.toList())
            row.clear()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> { /* swallow; \n ends the row */ }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
