package com.stockflow.imports.application

import com.stockflow.common.errors.InvalidImportException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class CsvPackageReader {
    companion object {
        private const val MAX_ENTRIES = 40
        private const val MAX_UNCOMPRESSED_BYTES = 30L * 1024L * 1024L

        fun readZip(bytes: ByteArray): Map<String, ByteArray> {
            val entries = linkedMapOf<String, ByteArray>()
            var totalBytes = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entries.size >= MAX_ENTRIES) {
                        throw InvalidImportException("ZIP contains more than $MAX_ENTRIES files")
                    }
                    val normalized = entry.name.replace('\\', '/').removePrefix("/")
                    if (normalized.contains("../")) {
                        throw InvalidImportException("ZIP contains an unsafe path: ${entry.name}")
                    }
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw InvalidImportException("ZIP expands beyond the 30 MB import limit")
                        }
                        output.write(buffer, 0, read)
                    }
                    entries[normalized] = output.toByteArray()
                }
            }
            if (entries.isEmpty()) throw InvalidImportException("The uploaded ZIP is empty")
            return entries
        }

        fun requiredEntry(entries: Map<String, ByteArray>, suffix: String): Pair<String, ByteArray> {
            val matches = entries.filterKeys { it.endsWith(suffix) }
            if (matches.isEmpty()) throw InvalidImportException("Required file '$suffix' is missing")
            if (matches.size > 1) throw InvalidImportException("ZIP contains multiple files ending with '$suffix'")
            return matches.entries.first().let { it.key to it.value }
        }

        fun parseCsv(bytes: ByteArray): List<CsvRow> {
            val text = bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
            val records = parseRecords(text)
            if (records.isEmpty()) throw InvalidImportException("CSV file has no header")
            val header = records.first().map { it.trim() }
            if (header.any { it.isBlank() } || header.toSet().size != header.size) {
                throw InvalidImportException("CSV header contains blank or duplicate columns")
            }
            return records.drop(1).mapIndexedNotNull { index, values ->
                if (values.all { it.isBlank() }) return@mapIndexedNotNull null
                if (values.size != header.size) {
                    throw InvalidImportException(
                        "CSV row ${index + 2} has ${values.size} columns; expected ${header.size}"
                    )
                }
                CsvRow(index + 2L, header.zip(values).toMap())
            }
        }

        private fun parseRecords(text: String): List<List<String>> {
            val records = mutableListOf<List<String>>()
            var record = mutableListOf<String>()
            val field = StringBuilder()
            var quoted = false
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                when {
                    ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    ch == '"' -> quoted = !quoted
                    ch == ',' && !quoted -> {
                        record.add(field.toString())
                        field.setLength(0)
                    }
                    (ch == '\n' || ch == '\r') && !quoted -> {
                        if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        record.add(field.toString())
                        field.setLength(0)
                        records.add(record)
                        record = mutableListOf()
                    }
                    else -> field.append(ch)
                }
                i++
            }
            if (quoted) throw InvalidImportException("CSV contains an unclosed quoted field")
            if (field.isNotEmpty() || record.isNotEmpty()) {
                record.add(field.toString())
                records.add(record)
            }
            return records
        }
    }
}
