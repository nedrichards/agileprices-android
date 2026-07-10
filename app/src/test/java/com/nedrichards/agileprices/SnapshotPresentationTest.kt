package com.nedrichards.agileprices

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPresentationTest {
    @Test
    fun surfaceChoiceUsesWearOnlyForWatchDevices() {
        assertEquals(AgileSurface.Wear, chooseSurface(isWatchDevice = true))
        assertEquals(AgileSurface.Phone, chooseSurface(isWatchDevice = false))
    }

    @Test
    fun loadedSnapshotShowsCurrentPrice() {
        val snapshot = PriceSnapshot(
            currentPrice = PriceWindow(
                validFrom = Instant.parse("2026-03-21T12:00:00Z"),
                validTo = Instant.parse("2026-03-21T12:30:00Z"),
                pricePencePerKwh = -1.2,
            ),
            bestWindow = null,
            fetchedAt = Instant.parse("2026-03-21T11:58:00Z"),
            validUntil = Instant.parse("2026-03-22T00:00:00Z"),
            status = SnapshotStatus.Loaded,
        )

        assertEquals("-1.2p", snapshot.primaryPriceText())
        assertEquals("-1.2", snapshot.primaryPriceValueForApp())
        assertEquals("p/kWh now", snapshot.primaryPriceCaption())
    }

    @Test
    fun staleSnapshotDoesNotLookLikeCurrentPrice() {
        val snapshot = PriceSnapshot(
            currentPrice = null,
            bestWindow = null,
            fetchedAt = Instant.parse("2026-03-20T11:58:00Z"),
            validUntil = Instant.parse("2026-03-21T12:00:00Z"),
            status = SnapshotStatus.Stale,
        )

        assertEquals("Stale", snapshot.primaryPriceText())
        assertEquals("--", snapshot.primaryPriceValueForApp())
        assertEquals("Cached data expired", snapshot.primaryPriceCaption())
        assertEquals("Cache ended ${formatDateTime(Instant.parse("2026-03-21T12:00:00Z"))}", snapshot.secondaryStatusText())
    }

    @Test
    fun noSetupSnapshotPromptsForSetup() {
        val snapshot = PriceSnapshot(
            currentPrice = null,
            bestWindow = null,
            fetchedAt = null,
            validUntil = null,
            status = SnapshotStatus.NoSetup,
        )

        assertEquals("Setup", snapshot.primaryPriceText())
        assertEquals("Choose region", snapshot.primaryPriceCaption())
    }

    @Test
    fun widgetPresentationUsesCachedPriceAndCheapestWindow() {
        val presentation = widgetPresentation(
            PriceSnapshot(
                currentPrice = PriceWindow(
                    validFrom = Instant.parse("2026-03-21T12:00:00Z"),
                    validTo = Instant.parse("2026-03-21T12:30:00Z"),
                    pricePencePerKwh = 8.2,
                ),
                bestWindow = BestWindow(
                    start = Instant.parse("2026-03-21T13:00:00Z"),
                    end = Instant.parse("2026-03-21T14:00:00Z"),
                    averagePricePencePerKwh = 4.0,
                ),
                fetchedAt = Instant.parse("2026-03-21T11:58:00Z"),
                validUntil = Instant.parse("2026-03-22T00:00:00Z"),
                status = SnapshotStatus.Loaded,
            ),
        )

        assertEquals("8.2p", presentation.value)
        assertEquals("p/kWh now", presentation.caption)
        assertEquals("13:00-14:00 4.0p", presentation.detail)
    }

    @Test
    fun widgetPresentationDoesNotPresentStaleDataAsCurrent() {
        val presentation = widgetPresentation(
            PriceSnapshot(
                currentPrice = null,
                bestWindow = null,
                fetchedAt = Instant.parse("2026-03-21T11:58:00Z"),
                validUntil = Instant.parse("2026-03-21T12:00:00Z"),
                status = SnapshotStatus.Stale,
            ),
        )

        assertEquals("Stale", presentation.value)
        assertEquals("Cached data expired", presentation.caption)
        assertEquals("Cache ended ${formatDateTime(Instant.parse("2026-03-21T12:00:00Z"))}", presentation.detail)
    }

    @Test
    fun launchRefreshRunsWhenConfiguredCacheIsEmpty() {
        val now = Instant.parse("2026-03-21T12:00:00Z")

        assertTrue(
            shouldRefreshOnStart(
                settings = settings(cachedPrices = emptyList()),
                snapshot = PriceSnapshot(
                    currentPrice = null,
                    bestWindow = null,
                    fetchedAt = null,
                    validUntil = null,
                    status = SnapshotStatus.Error,
                ),
                now = now,
            ),
        )
    }

    @Test
    fun launchRefreshRunsWhenConfiguredCacheHasExpired() {
        val now = Instant.parse("2026-03-21T12:00:00Z")

        assertTrue(
            shouldRefreshOnStart(
                settings = settings(
                    cachedPrices = listOf(
                        PriceWindow(
                            validFrom = Instant.parse("2026-03-21T11:00:00Z"),
                            validTo = Instant.parse("2026-03-21T11:30:00Z"),
                            pricePencePerKwh = 8.2,
                        ),
                    ),
                ),
                snapshot = PriceSnapshot(
                    currentPrice = null,
                    bestWindow = null,
                    fetchedAt = Instant.parse("2026-03-21T10:58:00Z"),
                    validUntil = Instant.parse("2026-03-21T11:30:00Z"),
                    status = SnapshotStatus.Stale,
                ),
                now = now,
            ),
        )
    }

    @Test
    fun launchRefreshWaitsWhenSetupIsMissingOrCacheIsCurrent() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val currentPrice = PriceWindow(
            validFrom = Instant.parse("2026-03-21T12:00:00Z"),
            validTo = Instant.parse("2026-03-21T12:30:00Z"),
            pricePencePerKwh = 8.2,
        )

        assertFalse(
            shouldRefreshOnStart(
                settings = settings(selectedTariffCode = null, cachedPrices = emptyList()),
                snapshot = PriceSnapshot(
                    currentPrice = null,
                    bestWindow = null,
                    fetchedAt = null,
                    validUntil = null,
                    status = SnapshotStatus.NoSetup,
                ),
                now = now,
            ),
        )
        assertFalse(
            shouldRefreshOnStart(
                settings = settings(cachedPrices = listOf(currentPrice)),
                snapshot = PriceSnapshot(
                    currentPrice = currentPrice,
                    bestWindow = null,
                    fetchedAt = Instant.parse("2026-03-21T11:58:00Z"),
                    validUntil = Instant.parse("2026-03-21T12:30:00Z"),
                    status = SnapshotStatus.Loaded,
                ),
                now = now,
            ),
        )
    }

    @Test
    fun windowRangeLabelsTomorrow() {
        val zone = ZoneId.systemDefault()
        val reference = LocalDate.of(2026, 3, 21).atTime(LocalTime.NOON).atZone(zone).toInstant()
        val start = LocalDate.of(2026, 3, 22).atTime(1, 0).atZone(zone).toInstant()
        val end = LocalDate.of(2026, 3, 22).atTime(2, 0).atZone(zone).toInstant()

        val label = formatWindowRange(start, end, reference)

        assertEquals("Tomorrow 01:00-02:00", label)
    }

    @Test
    fun bestWindowShowsStartsInEndsInAndDuration() {
        val window = BestWindow(
            start = Instant.parse("2026-03-21T13:45:00Z"),
            end = Instant.parse("2026-03-21T15:15:00Z"),
            averagePricePencePerKwh = 4.0,
        )

        assertEquals("Starts in 1h 28m", window.startsInText(Instant.parse("2026-03-21T12:17:00Z")))
        assertEquals("Ends in 2h 58m", window.endsInText(Instant.parse("2026-03-21T12:17:00Z")))
        assertEquals("In 1h 28m / Ends 2h 58m", window.compactTimingText(Instant.parse("2026-03-21T12:17:00Z")))
        assertEquals("Duration 1h 30m", window.durationText())
    }

    @Test
    fun bestWindowStartingNowShowsStartsNowAndRemainingEndTime() {
        val window = BestWindow(
            start = Instant.parse("2026-03-21T13:30:00Z"),
            end = Instant.parse("2026-03-21T14:30:00Z"),
            averagePricePencePerKwh = 4.0,
        )

        assertEquals("Starts now", window.startsInText(Instant.parse("2026-03-21T13:30:00Z")))
        assertEquals("Ends in 1h", window.endsInText(Instant.parse("2026-03-21T13:30:00Z")))
        assertEquals("Now / Ends 1h", window.compactTimingText(Instant.parse("2026-03-21T13:30:00Z")))
    }

    @Test
    fun timingGuidanceOmitsTimerHintsForWholeHours() {
        val window = BestWindow(
            start = Instant.parse("2026-03-21T13:00:00Z"),
            end = Instant.parse("2026-03-21T14:00:00Z"),
            averagePricePencePerKwh = 4.0,
        )

        assertEquals("Starts in 1h - Ends in 2h", window.timingGuidanceText(Instant.parse("2026-03-21T12:00:00Z")))
    }

    @Test
    fun timingGuidanceAddsTimerHintsForPartialHours() {
        val window = BestWindow(
            start = Instant.parse("2026-03-21T16:49:00Z"),
            end = Instant.parse("2026-03-21T20:18:00Z"),
            averagePricePencePerKwh = 3.2,
        )

        assertEquals(
            "Starts in 4h 32m (4h) - Ends in 8h 1m (9h)",
            window.timingGuidanceText(Instant.parse("2026-03-21T12:17:00Z")),
        )
    }

    private fun settings(
        selectedTariffCode: String? = "E-1R-AGILE-26-05-01-C",
        cachedPrices: List<PriceWindow>,
    ): AgileSettings =
        AgileSettings(
            selectedRegionCode = selectedTariffCode?.let { "_C" },
            selectedTariffCode = selectedTariffCode,
            loadDurationMinutes = 60,
            searchHorizonMinutes = 480,
            cachedPrices = cachedPrices,
            fetchedAt = null,
            lastRefreshMessage = null,
        )
}
