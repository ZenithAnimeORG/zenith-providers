package com.pilldev.zenith.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class AniSkipResponse(
    @SerialName("found") val found: Boolean = false,
    @SerialName("results") val results: List<AniSkipResult> = emptyList(),
)

@Serializable
public data class AniSkipResult(
    @SerialName("interval") val interval: Interval? = null,
    @SerialName("skipType") val skipType: String = "",
    @SerialName("episodeLength") val episodeLength: Double? = null,
    @SerialName("start") val start: Double? = null,
    @SerialName("end") val end: Double? = null,
)

@Serializable
public data class Interval(
    @SerialName("startTime") val startTime: Double,
    @SerialName("endTime") val endTime: Double,
)
