package com.pilldev.zenith.providers.shikimori

import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.provider.ZenithProviderFactory
import com.pilldev.zenith.provider.model.PluginManifest

public class ShikimoriProviderFactory : ZenithProviderFactory {
    override fun create(manifest: PluginManifest): ZenithProvider =
        ShikimoriProvider(manifest)
}
