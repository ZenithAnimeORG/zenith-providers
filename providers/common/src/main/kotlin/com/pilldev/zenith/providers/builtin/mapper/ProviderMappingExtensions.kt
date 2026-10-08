package com.pilldev.zenith.providers.builtin.mapper

import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.ExternalSubtitle
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.provider.model.ProviderEpisode
import com.pilldev.zenith.provider.model.ProviderSubtitle
import com.pilldev.zenith.provider.model.ProviderTranslationType
import com.pilldev.zenith.provider.model.ProviderVideoSource

public fun VideoSource.toProviderVideoSource(): ProviderVideoSource =
    ProviderVideoSource(
        name = name,
        translationName = translationName,
        translationType = translationType.toProviderTranslationType(),
        episodes = episodes.map { it.toProviderEpisode() },
        qualities = qualities,
    )

public fun EpisodeSource.toProviderEpisode(): ProviderEpisode =
    ProviderEpisode(
        number = number,
        url = url,
        quality = quality,
        providerId = providerId,
    )

public fun TranslationType.toProviderTranslationType(): ProviderTranslationType =
    when (this) {
        TranslationType.DUB -> ProviderTranslationType.DUB
        TranslationType.SUB -> ProviderTranslationType.SUB
        TranslationType.VO -> ProviderTranslationType.VO
        TranslationType.UNKNOWN -> ProviderTranslationType.UNKNOWN
    }

public fun ExternalSubtitle.toProviderSubtitle(): ProviderSubtitle =
    ProviderSubtitle(
        fileId = fileId,
        language = language,
        releaseName = releaseName,
        directUrl = directUrl,
        provider = provider,
        isHearingImpaired = isHearingImpaired,
        isAiTranslated = isAiTranslated,
        downloadCount = downloadCount,
    )
