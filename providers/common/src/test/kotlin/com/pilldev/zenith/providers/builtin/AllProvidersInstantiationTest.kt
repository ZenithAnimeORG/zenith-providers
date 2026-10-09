package com.pilldev.zenith.providers.builtin

import com.pilldev.zenith.provider.MediaSourceProvider
import com.pilldev.zenith.provider.SkipTimingsProvider
import com.pilldev.zenith.provider.SubtitleSourceProvider
import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.provider.ZenithProviderFactory
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.providers.builtin.provider.TorrentTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import com.pilldev.zenith.provider.model.PluginManifest
import com.pilldev.zenith.provider.model.ProviderId

class AllProvidersInstantiationTest {

    private val providerFactoryClassNames = listOf(
        "com.pilldev.zenith.providers.builtin.provider.HdRezkaProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.KodikProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.AniLibertyProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.YummyAnimeProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.AnitypeProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.AnimeGoProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.AnimeSkipProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.AniSkipProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.LiftProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.OpenSubtitlesProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.TheIntroDbProviderFactory",
        "com.pilldev.zenith.providers.builtin.provider.NyaaTrackerFactory",
        "com.pilldev.zenith.providers.builtin.provider.RuTorTrackerFactory",
        "com.pilldev.zenith.providers.builtin.provider.RuTrackerTrackerFactory",
    )

    @Test
    fun testAllProvidersInstantiateCleanlyViaFactory() {
        assertEquals(14, providerFactoryClassNames.size, "Must cover all 14 common providers")

        for (className in providerFactoryClassNames) {
            val clazz = Class.forName(className)
            val constructor = clazz.getDeclaredConstructor()
            val factoryInstance = constructor.newInstance() as? ZenithProviderFactory
            assertNotNull(factoryInstance, "Factory $className must implement ZenithProviderFactory")

            val dummyManifest = PluginManifest(
                id = ProviderId("test"),
                name = "Test",
                version = "1.0.0",
            )
            val provider: ZenithProvider = factoryInstance.create(dummyManifest)
            assertNotNull(provider, "Factory $className must create non-null provider")

            val meta = provider.metadata
            assertTrue(meta.id.value.isNotBlank(), "Provider from $className must have valid ID")
            assertTrue(meta.name.isNotBlank(), "Provider ${meta.id.value} must have valid name")
            assertTrue(meta.capabilities.isNotEmpty(), "Provider ${meta.id.value} must have capabilities")

            if (provider is MediaSourceProvider) {
                assertTrue(meta.capabilities.contains(ProviderCapability.MEDIA_SOURCE), "${meta.id.value} missing MEDIA_SOURCE capability")
            }
            if (provider is SubtitleSourceProvider) {
                assertTrue(meta.capabilities.contains(ProviderCapability.SUBTITLES), "${meta.id.value} missing SUBTITLES capability")
            }
            if (provider is SkipTimingsProvider) {
                assertTrue(meta.capabilities.contains(ProviderCapability.SKIP_TIMINGS), "${meta.id.value} missing SKIP_TIMINGS capability")
            }
            if (provider is TorrentTracker) {
                assertTrue(meta.capabilities.contains(ProviderCapability.TORRENT_SOURCE), "${meta.id.value} missing TORRENT_SOURCE capability")
            }
        }
    }
}
