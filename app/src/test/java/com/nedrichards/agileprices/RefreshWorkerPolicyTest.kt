package com.nedrichards.agileprices

import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshWorkerPolicyTest {
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
