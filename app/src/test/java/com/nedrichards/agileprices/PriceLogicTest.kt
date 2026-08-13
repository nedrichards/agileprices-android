package com.nedrichards.agileprices

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun continuousSlotSearchesFromCurrentMinute() {
        val slotStart = Instant.parse("2026-03-21T12:00:00Z")
        val now = Instant.parse("2026-03-21T12:17:42Z")
        val prices = windows(slotStart, listOf(1.0, 100.0, 100.0))

        val slot = findCheapestContinuousSlot(
            prices = prices,
            now = now,
            durationMinutes = 60,
            searchHorizonMinutes = 90,
        )

        assertNotNull(slot)
        assertEquals(Instant.parse("2026-03-21T12:18:00Z"), slot!!.start)
        assertEquals(Instant.parse("2026-03-21T13:18:00Z"), slot.end)
    }

    @Test
    fun continuousSlotCanStillChooseHalfHourWhenCheapest() {
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
    fun continuousSlotUsesCurrentMinuteWhenDurationFillsSearchWindow() {
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
        assertEquals(Instant.parse("2026-03-21T12:17:00Z"), slot!!.start)
        assertEquals(Instant.parse("2026-03-21T20:17:00Z"), slot.end)
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
    fun startTimerUsesWholeHourDelaysFromNextCompleteMinute() {
        val now = Instant.parse("2026-03-21T12:17:42Z")
        val prices = windows(
            Instant.parse("2026-03-21T12:00:00Z"),
            listOf(40.0, 40.0, 1.0, 1.0, 40.0, 40.0, 40.0),
        )

        val slot = findBestTimerWindow(prices, now, 60, 180, ApplianceTimerMode.Start)

        assertNotNull(slot)
        assertEquals(Instant.parse("2026-03-21T13:18:00Z"), slot!!.start)
        assertEquals(Instant.parse("2026-03-21T14:18:00Z"), slot.end)
        assertEquals(12.7, slot.averagePricePencePerKwh, 0.0001)
    }

    @Test
    fun finishTimerUsesWholeHourDelaysFromNextCompleteMinute() {
        val now = Instant.parse("2026-03-21T12:17:42Z")
        val prices = windows(
            Instant.parse("2026-03-21T12:00:00Z"),
            listOf(40.0, 40.0, 1.0, 1.0, 1.0, 40.0, 40.0),
        )

        val slot = findBestTimerWindow(prices, now, 90, 180, ApplianceTimerMode.Finish)

        assertNotNull(slot)
        assertEquals(Instant.parse("2026-03-21T12:48:00Z"), slot!!.start)
        assertEquals(Instant.parse("2026-03-21T14:18:00Z"), slot.end)
        assertEquals(6.2, slot.averagePricePencePerKwh, 0.0001)
    }

    @Test
    fun startAndFinishTimersCanChooseDifferentWindows() {
        val now = Instant.parse("2026-03-21T12:17:42Z")
        val prices = windows(
            Instant.parse("2026-03-21T12:00:00Z"),
            listOf(40.0, 40.0, 1.0, 1.0, 1.0, 40.0, 40.0),
        )

        val start = findBestTimerWindow(prices, now, 90, 180, ApplianceTimerMode.Start)
        val finish = findBestTimerWindow(prices, now, 90, 180, ApplianceTimerMode.Finish)

        assertNotNull(start)
        assertNotNull(finish)
        assertNotEquals(start!!.start, finish!!.start)
        assertEquals(Instant.parse("2026-03-21T13:18:00Z"), start.start)
        assertEquals(Instant.parse("2026-03-21T12:48:00Z"), finish.start)
    }

    @Test
    fun timerWindowRejectsMissingTariffCoverage() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = listOf(
            PriceWindow(now, now.plusSeconds(30 * 60), 1.0),
            PriceWindow(now.plusSeconds(60 * 60), now.plusSeconds(90 * 60), 1.0),
        )

        assertNull(findBestTimerWindow(prices, now, 60, 60, ApplianceTimerMode.Start))
    }

    @Test
    fun timerWindowMustFitCompletelyInsideHorizon() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(now, List(4) { 1.0 })

        assertNull(findBestTimerWindow(prices, now, 90, 60, ApplianceTimerMode.Start))
        assertNull(findBestTimerWindow(prices, now, 90, 60, ApplianceTimerMode.Finish))
    }

    @Test
    fun timerWindowCalculationRequiresSortedPrices() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(now, listOf(1.0, 2.0, 3.0)).asReversed()

        assertThrows(IllegalArgumentException::class.java) {
            findBestTimerWindow(prices, now, 60, 120, ApplianceTimerMode.Start)
        }
        assertThrows(IllegalArgumentException::class.java) {
            findBestTimerWindow(prices, now, 60, 120, ApplianceTimerMode.Finish)
        }
    }

    @Test
    fun timerWindowTieKeepsEarliestDelay() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(now, List(8) { 5.0 })

        val start = findBestTimerWindow(prices, now, 60, 180, ApplianceTimerMode.Start)
        val finish = findBestTimerWindow(prices, now, 60, 180, ApplianceTimerMode.Finish)

        assertEquals(now, start?.start)
        assertEquals(now.plusSeconds(60 * 60), finish?.end)
    }

    @Test
    fun startNowWindowPricesTheFullRunFromNextCompleteMinute() {
        val now = Instant.parse("2026-03-21T12:17:42Z")
        val prices = windows(
            Instant.parse("2026-03-21T12:00:00Z"),
            listOf(10.0, 20.0, 40.0),
        )

        val slot = findLoadWindowStartingNow(prices, now, 60, 120)

        assertEquals(Instant.parse("2026-03-21T12:18:00Z"), slot?.start)
        assertEquals(Instant.parse("2026-03-21T13:18:00Z"), slot?.end)
        assertEquals(24.0, slot!!.averagePricePencePerKwh, 0.0001)
    }

    @Test
    fun startNowWindowRequiresCoverageAndHorizon() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val gap = listOf(
            PriceWindow(now, now.plusSeconds(30 * 60), 1.0),
            PriceWindow(now.plusSeconds(60 * 60), now.plusSeconds(90 * 60), 1.0),
        )

        assertNull(findLoadWindowStartingNow(gap, now, 60, 120))
        assertNull(findLoadWindowStartingNow(windows(now, List(4) { 1.0 }), now, 90, 60))
    }

    @Test
    fun timerAlternativesCanBeIndependentlyUnavailable() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = windows(now, List(3) { 5.0 })

        val start = findBestTimerWindow(prices, now, 90, 120, ApplianceTimerMode.Start)
        val finish = findBestTimerWindow(prices, now, 90, 120, ApplianceTimerMode.Finish)

        assertNotNull(start)
        assertNull(finish)
    }

    @Test
    fun timerRecommendationsPriceNegativeWindowAndAvoidSharpCliff() {
        val now = Instant.parse("2026-03-21T12:17:42Z")
        val prices = windows(
            Instant.parse("2026-03-21T12:00:00Z"),
            listOf(35.0, 35.0, -50.0, -50.0, 95.0, 95.0, 35.0, 35.0),
        )

        val start = findBestTimerWindow(prices, now, 60, 180, ApplianceTimerMode.Start)
        val finish = findBestTimerWindow(prices, now, 60, 180, ApplianceTimerMode.Finish)

        assertEquals(-6.5, start!!.averagePricePencePerKwh, 0.0001)
        assertEquals(-6.5, finish!!.averagePricePencePerKwh, 0.0001)
        assertTrue(start.averagePricePencePerKwh < 95.0)
    }

    @Test
    fun timerDelaysRemainElapsedHoursAcrossUkSpringClockChange() {
        val now = Instant.parse("2026-03-29T00:30:42Z")
        val prices = windows(Instant.parse("2026-03-29T00:00:00Z"), List(8) { 5.0 })

        val slot = findBestTimerWindow(prices, now, 60, 180, ApplianceTimerMode.Start)

        assertEquals(Instant.parse("2026-03-29T00:31:00Z"), slot?.start)
        assertEquals(Instant.parse("2026-03-29T01:31:00Z"), slot?.end)
    }

    @Test
    fun timerDelaysRemainElapsedHoursAcrossUkAutumnClockChange() {
        val now = Instant.parse("2026-10-25T00:30:42Z")
        val prices = windows(Instant.parse("2026-10-25T00:00:00Z"), List(8) { 5.0 })

        val slot = findBestTimerWindow(prices, now, 60, 180, ApplianceTimerMode.Finish)

        assertEquals(Instant.parse("2026-10-25T00:31:00Z"), slot?.start)
        assertEquals(Instant.parse("2026-10-25T01:31:00Z"), slot?.end)
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
    fun windowRangeDisambiguatesRepeatedUkAutumnDstHour() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        try {
            val firstRepeatedHour = formatWindowRange(
                start = Instant.parse("2026-10-25T00:00:00Z"),
                end = Instant.parse("2026-10-25T00:30:00Z"),
                reference = Instant.parse("2026-10-25T00:00:00Z"),
            )
            val secondRepeatedHour = formatWindowRange(
                start = Instant.parse("2026-10-25T01:00:00Z"),
                end = Instant.parse("2026-10-25T01:30:00Z"),
                reference = Instant.parse("2026-10-25T00:00:00Z"),
            )

            assertEquals("01:00 BST-01:30 BST", firstRepeatedHour)
            assertEquals("01:00 GMT-01:30 GMT", secondRepeatedHour)
            assertNotEquals(firstRepeatedHour, secondRepeatedHour)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun maxDurationCanBeFoundNearEndOfSearchHorizon() {
        val now = Instant.parse("2026-03-21T12:00:00Z")
        val prices = List(65) { index ->
            val validFrom = now.plusSeconds(index * 30L * 60L)
            PriceWindow(
                validFrom = validFrom,
                validTo = validFrom.plusSeconds(30 * 60),
                pricePencePerKwh = if (index in 47..62) 1.0 else 40.0,
            )
        }

        val slot = findBestLoadWindow(
            prices = prices,
            now = now,
            durationMinutes = 480,
            searchHorizonMinutes = 1440,
        )

        assertNotNull(slot)
        assertEquals(now.plusSeconds(47 * 30L * 60L), slot!!.start)
        assertEquals(now.plusSeconds(63 * 30L * 60L), slot.end)
        assertEquals(1.0, slot.averagePricePencePerKwh, 0.0001)
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
