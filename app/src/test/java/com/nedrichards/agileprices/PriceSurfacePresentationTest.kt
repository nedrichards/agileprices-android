package com.nedrichards.agileprices

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceSurfacePresentationTest {
    @Test
    fun tileAndComplicationShowLoadedState() = withUtcTimeZone {
        val snapshot = loadedSnapshot(price = 8.2)

        val tile = snapshot.tilePresentation()
        val complication = snapshot.complicationPresentation()

        assertEquals("8.2p", tile.currentText)
        assertEquals("p/kWh now", tile.caption)
        assertEquals("13:00-14:00 4.0p", tile.detail)
        assertEquals("8.2p", complication.text)
        assertEquals(
            "Current Agile electricity price 8.2 pence per kilowatt hour",
            complication.contentDescription,
        )
    }

    @Test
    fun tileAndComplicationShowNegativePriceState() = withUtcTimeZone {
        val snapshot = loadedSnapshot(price = -1.2)

        val tile = snapshot.tilePresentation()
        val complication = snapshot.complicationPresentation()

        assertEquals("-1.2p", tile.currentText)
        assertEquals("p/kWh now", tile.caption)
        assertEquals("13:00-14:00 4.0p", tile.detail)
        assertEquals("-1.2p", complication.text)
        assertEquals(
            "Current Agile electricity price -1.2 pence per kilowatt hour",
            complication.contentDescription,
        )
    }

    @Test
    fun tileAndComplicationShowNoSetupState() {
        val snapshot = PriceSnapshot(
            currentPrice = null,
            bestWindow = null,
            fetchedAt = null,
            validUntil = null,
            status = SnapshotStatus.NoSetup,
        )

        val tile = snapshot.tilePresentation()
        val complication = snapshot.complicationPresentation()

        assertEquals("Setup", tile.currentText)
        assertEquals("Choose region", tile.caption)
        assertEquals("Choose a region to load Agile prices", tile.detail)
        assertEquals("Setup", complication.text)
        assertEquals("Agile Prices needs a region setup", complication.contentDescription)
    }

    @Test
    fun tileAndComplicationShowStaleState() = withUtcTimeZone {
        val snapshot = PriceSnapshot(
            currentPrice = null,
            bestWindow = null,
            fetchedAt = Instant.parse("2026-01-05T09:00:00Z"),
            validUntil = Instant.parse("2026-01-05T11:00:00Z"),
            status = SnapshotStatus.Stale,
        )

        val tile = snapshot.tilePresentation()
        val complication = snapshot.complicationPresentation()

        assertEquals("Stale", tile.currentText)
        assertEquals("Cached data expired", tile.caption)
        assertEquals("Cache ended 5 Jan 11:00", tile.detail)
        assertEquals("Stale", complication.text)
        assertEquals("Cached Agile electricity prices are stale", complication.contentDescription)
    }

    @Test
    fun tileAndComplicationShowNoDataState() {
        val snapshot = PriceSnapshot(
            currentPrice = null,
            bestWindow = null,
            fetchedAt = null,
            validUntil = null,
            status = SnapshotStatus.Error,
        )

        val tile = snapshot.tilePresentation()
        val complication = snapshot.complicationPresentation()

        assertEquals("No data", tile.currentText)
        assertEquals("Price data unavailable", tile.caption)
        assertEquals("No cached price data", tile.detail)
        assertEquals("No data", complication.text)
        assertEquals("Agile electricity price data is unavailable", complication.contentDescription)
    }

    private fun loadedSnapshot(price: Double): PriceSnapshot =
        PriceSnapshot(
            currentPrice = PriceWindow(
                validFrom = Instant.parse("2026-01-05T12:00:00Z"),
                validTo = Instant.parse("2026-01-05T12:30:00Z"),
                pricePencePerKwh = price,
            ),
            bestWindow = BestWindow(
                start = Instant.parse("2026-01-05T13:00:00Z"),
                end = Instant.parse("2026-01-05T14:00:00Z"),
                averagePricePencePerKwh = 4.0,
            ),
            fetchedAt = Instant.parse("2026-01-05T11:58:00Z"),
            validUntil = Instant.parse("2026-01-06T00:00:00Z"),
            status = SnapshotStatus.Loaded,
        )

    private fun withUtcTimeZone(block: () -> Unit) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
