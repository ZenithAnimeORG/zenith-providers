package com.pilldev.zenith.providers.anixart

import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.UserMediaStatus
import com.pilldev.zenith.provider.testkit.ProviderContractTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnixartImportProviderTest : ProviderContractTestBase() {

    private val provider = AnixartImportProvider()

    @Test
    fun provider_declaresExpectedContractAndCapabilities() {
        assertEquals("anixart", provider.manifest.id.value)
        assertEquals("Anixart", provider.manifest.name)
        verifyRequiredCapabilities(provider, setOf(ProviderCapability.IMPORT))
        verifyBasicLifecycle(provider)
        assertEquals(listOf("csv"), provider.supportedExtensions)
    }

    @Test
    fun factory_createsProviderWithCustomManifest() {
        val factory = AnixartImportProviderFactory()
        val custom = factory.create(AnixartImportProvider.DEFAULT_MANIFEST)
        assertNotNull(custom)
        assertEquals("anixart", custom.manifest.id.value)
    }

    @Test
    fun parseBackup_emptyData_returnsEmptyBatch() = runTest {
        val result = provider.parseBackup(ByteArray(0))
        assertTrue(result is ProviderResult.Success)
        val batch = result.data
        assertEquals("Anixart", batch.sourceName)
        assertTrue(batch.entries.isEmpty())
    }

    @Test
    fun parseBackup_validCsvFixture_parsesAllStatusesAndRatings() = runTest {
        val csv = """
            #,Русское название,Оригинальное название,Альтернативные названия,Добавлено в избранное,Статус просмотра,Моя оценка
            1,"Re:Zero. Жизнь с нуля в альтернативном мире","Re:Zero kara Hajimeru Isekai Seikatsu","С нуля",Не добавлено,В планах,Не оценено
            2,"Адзуманга Дайо","Azumanga Daioh",Не указаны,Не добавлено,Брошено,Не оценено
            3,"Восхождение Героя щита","Tate no Yuusha no Nariagari",Не указаны,Не добавлено,Просмотрено,5 из 5
            4,"Восхождение героя щита 4","Tate no Yuusha no Nariagari Season 4",Не указаны,Не добавлено,Смотрю,4 / 5
            5,"Госпожа Кагуя 2","Kaguya-sama 2",Не указаны,Не добавлено,Отложено,5
            6,"Бродяга Кэнсин (2023)","Rurouni Kenshin (2023)",Не указаны,Не добавлено,В планах,Не оценено
            7,"Самый известный диктор","Saikyou no Shienshoku "Wajutsushi"",Не указаны,Не добавлено,В планах,Не оценено
        """.trimIndent()

        val result = provider.parseBackup(csv.encodeToByteArray())
        assertTrue(result is ProviderResult.Success)
        val batch = result.data
        assertEquals("Anixart", batch.sourceName)
        assertEquals(7, batch.entries.size)

        val entry1 = batch.entries[0]
        assertEquals("Re:Zero. Жизнь с нуля в альтернативном мире", entry1.title)
        assertEquals("Re:Zero kara Hajimeru Isekai Seikatsu", entry1.originalTitle)
        assertEquals(UserMediaStatus.PLANNED, entry1.targetStatus)
        assertNull(entry1.rating)

        val entry2 = batch.entries[1]
        assertEquals("Адзуманга Дайо", entry2.title)
        assertEquals("Azumanga Daioh", entry2.originalTitle)
        assertEquals(UserMediaStatus.DROPPED, entry2.targetStatus)

        val entry3 = batch.entries[2]
        assertEquals("Восхождение Героя щита", entry3.title)
        assertEquals(UserMediaStatus.COMPLETED, entry3.targetStatus)
        assertEquals(5, entry3.rating)

        val entry4 = batch.entries[3]
        assertEquals(UserMediaStatus.WATCHING, entry4.targetStatus)
        assertEquals(4, entry4.rating)

        val entry5 = batch.entries[4]
        assertEquals(UserMediaStatus.ON_HOLD, entry5.targetStatus)
        assertEquals(5, entry5.rating)

        val entry6 = batch.entries[5]
        assertEquals(2023, entry6.releaseYear)

        val entry7 = batch.entries[6]
        assertEquals("Самый известный диктор", entry7.title)
    }

    @Test
    fun parseBackup_englishHeaders_parsesCorrectly() = runTest {
        val csv = """
            id,russian_name,original_name,status,rating
            1,Название,Original,watching,10
            2,Второе,Second,completed,8
        """.trimIndent()

        val result = provider.parseBackup(csv.encodeToByteArray())
        assertTrue(result is ProviderResult.Success)
        val batch = result.data
        assertEquals(2, batch.entries.size)
        assertEquals("Название", batch.entries[0].title)
        assertEquals("Original", batch.entries[0].originalTitle)
        assertEquals(UserMediaStatus.WATCHING, batch.entries[0].targetStatus)
        assertEquals(10, batch.entries[0].rating)
        assertEquals(UserMediaStatus.COMPLETED, batch.entries[1].targetStatus)
        assertEquals(8, batch.entries[1].rating)
    }
}
