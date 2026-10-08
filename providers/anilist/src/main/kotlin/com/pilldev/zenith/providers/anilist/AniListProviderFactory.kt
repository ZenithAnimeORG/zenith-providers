package com.pilldev.zenith.providers.anilist

import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.provider.ZenithProviderFactory
import com.pilldev.zenith.provider.model.PluginManifest

public class AniListProviderFactory : ZenithProviderFactory {
    override fun create(manifest: PluginManifest): ZenithProvider =
        AniListProvider(manifest)
}
