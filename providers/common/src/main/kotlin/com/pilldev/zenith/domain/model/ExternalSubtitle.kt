package com.pilldev.zenith.domain.model

public data class ExternalSubtitle(
    val fileId: Long = 0L,
    val language: String,
    val releaseName: String,
    val isHearingImpaired: Boolean = false,
    val isAiTranslated: Boolean = false,
    val downloadCount: Int = 0,
    val directUrl: String? = null,
    val provider: String = "OpenSubtitles",
)
