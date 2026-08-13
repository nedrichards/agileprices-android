package com.nedrichards.agileprices

import java.time.Instant
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AgileRepositoryTest {
    @Test
    fun configureRegionPersistsSelectionWhenInitialRatesRefreshFails() = runTest {
        val settings = FakeSettingsDataSource()
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(
                standardRatesError = OctopusApiException("Network unavailable."),
            ),
            clock = { fixedNow },
        )

        val tariff = repository.configureRegion("_C")
        val saved = settings.settings.first()

        assertEquals("E-1R-AGILE-24-10-01-C", tariff.code)
        assertEquals("_C", saved.selectedRegionCode)
        assertEquals("E-1R-AGILE-24-10-01-C", saved.selectedTariffCode)
        assertEquals(emptyList<PriceWindow>(), saved.cachedPrices)
        assertEquals("Network unavailable.", saved.lastRefreshMessage)
    }

    @Test
    fun configureRegionShowsClockHintWhenSecureConnectionFails() = runTest {
        val settings = FakeSettingsDataSource()
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(
                standardRatesError = SSLHandshakeException("Chain validation failed"),
            ),
            clock = { fixedNow },
        )

        repository.configureRegion("_C")
        val saved = settings.settings.first()

        assertEquals("_C", saved.selectedRegionCode)
        assertEquals("E-1R-AGILE-24-10-01-C", saved.selectedTariffCode)
        assertEquals(
            "Secure connection failed. Check the watch/emulator date and time, then try again.",
            saved.lastRefreshMessage,
        )
    }

    @Test
    fun refreshCancellationDoesNotPersistInternalCoroutineMessage() = runTest {
        val settings = FakeSettingsDataSource(
            initial = AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = "E-1R-AGILE-24-10-01-C",
                loadDurationMinutes = 60,
                searchHorizonMinutes = 480,
                cachedPrices = emptyList(),
                fetchedAt = null,
                lastRefreshMessage = "Previous refresh failed.",
            ),
        )
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(
                standardRatesError = CancellationException("The coroutine scope left the composition"),
            ),
            clock = { fixedNow },
        )

        try {
            repository.refresh()
            fail("Expected refresh cancellation")
        } catch (expected: CancellationException) {
            assertEquals("The coroutine scope left the composition", expected.message)
        }

        assertEquals("Previous refresh failed.", settings.settings.first().lastRefreshMessage)
    }

    @Test
    fun configureRegionSavesFetchedPricesAndRequestsSurfaceUpdate() = runTest {
        val settings = FakeSettingsDataSource()
        val surfaceUpdater = CountingSurfaceUpdater()
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(
                standardRates = listOf(
                    PriceWindow(
                        validFrom = fixedNow.plusSeconds(30 * 60),
                        validTo = fixedNow.plusSeconds(60 * 60),
                        pricePencePerKwh = 7.9,
                    ),
                    PriceWindow(
                        validFrom = fixedNow,
                        validTo = fixedNow.plusSeconds(30 * 60),
                        pricePencePerKwh = 8.1,
                    ),
                ),
            ),
            clock = { fixedNow },
            surfaceUpdater = surfaceUpdater,
        )

        repository.configureRegion("_C")
        val saved = settings.settings.first()

        assertEquals("_C", saved.selectedRegionCode)
        assertEquals("E-1R-AGILE-24-10-01-C", saved.selectedTariffCode)
        assertEquals(2, saved.cachedPrices.size)
        assertEquals(fixedNow, saved.cachedPrices.first().validFrom)
        assertEquals(fixedNow, saved.fetchedAt)
        assertEquals(null, saved.lastRefreshMessage)
        assertEquals(1, surfaceUpdater.requestCount)
    }

    @Test
    fun refreshWithSavedRegionButNoTariffConfiguresRegionWithoutThrowingOnRateFailure() = runTest {
        val settings = FakeSettingsDataSource(
            initial = AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = null,
                loadDurationMinutes = 60,
                searchHorizonMinutes = 480,
                cachedPrices = emptyList(),
                fetchedAt = null,
                lastRefreshMessage = null,
            ),
        )
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(
                standardRatesError = OctopusApiException("Network unavailable."),
            ),
            clock = { fixedNow },
        )

        repository.refresh()
        val saved = settings.settings.first()

        assertEquals("_C", saved.selectedRegionCode)
        assertEquals("E-1R-AGILE-24-10-01-C", saved.selectedTariffCode)
        assertEquals("Network unavailable.", saved.lastRefreshMessage)
    }

    @Test
    fun snapshotAfterSavedSelectionWithoutPricesIsErrorNotSetup() = runTest {
        val settings = FakeSettingsDataSource(
            initial = AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = "E-1R-AGILE-24-10-01-C",
                loadDurationMinutes = 60,
                searchHorizonMinutes = 480,
                cachedPrices = emptyList(),
                fetchedAt = null,
                lastRefreshMessage = "Network unavailable.",
            ),
        )
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(),
            clock = { fixedNow },
        )

        val snapshot = repository.snapshots.first()

        assertEquals(SnapshotStatus.Error, snapshot.status)
        assertEquals("Network unavailable.", snapshot.message)
    }

    @Test
    fun appStateCarriesSettingsAndSnapshotFromSingleSettingsCollection() = runTest {
        val settings = FakeSettingsDataSource(
            initial = AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = "E-1R-AGILE-24-10-01-C",
                loadDurationMinutes = 60,
                searchHorizonMinutes = 480,
                cachedPrices = listOf(
                    PriceWindow(
                        validFrom = fixedNow.minusSeconds(30 * 60),
                        validTo = fixedNow.plusSeconds(30 * 60),
                        pricePencePerKwh = 8.1,
                    ),
                    PriceWindow(
                        validFrom = fixedNow.plusSeconds(30 * 60),
                        validTo = fixedNow.plusSeconds(60 * 60),
                        pricePencePerKwh = 7.9,
                    ),
                ),
                fetchedAt = fixedNow,
                lastRefreshMessage = null,
            ),
        )
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(),
            clock = { fixedNow },
        )

        val appState = repository.appState.first()

        assertEquals(1, settings.collectionCount)
        assertEquals("_C", appState.settings.selectedRegionCode)
        assertEquals(SnapshotStatus.Loaded, appState.snapshot.status)
        assertEquals(8.1, appState.snapshot.currentPrice!!.pricePencePerKwh, 0.0001)
    }

    @Test
    fun snapshotCarriesCheapestContinuousBestWindow() = runTest {
        val now = fixedNow.plusSeconds(17 * 60)
        val prices = listOf(100.0, 100.0, 100.0, 1.0, 1.0, 100.0, 100.0).mapIndexed { index, price ->
            val validFrom = fixedNow.plusSeconds(index * 30L * 60L)
            PriceWindow(
                validFrom = validFrom,
                validTo = validFrom.plusSeconds(30 * 60),
                pricePencePerKwh = price,
            )
        }
        val settings = FakeSettingsDataSource(
            initial = AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = "E-1R-AGILE-24-10-01-C",
                loadDurationMinutes = 60,
                searchHorizonMinutes = 240,
                cachedPrices = prices,
                fetchedAt = fixedNow,
                lastRefreshMessage = null,
            ),
        )
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(),
            clock = { now },
        )

        val snapshot = repository.snapshots.first()
        val bestWindow = requireNotNull(snapshot.bestWindow)
        val startNowWindow = requireNotNull(snapshot.startNowWindow)
        val startTimerWindow = requireNotNull(snapshot.startTimerWindow)
        val finishTimerWindow = requireNotNull(snapshot.finishTimerWindow)

        assertEquals(fixedNow.plusSeconds(90 * 60), bestWindow.start)
        assertEquals(1.0, bestWindow.averagePricePencePerKwh, 0.0001)
        assertEquals(fixedNow.plusSeconds(17 * 60), startNowWindow.start)
        assertEquals(fixedNow.plusSeconds(77 * 60), startTimerWindow.start)
        assertEquals(fixedNow.plusSeconds(137 * 60), finishTimerWindow.end)
    }

    @Test
    fun snapshotKeepsShortRowsButGraphsTwentyFourHoursIncludingCurrentSlot() = runTest {
        val now = fixedNow.plusSeconds(15 * 60)
        val prices = List(60) { index ->
            val validFrom = fixedNow.minusSeconds(30 * 60).plusSeconds(index * 30L * 60L)
            PriceWindow(
                validFrom = validFrom,
                validTo = validFrom.plusSeconds(30 * 60),
                pricePencePerKwh = index.toDouble(),
            )
        }
        val settings = FakeSettingsDataSource(
            initial = AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = "E-1R-AGILE-24-10-01-C",
                loadDurationMinutes = 60,
                searchHorizonMinutes = 480,
                cachedPrices = prices,
                fetchedAt = fixedNow,
                lastRefreshMessage = null,
            ),
        )
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(),
            clock = { now },
        )

        val snapshot = repository.snapshots.first()

        assertEquals(8, snapshot.upcoming.size)
        assertEquals(fixedNow.plusSeconds(30 * 60), snapshot.upcoming.first().validFrom)
        assertEquals(49, snapshot.sparklinePrices.size)
        assertEquals(fixedNow, snapshot.sparklinePrices.first().validFrom)
        assertEquals(fixedNow.plusSeconds(24 * 60 * 60), snapshot.sparklinePrices.last().validFrom)
    }

    @Test
    fun refreshWithCachedPricesShowsClockHintWhenSecureConnectionFails() = runTest {
        val cachedPrice = PriceWindow(
            validFrom = fixedNow.minusSeconds(30 * 60),
            validTo = fixedNow.plusSeconds(30 * 60),
            pricePencePerKwh = 8.1,
        )
        val settings = FakeSettingsDataSource(
            initial = AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = "E-1R-AGILE-24-10-01-C",
                loadDurationMinutes = 60,
                searchHorizonMinutes = 480,
                cachedPrices = listOf(cachedPrice),
                fetchedAt = fixedNow.minusSeconds(60 * 60),
                lastRefreshMessage = null,
            ),
        )
        val repository = AgileRepository(
            settingsStore = settings,
            octopusApi = FakeOctopusClient(
                standardRatesError = SSLHandshakeException("Chain validation failed"),
            ),
            clock = { fixedNow },
        )

        runCatching { repository.refresh() }
        val saved = settings.settings.first()

        assertEquals(
            "Using cached prices. Last refresh failed: Secure connection failed. Check the watch/emulator date and time, then try again.",
            saved.lastRefreshMessage,
        )
    }

    private class FakeSettingsDataSource(
        initial: AgileSettings = AgileSettings(
            selectedRegionCode = null,
            selectedTariffCode = null,
            loadDurationMinutes = 60,
            searchHorizonMinutes = 480,
            cachedPrices = emptyList(),
            fetchedAt = null,
            lastRefreshMessage = null,
        ),
    ) : SettingsDataSource {
        private val state = MutableStateFlow(initial)
        var collectionCount = 0
            private set

        override val settings: Flow<AgileSettings> = flow {
            collectionCount += 1
            emitAll(state)
        }

        override suspend fun saveSelectedTariff(regionCode: String, tariffCode: String) {
            state.value = state.value.copy(
                selectedRegionCode = regionCode,
                selectedTariffCode = tariffCode,
                cachedPrices = emptyList(),
                fetchedAt = null,
                lastRefreshMessage = null,
            )
        }

        override suspend fun saveLoadDurationMinutes(value: Int) {
            state.value = state.value.copy(loadDurationMinutes = value.coerceIn(30, 480))
        }

        override suspend fun saveSearchHorizonMinutes(value: Int) {
            state.value = state.value.copy(searchHorizonMinutes = value.coerceIn(60, 1440))
        }

        override suspend fun saveCache(prices: List<PriceWindow>, fetchedAt: Instant, message: String?) {
            state.value = state.value.copy(
                cachedPrices = prices,
                fetchedAt = fetchedAt,
                lastRefreshMessage = message,
            )
        }

        override suspend fun saveRefreshMessage(message: String) {
            state.value = state.value.copy(lastRefreshMessage = message)
        }
    }

    private class FakeOctopusClient(
        private val product: ProductDetailDto = agileProduct(),
        private val standardRates: List<PriceWindow> = emptyList(),
        private val standardRatesError: Throwable? = null,
    ) : OctopusClient {
        override suspend fun discoverLatestAgileProduct(): ProductDetailDto = product

        override suspend fun standardUnitRates(
            productCode: String,
            tariffCode: String,
            periodFrom: Instant,
            periodTo: Instant,
        ): List<PriceWindow> {
            assertEquals("AGILE-24-10-01", productCode)
            assertEquals("E-1R-AGILE-24-10-01-C", tariffCode)
            standardRatesError?.let { throw it }
            return standardRates
        }
    }

    private class CountingSurfaceUpdater : PriceSurfaceUpdater {
        var requestCount = 0
            private set

        override fun requestUpdates() {
            requestCount += 1
        }
    }

    private companion object {
        val fixedNow: Instant = Instant.parse("2026-06-05T12:00:00Z")

        fun agileProduct(): ProductDetailDto =
            ProductDetailDto(
                code = "AGILE-24-10-01",
                fullName = "Agile Test",
                singleRegisterElectricityTariffs = mapOf(
                    "_C" to mapOf(
                        "direct_debit_monthly" to TariffCodeDto("E-1R-AGILE-24-10-01-C"),
                    ),
                ),
            )
    }
}
