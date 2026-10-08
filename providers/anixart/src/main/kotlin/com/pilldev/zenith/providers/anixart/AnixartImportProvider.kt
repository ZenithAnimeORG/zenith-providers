package com.pilldev.zenith.providers.anixart

import com.pilldev.zenith.provider.ImportBatch
import com.pilldev.zenith.provider.ImportProvider
import com.pilldev.zenith.provider.RawImportEntry
import com.pilldev.zenith.provider.model.PluginManifest
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderId
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.UserMediaStatus

public open class AnixartImportProvider(
    override val manifest: PluginManifest = DEFAULT_MANIFEST,
) : ImportProvider {

    override val supportedExtensions: List<String> = listOf("csv")

    override suspend fun parseBackup(data: ByteArray): ProviderResult<ImportBatch> {
        return try {
            if (data.isEmpty()) {
                return ProviderResult.Success(ImportBatch(sourceName = manifest.name, entries = emptyList()))
            }
            val text = data.decodeToString()
            val lines = text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) {
                return ProviderResult.Success(ImportBatch(sourceName = manifest.name, entries = emptyList()))
            }

            val headerLine = lines.first()
            val colMap = parseHeaders(headerLine)

            val dataLines = lines.drop(1)
            val entries = mutableListOf<RawImportEntry>()

            for (line in dataLines) {
                if (line.isBlank()) continue
                val parts = parseCsvLine(line)
                if (parts.size < 2) continue

                val rusIdx = colMap["русское название"] ?: colMap["russian_name"] ?: colMap["russian"] ?: 1
                val origIdx = colMap["оригинальное название"] ?: colMap["original_name"] ?: colMap["original"] ?: 2
                val statusIdx = colMap["статус просмотра"] ?: colMap["status"] ?: (if (parts.size >= 6) 5 else parts.size - 1)
                val ratingIdx = colMap["моя оценка"] ?: colMap["оценка"] ?: colMap["rating"] ?: colMap["score"] ?: 6

                val russianName = parts.getOrNull(rusIdx)?.trim().orEmpty()
                val originalName = parts.getOrNull(origIdx)?.trim().orEmpty()

                if (russianName.isBlank() && originalName.isBlank()) continue

                val title = russianName.ifBlank { originalName }
                val originalTitle = if (russianName.isNotBlank() && originalName.isNotBlank()) originalName else null

                val statusStr = parts.getOrNull(statusIdx)?.trim().orEmpty()
                val targetStatus = mapStatus(statusStr)

                val ratingStr = parts.getOrNull(ratingIdx)?.trim()
                val rating = parseRating(ratingStr)

                val releaseYear = extractYear(title) ?: originalTitle?.let { extractYear(it) }

                entries.add(
                    RawImportEntry(
                        title = title,
                        originalTitle = originalTitle,
                        releaseYear = releaseYear,
                        watchedEpisodes = 0,
                        totalEpisodes = null,
                        targetStatus = targetStatus,
                        rating = rating,
                    ),
                )
            }

            ProviderResult.Success(ImportBatch(sourceName = manifest.name, entries = entries))
        } catch (t: Throwable) {
            ProviderResult.Failure(t.message ?: "Failed to parse Anixart CSV", t)
        }
    }

    private fun parseHeaders(headerLine: String): Map<String, Int> =
        parseCsvLine(headerLine)
            .mapIndexed { index, name -> name.trim().removeSurrounding("\"").lowercase() to index }
            .toMap()

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes) {
                    if (i + 1 == line.length || line[i + 1] == ',') {
                        inQuotes = false
                    } else if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        sb.append('"')
                    }
                } else {
                    if (sb.isEmpty()) {
                        inQuotes = true
                    } else {
                        sb.append('"')
                    }
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun mapStatus(statusStr: String): UserMediaStatus =
        when {
            statusStr.startsWith("Смотрю", ignoreCase = true) || statusStr.equals("watching", ignoreCase = true) ->
                UserMediaStatus.WATCHING
            statusStr.startsWith("В планах", ignoreCase = true) || statusStr.equals("planned", ignoreCase = true) ->
                UserMediaStatus.PLANNED
            statusStr.startsWith("Просмотрено", ignoreCase = true) || statusStr.equals("completed", ignoreCase = true) ->
                UserMediaStatus.COMPLETED
            statusStr.startsWith("Брошено", ignoreCase = true) || statusStr.equals("dropped", ignoreCase = true) ->
                UserMediaStatus.DROPPED
            statusStr.startsWith("Отложено", ignoreCase = true) || statusStr.equals("on_hold", ignoreCase = true) ->
                UserMediaStatus.ON_HOLD
            else -> UserMediaStatus.WATCHING
        }

    private fun parseRating(ratingStr: String?): Int? {
        if (ratingStr.isNullOrBlank() || ratingStr.contains("Не оценено", ignoreCase = true)) return null
        val match = RATING_REGEX.find(ratingStr)
        if (match != null) {
            return match.groupValues.getOrNull(1)?.toIntOrNull()
        }
        return ratingStr.toIntOrNull()
    }

    private fun extractYear(title: String): Int? {
        val match = YEAR_REGEX.find(title)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    public companion object {
        private val RATING_REGEX = Regex("""(\d+)\s*(?:из|/)\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val YEAR_REGEX = Regex("""\b(19\d\d|20\d\d)\b""")

        public val DEFAULT_MANIFEST: PluginManifest = PluginManifest(
            id = ProviderId("anixart"),
            name = "Anixart",
            version = "1.0.0",
            sdkVersion = "2.0",
            description = "Импорт закладок и списков из CSV экспорта Anixart",
            author = "Anixart",
            homepage = "https://anixart.tv",
            icon = "icon.png",
            capabilities = setOf(ProviderCapability.IMPORT),
            hosts = emptyList(),
            entryClass = "com.pilldev.zenith.providers.anixart.AnixartImportProviderFactory",
            settings = "settings.json",
            pros = listOf("Быстрый перенос списков из Anixart", "Поддержка стандартных CSV экспортов"),
            cons = emptyList(),
        )
    }
}
