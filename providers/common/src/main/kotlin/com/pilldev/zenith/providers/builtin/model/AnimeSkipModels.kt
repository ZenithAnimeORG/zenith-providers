package com.pilldev.zenith.providers.builtin.model

import kotlinx.serialization.Serializable

@Serializable
public data class AnimeSkipResponse(
    val data: AnimeSkipData? = null,
)

@Serializable
public data class AnimeSkipData(
    val findShowsByExternalId: List<AnimeSkipShow>? = null,
)

@Serializable
public data class AnimeSkipShow(
    val id: String,
    val episodes: List<AnimeSkipEpisode>? = null,
)

@Serializable
public data class AnimeSkipEpisode(
    val number: String,
    val timestamps: List<AnimeSkipTimestamp> = emptyList(),
)

@Serializable
public data class AnimeSkipTimestamp(
    val at: Double,
    val type: AnimeSkipType,
)

@Serializable
public data class AnimeSkipType(
    val name: String,
)
