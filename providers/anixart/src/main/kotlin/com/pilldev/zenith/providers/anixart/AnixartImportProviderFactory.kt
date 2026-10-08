package com.pilldev.zenith.providers.anixart

import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.provider.ZenithProviderFactory
import com.pilldev.zenith.provider.model.PluginManifest

public class AnixartImportProviderFactory : ZenithProviderFactory {
    override fun create(manifest: PluginManifest): ZenithProvider =
        AnixartImportProvider(manifest)
}
