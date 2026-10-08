package com.pilldev.zenith.providers.builtin.resolver

import io.ktor.http.decodeURLQueryComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class KodikMetadata(
    val id: String,
    val type: String,
    val hash: String,
    val paramsMap: Map<String, String>,
    val urlParamsStr: String,
)

@OptIn(ExperimentalEncodingApi::class)
internal object KodikCipher {
    private val ID_PATTERNS = listOf("videoId", "video_id", "id", "serialId")
    private val ID_STR_REGEXES = ID_PATTERNS.map { Regex("(?:(?:var|let|const)\\s+)?$it\\s*=\\s*[\"']([^\"']+)[\"']") }
    private val ID_DIGIT_REGEXES = ID_PATTERNS.map { Regex("(?:(?:var|let|const)\\s+)?$it\\s*=\\s*(\\d+)") }

    private val TYPE_PATTERNS = listOf("videoType", "type")
    private val TYPE_REGEXES = TYPE_PATTERNS.map { Regex("(?:(?:var|let|const)\\s+)?$it\\s*=\\s*[\"']([^\"']+)[\"']") }

    private val HASH_PATTERNS = listOf("hash", "videoHash", "serialHash")
    private val HASH_STR_REGEXES = HASH_PATTERNS.map { Regex("(?:(?:var|let|const)\\s+)?$it\\s*=\\s*[\"']([^\"']+)[\"']") }
    private val HASH_KEY_REGEXES = HASH_PATTERNS.map { Regex("['\"]$it['\"]\\s*:\\s*['\"]([^'\"]+)['\"]") }

    private val VINFO_ID_REGEX = Regex("vInfo\\.id\\s*=\\s*['\"]([^'\"]+)['\"]|vInfo\\.id\\s*=\\s*(\\d+)")
    private val VINFO_HASH_REGEX = Regex("vInfo\\.hash\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val VINFO_TYPE_REGEX = Regex("vInfo\\.type\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val VINFO_OBJ_REGEX = Regex("(?:(?:var|let|const)\\s+)?vInfo\\s*=\\s*(\\{.*?\\})", RegexOption.DOT_MATCHES_ALL)

    private val URL_PARAMS_STR_REGEX = Regex("(?:(?:var|let|const)\\s+)?urlParams\\s*=\\s*['\"](\\{.*?\\})['\"]", RegexOption.DOT_MATCHES_ALL)
    private val URL_PARAMS_OBJ_REGEX = Regex("(?:(?:var|let|const)\\s+)?urlParams\\s*=\\s*(\\{.*?\\})", RegexOption.DOT_MATCHES_ALL)
    private val KV_PATTERN = Regex("['\"]?(\\w+)['\"]?\\s*:\\s*['\"]([^'\"]+)['\"]")

    private val VAR_REGEXES = listOf("domain", "d_sign", "pd", "pd_sign", "ref", "ref_sign").associateWith { name ->
        Regex("(?:(?:var|let|const)\\s+)?$name\\s*=\\s*[\"']([^\"']+)[\"']")
    }

    fun decodeKodik(input: String): String =
        try {
            var str = input.trim()
            val pad = (4 - (str.length % 4)) % 4
            if (pad > 0) {
                str += "=".repeat(pad)
            }
            val shifted =
                buildString(str.length) {
                    for (char in str) {
                        when (char) {
                            in 'a'..'z' -> {
                                val newCode = char.code + 18
                                append(if (newCode <= 'z'.code) newCode.toChar() else (newCode - 26).toChar())
                            }
                            in 'A'..'Z' -> {
                                val newCode = char.code + 18
                                append(if (newCode <= 'Z'.code) newCode.toChar() else (newCode - 26).toChar())
                            }
                            else -> append(char)
                        }
                    }
                }
            val decodedBytes = Base64.decode(shifted)
            var decoded = decodedBytes.decodeToString()
            if (decoded.startsWith("//")) decoded = "https:$decoded"
            decoded
        } catch (_: Exception) {
            ""
        }

    fun extractMetadata(
        html: String,
        url: String,
        json: Json,
    ): KodikMetadata? {
        var id: String? = null
        var type: String? = null
        var hash: String? = null

        // 1. Check vInfo assignments first
        val vInfoIdMatch = VINFO_ID_REGEX.find(html)
        if (vInfoIdMatch != null) {
            id = vInfoIdMatch.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: vInfoIdMatch.groupValues.getOrNull(2)
        }

        val vInfoHashMatch = VINFO_HASH_REGEX.find(html)
        if (vInfoHashMatch != null) hash = vInfoHashMatch.groupValues.getOrNull(1)

        val vInfoTypeMatch = VINFO_TYPE_REGEX.find(html)
        if (vInfoTypeMatch != null) type = vInfoTypeMatch.groupValues.getOrNull(1)

        // 2. Extract from standard JS variables if not in vInfo
        if (id == null) {
            for (regex in ID_STR_REGEXES) {
                val match = regex.find(html)
                if (match != null) {
                    id = match.groupValues.getOrNull(1)
                    break
                }
            }
        }
        if (id == null) {
            for (regex in ID_DIGIT_REGEXES) {
                val match = regex.find(html)
                if (match != null) {
                    id = match.groupValues.getOrNull(1)
                    break
                }
            }
        }

        if (type == null) {
            for (regex in TYPE_REGEXES) {
                val match = regex.find(html)
                if (match != null) {
                    type = match.groupValues.getOrNull(1)
                    break
                }
            }
        }

        if (hash == null) {
            for (regex in HASH_STR_REGEXES) {
                val match = regex.find(html)
                if (match != null) {
                    hash = match.groupValues.getOrNull(1)
                    break
                }
            }
        }
        if (hash == null) {
            for (regex in HASH_KEY_REGEXES) {
                val match = regex.find(html)
                if (match != null) {
                    hash = match.groupValues.getOrNull(1)
                    break
                }
            }
        }

        // 3. Check for vInfo JSON object
        val vInfoObjMatch = VINFO_OBJ_REGEX.find(html)
        if (vInfoObjMatch != null) {
            try {
                val vInfoJson = vInfoObjMatch.groupValues.getOrNull(1) ?: ""
                val vInfo = json.decodeFromString<JsonObject>(vInfoJson)
                if (id == null) {
                    id = vInfo["id"]?.jsonPrimitive?.content
                        ?: vInfo["videoId"]?.jsonPrimitive?.content
                        ?: vInfo["serialId"]?.jsonPrimitive?.content
                }
                if (type == null) type = vInfo["type"]?.jsonPrimitive?.content ?: vInfo["videoType"]?.jsonPrimitive?.content
                if (hash == null) {
                    hash = vInfo["hash"]?.jsonPrimitive?.content
                        ?: vInfo["videoHash"]?.jsonPrimitive?.content
                        ?: vInfo["serialHash"]?.jsonPrimitive?.content
                }
            } catch (_: Exception) {
                // ignore
            }
        }

        // 4. Extract urlParams (JSON string literal or JS object literal)
        val urlParamsMatch = URL_PARAMS_STR_REGEX.find(html)
        var urlParamsStr = urlParamsMatch?.groupValues?.getOrNull(1)?.replace("\\'", "'")
        if (urlParamsStr == null) {
            val objMatch = URL_PARAMS_OBJ_REGEX.find(html)
            if (objMatch != null) {
                urlParamsStr = objMatch.groupValues.getOrNull(1)
            }
        }

        // Fallback for ID from URL path if not found in JS
        if (id == null) {
            val pathParts = url.split("/")
            id = pathParts.find { it.all { c -> c.isDigit() } && it.length >= 4 }
        }

        // Fallback for type from URL
        if (type == null) {
            type = when {
                url.contains("/seria/") -> "seria"
                url.contains("/serial/") -> "serial"
                url.contains("/video/") -> "video"
                url.contains("/movie/") -> "movie"
                else -> "seria"
            }
        }

        // Fallback for hash from URL path if not found in JS
        if (hash == null) {
            val pathParts = url.split("/")
            hash = pathParts.find { it.length in 30..40 && !it.contains(".") }?.substringBefore("?")
        }

        if (id == null || hash == null) {
            return null
        }

        val paramsMap = mutableMapOf<String, String>()
        if (!urlParamsStr.isNullOrBlank() && urlParamsStr.trim().startsWith("{")) {
            try {
                val obj = json.decodeFromString<JsonObject>(urlParamsStr)
                obj.forEach { (k, v) ->
                    val content = when (v) {
                        is kotlinx.serialization.json.JsonPrimitive -> v.content
                        else -> v.toString()
                    }
                    paramsMap[k] = content
                }
            } catch (_: Exception) {
                KV_PATTERN.findAll(urlParamsStr).forEach { matchResult ->
                    val key = matchResult.groupValues.getOrNull(1)
                    val value = matchResult.groupValues.getOrNull(2)
                    if (!key.isNullOrEmpty() && !value.isNullOrEmpty()) {
                        paramsMap[key] = value
                    }
                }
            }
        }

        val getVar: (String) -> String? = { name ->
            VAR_REGEXES[name]?.find(html)?.groupValues?.getOrNull(1)
        }
        val domainVar = getVar("domain") ?: "kodikplayer.com"
        val dSignVar = getVar("d_sign")
        val pdVar = getVar("pd") ?: domainVar
        val pdSignVar = getVar("pd_sign") ?: dSignVar
        val refVar = getVar("ref") ?: ""
        val refSignVar = getVar("ref_sign")

        if (dSignVar != null && !paramsMap.containsKey("d_sign")) paramsMap["d_sign"] = dSignVar
        if (pdSignVar != null && !paramsMap.containsKey("pd_sign")) paramsMap["pd_sign"] = pdSignVar
        if (refSignVar != null && !paramsMap.containsKey("ref_sign")) paramsMap["ref_sign"] = refSignVar
        if (!paramsMap.containsKey("d")) paramsMap["d"] = domainVar
        if (!paramsMap.containsKey("pd")) paramsMap["pd"] = pdVar
        if (!paramsMap.containsKey("ref")) paramsMap["ref"] = refVar

        paramsMap["ref"]?.let { r ->
            if (r.contains("%")) {
                paramsMap["ref"] = try {
                    r.decodeURLQueryComponent()
                } catch (_: Exception) {
                    r
                }
            }
        }

        paramsMap["bad_user"] = "false"
        paramsMap["cdn_is_working"] = "true"

        return KodikMetadata(id, type, hash, paramsMap, urlParamsStr ?: "{}")
    }
}
