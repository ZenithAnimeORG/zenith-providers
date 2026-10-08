package com.pilldev.zenith.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HdRezkaStream(
    val resolution: String,
    val url: String,
    val qualities: Map<String, String>,
)
