package com.pilldev.zenith.providers.builtin.model

import com.pilldev.zenith.domain.model.VideoSource

public data class ParserResult(
    val sources: List<VideoSource>,
    val hasPartialFailures: Boolean = false,
)
