package com.nedrichards.agileprices

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PriceLogicTest {
    @Test
    fun extractProductCodeUsesMiddleSegments() {
        assertEquals("AGILE-24-10-01", extractProductCode("E-1R-AGILE-24-10-01-A"))
    }

    @Test
    fun currentPriceSelectsActiveWindow() {
        val start = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(start, listOf(30.0, -1.0, 20.0))

        val current = currentPriceAt(prices, Instant.parse("2026-03-21T12:42:00Z"))

        assertNotNull(current)
        assertEquals(-1.0, current!!.pricePencePerKwh, 0.0001)
    }

    @Test
    fun cheapestBoundarySlotReturnsLowestCostWindow() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(now, listOf(30.0, 25.0, 5.0, 4.0, 40.0, 50.0))

        val slot = findCheapestBoundarySlot(
            prices = prices,
            now = now,
            durationMinutes = 60,
            searchHorizonMinutes = 180,
            wholeHourStartsOnly = false,
        )

        assertNotNull(slot)
        assertEquals(Instant.parse("2026-03-21T13:00:00Z"), slot!!.start)
        assertEquals(Instant.parse("2026-03-21T14:00:00Z"), slot.end)
        assertEquals(4.5, slot.averagePricePencePerKwh, 0.0001)
    }

    @Test
    fun continuousSlotUsesHalfHourCadence() {
        val slotStart = Instant.parse("2026-03-21T12:00:00Z")
        val now = Instant.parse("2026-03-21T12:17:00Z")
        val prices = windows(slotStart, listOf(100.0, 100.0, 1.0, 1.0, 100.0))

        val slot = findCheapestContinuousSlot(
            prices = prices,
            now = now,
            durationMinutes = 60,
            searchHorizonMinutes = 120,
        )

        assertNotNull(slot)
        assertEquals(Instant.parse("2026-03-21T13:00:00Z"), slot!!.start)
        assertEquals(Instant.parse("2026-03-21T14:00:00Z"), slot.end)
    }

    @Test
    fun continuousSlotRoundsUpWhenDurationFillsSearchWindow() {
        val slotStart = Instant.parse("2026-03-21T12:00:00Z")
        val now = Instant.parse("2026-03-21T12:17:00Z")
        val prices = windows(slotStart, List(17) { 10.0 })

        val slot = findCheapestContinuousSlot(
            prices = prices,
            now = now,
            durationMinutes = 480,
            searchHorizonMinutes = 480,
        )

        assertNotNull(slot)
        assertEquals(Instant.parse("2026-03-21T12:30:00Z"), slot!!.start)
        assertEquals(Instant.parse("2026-03-21T20:30:00Z"), slot.end)
    }

    @Test
    fun continuousSlotCanStartNowOnHalfHourBoundary() {
        val now = Instant.parse("2026-03-21T12:30:00Z")
        val prices = windows(now, listOf(1.0, 1.0, 100.0))

        val slot = findCheapestContinuousSlot(
            prices = prices,
            now = now,
            durationMinutes = 60,
            searchHorizonMinutes = 120,
        )

        assertNotNull(slot)
        assertEquals(now, slot!!.start)
        assertEquals(Instant.parse("2026-03-21T13:30:00Z"), slot.end)
    }

    @Test
    fun bestWindowCalculationRequiresSortedPrices() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(now, listOf(30.0, 25.0, 5.0)).asReversed()

        assertThrows(IllegalArgumentException::class.java) {
            findBestLoadWindow(
                prices = prices,
                now = now,
                durationMinutes = 60,
                searchHorizonMinutes = 120,
            )
        }
    }

    @Test
    fun missingDataReturnsNoSlot() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(now, listOf(10.0))

        assertNull(
            findCheapestBoundarySlot(
                prices = prices,
                now = now,
                durationMinutes = 60,
                searchHorizonMinutes = 60,
                wholeHourStartsOnly = false,
            ),
        )
    }

    @Test
    fun continuousSlotRejectsWindowWithHalfHourGap() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = listOf(
            PriceWindow(
                validFrom = Instant.parse("2026-03-21T12:00:00Z"),
                validTo = Instant.parse("2026-03-21T12:30:00Z"),
                pricePencePerKwh = 1.0,
            ),
            PriceWindow(
                validFrom = Instant.parse("2026-03-21T13:00:00Z"),
                validTo = Instant.parse("2026-03-21T13:30:00Z"),
                pricePencePerKwh = 1.0,
            ),
        )

        val slot = findCheapestContinuousSlot(
            prices = prices,
            now = now,
            durationMinutes = 60,
            searchHorizonMinutes = 60,
        )

        assertNull(slot)
    }

    @Test
    fun wholeHourStartsWorkAcrossUkDstBoundary() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        try {
            val now = Instant.parse("2026-03-29T00:00:00Z")
            val prices = windows(now, listOf(50.0, 50.0, -2.0, -2.0, 50.0, 50.0))

            val slot = findCheapestBoundarySlot(
                prices = prices,
                now = now,
                durationMinutes = 60,
                searchHorizonMinutes = 180,
                wholeHourStartsOnly = true,
            )

            assertNotNull(slot)
            assertEquals(Instant.parse("2026-03-29T01:00:00Z"), slot!!.start)
            assertEquals(-2.0, slot.averagePricePencePerKwh, 0.0001)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun buildRegionToTariffsMapPrefersDirectDebit() {
        val product = ProductDetailDto(
            code = "AGILE-TEST",
            fullName = "Agile Test Tariff",
            singleRegisterElectricityTariffs = mapOf(
                "_A" to mapOf(
                    "prepay" to TariffCodeDto("PREPAY-A"),
                    "direct_debit_monthly" to TariffCodeDto("DDM-A"),
                ),
                "_Z" to mapOf("direct_debit_monthly" to TariffCodeDto("UNKNOWN")),
            ),
        )

        val result = buildRegionToTariffsMap(product, mapOf("_A" to "Eastern England"))

        assertEquals("DDM-A", result["_A"]!!.first().code)
        assertEquals("Agile Test Tariff (Eastern England)", result["_A"]!!.first().fullName)
    }

    @Test
    fun buildRegionToTariffsMapFallsBackToFirstCode() {
        val product = ProductDetailDto(
            code = "AGILE-TEST",
            singleRegisterElectricityTariffs = mapOf(
                "_A" to mapOf("prepay" to TariffCodeDto("PREPAY-A")),
            ),
        )

        val result = buildRegionToTariffsMap(product, mapOf("_A" to "Eastern England"))

        assertEquals("PREPAY-A", result["_A"]!!.first().code)
    }

    @Test
    fun selectLatestActiveAgileProductIgnoresExportAndExpiredProducts() {
        val now = Instant.parse("2026-06-05T12:00:00Z")
        val products = listOf(
            ProductSummaryDto(
                code = "AGILE-OLD",
                availableFrom = "2024-01-01T00:00:00Z",
                availableTo = "2026-01-01T00:00:00Z",
            ),
            ProductSummaryDto(
                code = "AGILE-EXPORT-26-01-01",
                availableFrom = "2026-01-01T00:00:00Z",
            ),
            ProductSummaryDto(
                code = "AGILE-26-01-01",
                availableFrom = "2026-01-01T00:00:00Z",
            ),
            ProductSummaryDto(
                code = "AGILE-26-05-01",
                availableFrom = "2026-05-01T00:00:00Z",
            ),
        )

        val selected = selectLatestActiveAgileProduct(products, now)

        assertEquals("AGILE-26-05-01", selected!!.code)
    }

    private fun windows(start: Instant, values: List<Double>): List<PriceWindow> =
        values.mapIndexed { index, value ->
            val validFrom = start.plusSeconds(index * 30L * 60L)
            PriceWindow(
                validFrom = validFrom,
                validTo = validFrom.plusSeconds(30 * 60),
                pricePencePerKwh = value,
            )
        }
}
