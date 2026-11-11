package com.nedrichards.agileprices

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearConnectionRecoveryTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val settings = AgileSettings("A", "tariff", 60, 480, emptyList(), null, null)

    @Test
    fun recoveryRequiresConfiguredTariffAndMissingCurrentPrice() {
        assertTrue(settings.needsWearConnectionRecovery(now))
        assertFalse(settings.copy(selectedTariffCode = null).needsWearConnectionRecovery(now))
        val current = PriceWindow(now, now.plusSeconds(1800), 10.0)
        assertFalse(settings.copy(cachedPrices = listOf(current)).needsWearConnectionRecovery(now))
        // Validity ends exclusively, so expiry must trigger recovery immediately.
        assertTrue(settings.copy(cachedPrices = listOf(current)).needsWearConnectionRecovery(current.validTo))
        assertTrue(settings.copy(cachedPrices = listOf(current.copy(validFrom = now.plusSeconds(60)))).needsWearConnectionRecovery(now))
    }
}
