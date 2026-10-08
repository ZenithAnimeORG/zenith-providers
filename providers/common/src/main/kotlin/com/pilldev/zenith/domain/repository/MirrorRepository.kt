package com.pilldev.zenith.domain.repository

import com.pilldev.zenith.domain.model.MirrorEvaluation
import com.pilldev.zenith.provider.model.ProviderMirrorSpec
import kotlinx.coroutines.flow.StateFlow

public interface MirrorRepository {
    public fun isAutoMirror(providerId: String): StateFlow<Boolean>

    public fun setAutoMirror(
        providerId: String,
        auto: Boolean
    )

    public fun getManualMirror(providerId: String): StateFlow<String?>

    public fun setManualMirror(
        providerId: String,
        mirror: String?
    )

    public suspend fun getEffectiveMirror(providerId: String): String

    public fun observeMirrorEvaluations(providerId: String): StateFlow<List<MirrorEvaluation>>

    public suspend fun probeMirrors(
        providerId: String,
        forceRefresh: Boolean = false
    ): List<MirrorEvaluation>

    public fun getAvailableMirrors(providerId: String): List<ProviderMirrorSpec>
}
