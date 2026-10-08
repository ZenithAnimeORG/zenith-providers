package com.pilldev.zenith.providers.builtin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class KodikResponse(
    val results: List<KodikResult> = emptyList(),
)

@Serializable
public data class KodikResult(
    val title: String = "",
    val link: String = "",
    @SerialName("last_episode") val lastEpisode: Int? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
    val translation: KodikTranslation? = null,
    val seasons: Map<String, KodikSeason>? = null,
    val screenshots: List<String>? = null,
)

@Serializable
public data class KodikTranslation(
    val id: Int,
    val title: String,
    val type: String,
)

@Serializable
public data class KodikSeason(
    val episodes: Map<String, String>,
)
