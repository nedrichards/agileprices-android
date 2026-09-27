package com.nedrichards.agileprices

import java.io.IOException
import java.time.Instant
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshWorkerPolicyTest {
    @Test
    fun expectedHorizonFollowsUkReleaseAndDaylightSavingTime() {
        assertEquals(
            Instant.parse("2026-03-21T23:00:00Z"),
            expectedPriceHorizon(Instant.parse("2026-03-21T15:59:00Z")),
        )
        assertEquals(
            Instant.parse("2026-03-22T23:00:00Z"),
            expectedPriceHorizon(Instant.parse("2026-03-21T16:00:00Z")),
        )
        assertEquals(
            Instant.parse("2026-07-21T22:00:00Z"),
            expectedPriceHorizon(Instant.parse("2026-07-21T14:59:00Z")),
        )
        assertEquals(
            Instant.parse("2026-07-22T22:00:00Z"),
            expectedPriceHorizon(Instant.parse("2026-07-21T15:00:00Z")),
        )
    }

    @Test
    fun expectedCoverageRequiresContinuousHalfHourRates() {
        val now = Instant.parse("2026-03-21T20:00:00Z")
        val complete = (0 until 54).map { slot ->
            PriceWindow(
                validFrom = now.plusSeconds(slot * 1800L),
                validTo = now.plusSeconds((slot + 1) * 1800L),
                pricePencePerKwh = 8.0,
            )
        }

        assertTrue(pricesCoverExpectedHorizon(complete, now))
        assertFalse(pricesCoverExpectedHorizon(complete.take(6), now))
        assertFalse(pricesCoverExpectedHorizon(complete.filterIndexed { index, _ -> index != 3 }, now))
    }

    @Test
    fun retriesTemporaryTransportAndServerFailures() {
        assertTrue(IOException("Timed out").isRetryableRefreshFailure())
        assertTrue(OctopusApiException("Busy", statusCode = 408).isRetryableRefreshFailure())
        assertTrue(OctopusApiException("Rate limited", statusCode = 429).isRetryableRefreshFailure())
        assertTrue(OctopusApiException("Unavailable", statusCode = 503).isRetryableRefreshFailure())
    }

    @Test
    fun doesNotRetryClockTlsOrPermanentApiFailures() {
        assertFalse(SSLHandshakeException("Certificate not yet valid").isRetryableRefreshFailure())
        assertFalse(OctopusApiException("Bad tariff", statusCode = 404).isRetryableRefreshFailure())
        assertFalse(OctopusApiException("No active tariff").isRetryableRefreshFailure())
    }
}
