package com.nedrichards.agileprices

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun atOrBelowZeroAlertContinuesThroughZeroUntilTheFirstPositivePrice() {
        val now = Instant.parse("2026-03-21T12:05:00Z")
        val negative = PriceWindow(
            validFrom = Instant.parse("2026-03-21T12:00:00Z"),
            validTo = Instant.parse("2026-03-21T12:30:00Z"),
            pricePencePerKwh = -1.2,
        )
        val zero = PriceWindow(
            validFrom = negative.validTo,
            validTo = Instant.parse("2026-03-21T13:00:00Z"),
            pricePencePerKwh = 0.0,
        )
        val positive = PriceWindow(
            validFrom = zero.validTo,
            validTo = Instant.parse("2026-03-21T13:30:00Z"),
            pricePencePerKwh = 0.01,
        )

        val alert = atOrBelowZeroAlert(
            PriceSnapshot(
                currentPrice = negative,
                bestWindow = null,
                fetchedAt = now,
                validUntil = positive.validTo,
                status = SnapshotStatus.Loaded,
                sparklinePrices = listOf(negative, zero, positive),
            ),
            now,
        )

        assertEquals(negative.validTo, alert?.nextBoundary)
        assertEquals(positive.validFrom, alert?.positiveAt)
        assertEquals(
            "At or below zero for 55m; positive from ${formatTime(positive.validFrom)}",
            alert?.detailText(now),
        )
    }

    @Test
    fun positivePriceCancelsAtOrBelowZeroAlert() {
        val positive = PriceWindow(
            validFrom = Instant.parse("2026-03-21T12:00:00Z"),
            validTo = Instant.parse("2026-03-21T12:30:00Z"),
            pricePencePerKwh = 0.01,
        )

        assertNull(
            atOrBelowZeroAlert(
                PriceSnapshot(
                    currentPrice = positive,
                    bestWindow = null,
                    fetchedAt = positive.validFrom,
                    validUntil = positive.validTo,
                    status = SnapshotStatus.Loaded,
                    sparklinePrices = listOf(positive),
                ),
                positive.validFrom,
            ),
        )
    }

    @Test
    fun zeroPriceKeepsAtOrBelowZeroAlertActive() {
        val zero = PriceWindow(
            validFrom = Instant.parse("2026-03-21T12:30:00Z"),
            validTo = Instant.parse("2026-03-21T13:00:00Z"),
            pricePencePerKwh = 0.0,
        )
        val positive = PriceWindow(
            validFrom = zero.validTo,
            validTo = Instant.parse("2026-03-21T13:30:00Z"),
            pricePencePerKwh = 0.01,
        )

        val alert = atOrBelowZeroAlert(
            PriceSnapshot(
                currentPrice = zero,
                bestWindow = null,
                fetchedAt = zero.validFrom,
                validUntil = positive.validTo,
                status = SnapshotStatus.Loaded,
                sparklinePrices = listOf(zero, positive),
            ),
            zero.validFrom,
        )

        assertEquals(zero.validTo, alert?.nextBoundary)
        assertEquals(positive.validFrom, alert?.positiveAt)
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
    fun launchRefreshWaitsWhenSetupIsMissingOrCacheCanPlanTheRequestedRun() {
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
        val nextPrice = PriceWindow(
            validFrom = currentPrice.validTo,
            validTo = currentPrice.validTo.plusSeconds(30 * 60),
            pricePencePerKwh = 8.3,
        )
        assertFalse(
            shouldRefreshOnStart(
                settings = settings(cachedPrices = listOf(currentPrice, nextPrice)),
                snapshot = PriceSnapshot(
                    currentPrice = currentPrice,
                    bestWindow = BestWindow(
                        start = currentPrice.validFrom,
                        end = nextPrice.validTo,
                        averagePricePencePerKwh = 8.25,
                    ),
                    fetchedAt = Instant.parse("2026-03-21T11:58:00Z"),
                    validUntil = Instant.parse("2026-03-21T12:30:00Z"),
                    status = SnapshotStatus.Loaded,
                ),
                now = now,
            ),
        )
    }

    @Test
    fun launchRefreshRunsWhenCacheCannotCoverRequestedDuration() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val currentPrice = PriceWindow(
            validFrom = now,
            validTo = now.plusSeconds(30 * 60),
            pricePencePerKwh = 8.2,
        )

        assertTrue(
            shouldRefreshOnStart(
                settings = settings(cachedPrices = listOf(currentPrice)),
                snapshot = PriceSnapshot(
                    currentPrice = currentPrice,
                    bestWindow = null,
                    fetchedAt = now,
                    validUntil = currentPrice.validTo,
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
    fun timerPresentationsShowIndependentPhoneDetailsAndWearRows() {
        val now = Instant.parse("2026-03-21T12:17:42Z")
        val snapshot = timerSnapshot(startPrice = 3.2, finishPrice = 3.8)

        val recommendations = snapshot.timerRecommendationPresentations(now)

        assertEquals("Start in", recommendations[0].label)
        assertEquals("4h", recommendations[0].timerValue)
        assertEquals("16:18-17:18 · 3.2p/kWh average · +0.2p/kWh", recommendations[0].detail)
        assertEquals(
            listOf(
                "16:18-17:18 · 3.2p/kWh average · +0.2p/kWh",
                "16:18-17:18 · 3.2p/kWh avg · +0.2p/kWh",
                "16:18-17:18 · 3.2p avg · +0.2p",
                "16:18-17:18 · 3.2p/kWh avg",
                "16:18-17:18 · 3.2p",
            ),
            recommendations[0].detailOptions,
        )
        assertEquals("Finish in", recommendations[1].label)
        assertEquals("9h", recommendations[1].timerValue)
        assertEquals("20:18-21:18 · 3.8p/kWh average · +0.8p/kWh", recommendations[1].detail)
        val wear = snapshot.wearTimerPresentation(now)
        assertEquals("Start now · 5.0p avg", wear.startNowText)
        assertEquals(
            listOf("Start in 4h · 3.2p avg", "Finish in 9h · 3.8p avg"),
            wear.recommendationRows,
        )
    }

    @Test
    fun timerPresentationSuppressesNegligibleDeltaButHandlesNegativePrices() {
        val snapshot = timerSnapshot(exactPrice = -4.0, startPrice = -3.96, finishPrice = -3.4)

        val recommendations = snapshot.timerRecommendationPresentations(Instant.parse("2026-03-21T12:17:42Z"))

        assertFalse(recommendations[0].detail.contains("+"))
        assertTrue(recommendations[1].detail.endsWith("+0.6p/kWh"))
    }

    @Test
    fun timerPresentationOmitsUnavailableAlternative() {
        val snapshot = timerSnapshot(finishPrice = null)

        assertEquals(
            listOf("Start in 4h · 3.2p avg"),
            snapshot.wearTimerPresentation(Instant.parse("2026-03-21T12:17:42Z")).recommendationRows,
        )
    }

    @Test
    fun timerPresentationUsesDateAndDstAwareAbsoluteRange() {
        val originalTimeZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/London"))
        try {
            val now = Instant.parse("2026-10-24T21:00:42Z")
            val exact = BestWindow(
                Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-25T00:30:00Z"),
                1.0,
            )
            val repeated = BestWindow(
                Instant.parse("2026-10-25T01:01:00Z"),
                Instant.parse("2026-10-25T01:31:00Z"),
                1.1,
            )
            val snapshot = PriceSnapshot(
                null, exact, null, null, SnapshotStatus.Loaded,
                startTimerWindow = repeated,
            )

            val recommendation = snapshot.timerRecommendationPresentations(now).single()

            assertTrue(recommendation.detail.startsWith("Tomorrow 01:01 GMT-01:31 GMT"))
            assertTrue(recommendation.detailOptions.all { it.startsWith("Tomorrow 01:01 GMT-01:31 GMT") })
            assertEquals("Start in 4h · 1.1p avg", snapshot.wearTimerPresentation(now).recommendationRows.single())
        } finally {
            java.util.TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun wearSparklineIncludesZeroWhenAnyVisiblePriceIsNegative() {
        val start = Instant.parse("2026-03-21T12:00:00Z")
        val allNegative = wearSparklineScale(
            listOf(
                PriceWindow(start, start.plusSeconds(1800), -8.0),
                PriceWindow(start.plusSeconds(1800), start.plusSeconds(3600), -2.0),
            ),
        )
        val mixed = wearSparklineScale(
            listOf(
                PriceWindow(start, start.plusSeconds(1800), -1.0),
                PriceWindow(start.plusSeconds(1800), start.plusSeconds(3600), 4.0),
            ),
        )
        val positive = wearSparklineScale(
            listOf(
                PriceWindow(start, start.plusSeconds(1800), 2.0),
                PriceWindow(start.plusSeconds(1800), start.plusSeconds(3600), 4.0),
            ),
        )

        assertEquals(0.0, allNegative.maximum, 0.0001)
        assertTrue(allNegative.showsZeroBaseline)
        assertEquals(4.0, mixed.maximum, 0.0001)
        assertTrue(mixed.showsZeroBaseline)
        assertFalse(positive.showsZeroBaseline)
    }

    private fun timerSnapshot(
        exactPrice: Double = 3.0,
        startPrice: Double? = 3.2,
        finishPrice: Double? = 3.8,
    ): PriceSnapshot = PriceSnapshot(
        currentPrice = null,
        bestWindow = BestWindow(
            Instant.parse("2026-03-21T16:17:00Z"),
            Instant.parse("2026-03-21T17:17:00Z"),
            exactPrice,
        ),
        fetchedAt = null,
        validUntil = null,
        status = SnapshotStatus.Loaded,
        startNowWindow = BestWindow(
            Instant.parse("2026-03-21T12:18:00Z"),
            Instant.parse("2026-03-21T13:18:00Z"),
            5.0,
        ),
        startTimerWindow = startPrice?.let {
            BestWindow(
                Instant.parse("2026-03-21T16:18:00Z"),
                Instant.parse("2026-03-21T17:18:00Z"),
                it,
            )
        },
        finishTimerWindow = finishPrice?.let {
            BestWindow(
                Instant.parse("2026-03-21T20:18:00Z"),
                Instant.parse("2026-03-21T21:18:00Z"),
                it,
            )
        },
    )

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
