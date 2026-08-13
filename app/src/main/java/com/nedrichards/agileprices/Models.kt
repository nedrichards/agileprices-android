package com.nedrichards.agileprices

import java.time.Instant

data class PriceWindow(
    val validFrom: Instant,
    val validTo: Instant,
    val pricePencePerKwh: Double,
)

data class BestWindow(
    val start: Instant,
    val end: Instant,
    val averagePricePencePerKwh: Double,
)

enum class SnapshotStatus {
    NoSetup,
    Loading,
    Loaded,
    Stale,
    Error,
}

data class PriceSnapshot(
    val currentPrice: PriceWindow?,
    val bestWindow: BestWindow?,
    val fetchedAt: Instant?,
    val validUntil: Instant?,
    val status: SnapshotStatus,
    val message: String? = null,
    val upcoming: List<PriceWindow> = emptyList(),
    val sparklinePrices: List<PriceWindow> = emptyList(),
    val startNowWindow: BestWindow? = null,
    val startTimerWindow: BestWindow? = null,
    val finishTimerWindow: BestWindow? = null,
)

data class AgileAppState(
    val settings: AgileSettings,
    val snapshot: PriceSnapshot,
)

data class TariffOption(
    val code: String,
    val fullName: String,
)

data class AgileSettings(
    val selectedRegionCode: String?,
    val selectedTariffCode: String?,
    val loadDurationMinutes: Int,
    val searchHorizonMinutes: Int,
    val cachedPrices: List<PriceWindow>,
    val fetchedAt: Instant?,
    val lastRefreshMessage: String?,
)
