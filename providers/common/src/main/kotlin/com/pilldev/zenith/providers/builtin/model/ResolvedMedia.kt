package com.pilldev.zenith.providers.builtin.model

public data class ResolvedMedia(
    val url: String,
    val qualities: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
)
