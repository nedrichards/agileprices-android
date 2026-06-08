package com.nedrichards.agileprices

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotPresentationTest {
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
}
