package com.pilldev.zenith.providers.builtin.parser

public data class HdRezkaMirrorEvaluation(
    val mirror: String,
    val pingMs: Long,
    val isWorking: Boolean,
    val hasContent: Boolean,
    val isAntiBot: Boolean = false,
    val httpStatus: Int? = null,
    val errorMessage: String? = null,
    val profile: com.pilldev.zenith.domain.model.HdRezkaMirrorProfile? = null,
    val authEvaluation: HdRezkaMirrorEvaluation? = null,
)

public data class CachedMirrorVerification(
    val evaluation: HdRezkaMirrorEvaluation,
    val timestampMs: Long,
    val hasValidStream: Boolean? = null,
)

public val HDREZKA_CANDIDATE_MIRRORS: List<String> = com.pilldev.zenith.domain.model.HdRezkaCandidateMirrors.ALL
