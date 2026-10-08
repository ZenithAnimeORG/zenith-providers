package com.pilldev.zenith.domain.model

public data class MirrorEvaluation(
    val url: String,
    val displayName: String,
    val isOnline: Boolean,
    val latencyMs: Long,
    val errorMessage: String? = null,
)
