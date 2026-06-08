package com.nedrichards.agileprices

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun settingsDefaultsWhenPreferencesAreEmpty() = runTest {
        val store = createStore()

        val settings = store.settings.firstValue()

        assertNull(settings.selectedRegionCode)
        assertNull(settings.selectedTariffCode)
        assertEquals(SettingsStore.defaultLoadDurationMinutes, settings.loadDurationMinutes)
        assertEquals(SettingsStore.defaultSearchHorizonMinutes, settings.searchHorizonMinutes)
        assertEquals(emptyList<PriceWindow>(), settings.cachedPrices)
        assertNull(settings.fetchedAt)
        assertNull(settings.lastRefreshMessage)
    }

    @Test
    fun selectedTariffAndControlsPersistWithBounds() = runTest {
        val store = createStore()

        store.saveSelectedTariff(regionCode = "_C", tariffCode = "E-1R-AGILE-26-05-01-C")
        store.saveLoadDurationMinutes(999)
        store.saveSearchHorizonMinutes(1)
        store.saveRefreshMessage("Waiting for rates")

        val settings = store.settings.firstValue()

        assertEquals("_C", settings.selectedRegionCode)
        assertEquals("E-1R-AGILE-26-05-01-C", settings.selectedTariffCode)
        assertEquals(480, settings.loadDurationMinutes)
        assertEquals(60, settings.searchHorizonMinutes)
        assertEquals("Waiting for rates", settings.lastRefreshMessage)
    }

    @Test
    fun cachedPricesPersistSortedWithRefreshMetadata() = runTest {
        val store = createStore()
        val fetchedAt = Instant.parse("2026-06-05T12:00:00Z")

        store.saveCache(
            prices = listOf(
                PriceWindow(
                    validFrom = Instant.parse("2026-06-05T12:30:00Z"),
                    validTo = Instant.parse("2026-06-05T13:00:00Z"),
                    pricePencePerKwh = 8.2,
                ),
                PriceWindow(
                    validFrom = Instant.parse("2026-06-05T12:00:00Z"),
                    validTo = Instant.parse("2026-06-05T12:30:00Z"),
                    pricePencePerKwh = -1.5,
                ),
            ),
            fetchedAt = fetchedAt,
            message = null,
        )

        val settings = store.settings.firstValue()

        assertEquals(fetchedAt, settings.fetchedAt)
        assertNull(settings.lastRefreshMessage)
        assertEquals(2, settings.cachedPrices.size)
        assertEquals(Instant.parse("2026-06-05T12:00:00Z"), settings.cachedPrices[0].validFrom)
        assertEquals(-1.5, settings.cachedPrices[0].pricePencePerKwh, 0.0001)
        assertEquals(Instant.parse("2026-06-05T12:30:00Z"), settings.cachedPrices[1].validFrom)
        assertEquals(8.2, settings.cachedPrices[1].pricePencePerKwh, 0.0001)
    }

    @Test
    fun selectedTariffClearsCachedPricesAndRefreshMetadata() = runTest {
        val store = createStore()
        store.saveCache(
            prices = listOf(
                PriceWindow(
                    validFrom = Instant.parse("2026-06-05T12:00:00Z"),
                    validTo = Instant.parse("2026-06-05T12:30:00Z"),
                    pricePencePerKwh = 8.2,
                ),
            ),
            fetchedAt = Instant.parse("2026-06-05T12:00:00Z"),
            message = "Old message",
        )

        store.saveSelectedTariff(regionCode = "_D", tariffCode = "E-1R-AGILE-26-05-01-D")
        val settings = store.settings.firstValue()

        assertEquals("_D", settings.selectedRegionCode)
        assertEquals("E-1R-AGILE-26-05-01-D", settings.selectedTariffCode)
        assertEquals(emptyList<PriceWindow>(), settings.cachedPrices)
        assertNull(settings.fetchedAt)
        assertNull(settings.lastRefreshMessage)
    }

    @Test
    fun corruptCachedPricesAndFetchedAtRecoverToSafeDefaults() = runTest {
        val (store, dataStore) = createStoreWithDataStore()
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("selectedRegionCode")] = "_C"
            preferences[stringPreferencesKey("selectedTariffCode")] = "E-1R-AGILE-26-05-01-C"
            preferences[stringPreferencesKey("cachedPriceWindows")] = "not valid json"
            preferences[stringPreferencesKey("fetchedAt")] = "not an instant"
            preferences[stringPreferencesKey("lastRefreshMessage")] = "Still readable"
        }

        val settings = store.settings.firstValue()

        assertEquals("_C", settings.selectedRegionCode)
        assertEquals("E-1R-AGILE-26-05-01-C", settings.selectedTariffCode)
        assertEquals(emptyList<PriceWindow>(), settings.cachedPrices)
        assertNull(settings.fetchedAt)
        assertEquals("Still readable", settings.lastRefreshMessage)
    }

    private fun TestScope.createStore(): SettingsStore =
        createStoreWithDataStore().first

    private fun TestScope.createStoreWithDataStore(): Pair<SettingsStore, DataStore<Preferences>> {
        val file = temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
        file.delete()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        return SettingsStore(dataStore) to dataStore
    }

    private suspend fun kotlinx.coroutines.flow.Flow<AgileSettings>.firstValue(): AgileSettings =
        first()
}
