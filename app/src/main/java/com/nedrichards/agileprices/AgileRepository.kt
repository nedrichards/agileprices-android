package com.nedrichards.agileprices

import android.content.Context
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AgileRepository(
    private val settingsStore: SettingsDataSource,
    private val octopusApi: OctopusClient = OctopusApi(),
    private val clock: () -> Instant = Instant::now,
    private val surfaceUpdater: PriceSurfaceUpdater? = null,
) {
    val settings: Flow<AgileSettings> = settingsStore.settings

    val snapshots: Flow<PriceSnapshot> =
        settings.map { settings -> settings.toSnapshot(clock()) }

    val appState: Flow<AgileAppState> =
        settings.map { settings ->
            AgileAppState(
                settings = settings,
                snapshot = settings.toSnapshot(clock()),
            )
        }

    suspend fun configureRegion(regionCode: String): TariffOption {
        val product = octopusApi.discoverLatestAgileProduct()
        val tariff = buildRegionToTariffsMap(product, regionCodeToName)[regionCode]
            ?.firstOrNull()
            ?: throw OctopusApiException("No Agile tariff was found for ${regionCodeToName[regionCode] ?: regionCode}.")

        settingsStore.saveSelectedTariff(regionCode = regionCode, tariffCode = tariff.code)
        runCatching {
            refresh(tariffCode = tariff.code)
        }.onFailure { error ->
            settingsStore.saveRefreshMessage(error.userFacingRefreshMessage())
        }
        return tariff
    }

    suspend fun refresh() {
        val settings = settings.first()
        val tariffCode = settings.selectedTariffCode
        if (tariffCode.isNullOrBlank()) {
            val regionCode = settings.selectedRegionCode
                ?: throw OctopusApiException("Choose a region before refreshing prices.")
            configureRegion(regionCode)
            return
        }

        refresh(
            tariffCode = tariffCode,
            regionCode = settings.selectedRegionCode,
            allowTariffRollover = true,
            hasCachedPrices = settings.cachedPrices.isNotEmpty(),
        )
    }

    suspend fun setLoadDurationMinutes(value: Int) {
        settingsStore.saveLoadDurationMinutes(value)
    }

    suspend fun setSearchHorizonMinutes(value: Int) {
        settingsStore.saveSearchHorizonMinutes(value)
    }

    private suspend fun refresh(
        tariffCode: String,
        regionCode: String? = null,
        allowTariffRollover: Boolean = false,
        hasCachedPrices: Boolean = false,
    ) {
        val now = clock()
        val productCode = extractProductCode(tariffCode)
        runCatching {
            octopusApi.standardUnitRates(
                productCode = productCode,
                tariffCode = tariffCode,
                periodFrom = now.minus(Duration.ofHours(1)),
                periodTo = now.plus(Duration.ofHours(defaultFetchHorizonHours)),
            )
        }.onSuccess { prices ->
            if (prices.isEmpty() && allowTariffRollover && regionCode != null) {
                refreshLatestTariffForRegion(regionCode = regionCode, previousTariffCode = tariffCode)
                return
            }
            val sortedPrices = prices.sortedByValidFrom()
            settingsStore.saveCache(
                prices = sortedPrices,
                fetchedAt = now,
                message = if (sortedPrices.isEmpty()) "No future rates were returned." else null,
            )
            surfaceUpdater?.requestUpdates()
        }.onFailure { error ->
            if (allowTariffRollover && regionCode != null && error is OctopusApiException) {
                val recovered = runCatching {
                    refreshLatestTariffForRegion(regionCode = regionCode, previousTariffCode = tariffCode)
                }.isSuccess
                if (recovered) return
            }
            val detail = error.userFacingRefreshMessage()
            settingsStore.saveRefreshMessage(
                if (hasCachedPrices) {
                    "Using cached prices. Last refresh failed: $detail"
                } else {
                    detail
                },
            )
            throw error
        }
    }

    private suspend fun refreshLatestTariffForRegion(
        regionCode: String,
        previousTariffCode: String,
    ) {
        val product = octopusApi.discoverLatestAgileProduct()
        val tariff = buildRegionToTariffsMap(product, regionCodeToName)[regionCode]
            ?.firstOrNull()
            ?: throw OctopusApiException("No active Agile tariff was found for ${regionCodeToName[regionCode] ?: regionCode}.")

        if (tariff.code == previousTariffCode) {
            throw OctopusApiException("No newer Agile tariff was found for ${regionCodeToName[regionCode] ?: regionCode}.")
        }

        settingsStore.saveSelectedTariff(regionCode = regionCode, tariffCode = tariff.code)
        refresh(tariffCode = tariff.code)
    }

    private fun AgileSettings.toSnapshot(now: Instant): PriceSnapshot {
        if (selectedTariffCode.isNullOrBlank()) {
            return PriceSnapshot(
                currentPrice = null,
                bestWindow = null,
                fetchedAt = fetchedAt,
                validUntil = null,
                status = SnapshotStatus.NoSetup,
                message = lastRefreshMessage,
            )
        }

        val validPrices = cachedPrices
        val current = currentPriceAt(validPrices, now)
        val best = findBestLoadWindow(
            prices = validPrices,
            now = now,
            durationMinutes = loadDurationMinutes,
            searchHorizonMinutes = searchHorizonMinutes,
        )
        val validUntil = validPrices.maxOfOrNull { it.validTo }
        val status = when {
            validPrices.isEmpty() -> SnapshotStatus.Error
            validUntil != null && validUntil <= now -> SnapshotStatus.Stale
            current == null -> SnapshotStatus.Stale
            else -> SnapshotStatus.Loaded
        }

        return PriceSnapshot(
            currentPrice = current,
            bestWindow = best,
            fetchedAt = fetchedAt,
            validUntil = validUntil,
            status = status,
            message = lastRefreshMessage,
            upcoming = validPrices
                .filter { it.validFrom >= now }
                .take(8),
        )
    }

    companion object {
        const val defaultFetchHorizonHours = 36L
    }
}

private fun Throwable.userFacingRefreshMessage(): String {
    if (looksLikeDeviceClockTlsFailure()) {
        return "Secure connection failed. Check the watch/emulator date and time, then try again."
    }
    return message ?: "Could not refresh prices."
}

private fun Throwable.looksLikeDeviceClockTlsFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SSLException) return true
        val detail = "${current::class.qualifiedName.orEmpty()} ${current.message.orEmpty()}".lowercase()
        if (
            "chain validation" in detail ||
            "certpathvalidator" in detail ||
            "certificate" in detail && "valid" in detail
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

fun createRepository(context: Context): AgileRepository =
    AgileRepository(
        settingsStore = SettingsStore(context.applicationContext),
        surfaceUpdater = WearPriceSurfaceUpdater(context.applicationContext),
    )
