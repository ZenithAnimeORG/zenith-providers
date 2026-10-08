@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.pilldev.zenith.providers.builtin.parser

import kotlin.io.encoding.Base64

internal object HdRezkaDecoder {
    private val QUALITY_REGEX = Regex("""^\[(.*?)]""")
    private val TAG_REGEX = Regex("<[^>]*>")
    private val CLEAN_BASE64_REGEX = Regex("""[^A-Za-z0-9+/=]""")

    private val STATIC_TRASH = listOf("//_//", "!!@@##$$%%^^&&**", "^~^", "_|_")
    private val MARKERS = listOf("#@~^", "@#@^", "#@^", "^#@")

    // Precomputed Base64 trash codes from 2-char and 3-char combinations of trash symbols
    private val TRASH_CODES_SET: Set<String> = run {
        val trashChars = listOf("@", "#", "!", "^", "$")

        fun generateCombos(
            chars: List<String>,
            length: Int
        ): List<String> {
            if (length == 0) return listOf("")
            val subCombos = generateCombos(chars, length - 1)
            return chars.flatMap { c -> subCombos.map { sc -> c + sc } }
        }
        val set = mutableSetOf<String>()
        for (i in 2..3) {
            generateCombos(trashChars, i).forEach { combo ->
                set.add(Base64.encode(combo.encodeToByteArray()))
            }
        }
        set
    }

    fun isPremiumQuality(q: String): Boolean {
        val lowQ = q.lowercase()
        return lowQ.contains("pjs-prem-quality") || lowQ.contains("prem-icon") || lowQ.contains("ultra")
    }

    fun getQualityWeight(q: String): Int {
        val lowQ = q.lowercase()
        return when {
            lowQ.contains("ultra") -> 3000
            lowQ.contains("1080p") -> 2000
            lowQ.contains("720p") -> 1000
            lowQ.contains("480p") -> 500
            lowQ.contains("360p") -> 300
            else -> q.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    fun parseQualities(
        data: String,
        includePremium: Boolean = true,
    ): Map<String, String> {
        val qualities = mutableMapOf<String, String>()
        val unescaped = data.replace("\\/", "/")
        val parts = unescaped.split(",")

        parts.forEach { part ->
            val match = QUALITY_REGEX.find(part.trim()) ?: return@forEach

            val rawQuality = match.groupValues[1]
            if (!includePremium && isPremiumQuality(rawQuality)) {
                return@forEach
            }

            val content = part.trim().substringAfter("]").trim()
            val urls = content.split(" or ")

            val lowQ = rawQuality.lowercase()
            val isHighQuality = lowQ.contains("1080p") || lowQ.contains("ultra")

            val selectedUrl =
                if (isHighQuality) {
                    // ПРЕДПОЧИТАЕМ MP4 для высокого качества, чтобы избежать 720p-адаптивности HLS
                    urls.find { it.endsWith(".mp4") && !it.contains(":hls:") } ?: urls.first()
                } else {
                    // ПРЕДПОЧИТАЕМ HLS для низких качеств
                    urls.find { it.contains(":hls:") } ?: urls.first()
                }

            val cleanQuality = rawQuality.replace(TAG_REGEX, "").trim()
            qualities[cleanQuality] = selectedUrl.trim()
        }

        return qualities
    }

    fun getBestQuality(qualities: Map<String, String>): String =
        qualities.keys
            .filter { !isPremiumQuality(it) }
            .maxByOrNull { getQualityWeight(it) }
            ?: qualities.keys.maxByOrNull { getQualityWeight(it) }
            ?: ""

    fun clearTrash(data: String): String {
        val result = data.replace("#h", "")
        if (result.isBlank()) return ""

        if (result.startsWith("[") && result.contains("http")) return result

        var working = result.split("//_//").joinToString("")

        TRASH_CODES_SET.forEach { coding -> working = working.replace(coding, "") }
        STATIC_TRASH.forEach { t -> working = working.replace(t, "") }

        MARKERS.forEach { if (working.startsWith(it)) working = working.substring(it.length) }

        val cleaned = working.replace(CLEAN_BASE64_REGEX, "")

        fun tryDecode(input: String): String? =
            try {
                val decodedBytes = Base64.decode(input)
                val decodedStr = decodedBytes.decodeToString()
                if (decodedStr.contains("http")) {
                    decodedStr
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }

        return tryDecode(cleaned.reversed()) ?: tryDecode(cleaned) ?: working
    }
}
