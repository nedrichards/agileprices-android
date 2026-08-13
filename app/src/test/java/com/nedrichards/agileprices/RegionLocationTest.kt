package com.nedrichards.agileprices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionLocationTest {
    @Test
    fun suggestsTheOnlyRegionContainingThePoint() {
        val features = listOf(feature("UKPN (East)", square(0.0, 0.0, 2.0, 2.0)))

        assertEquals("_A", suggestRegionCode(1.0, 1.0, features))
    }

    @Test
    fun rejectsOutsideAndBoundaryPoints() {
        val features = listOf(
            feature("UKPN (East)", square(0.0, 0.0, 1.0, 2.0)),
            feature("WPD (East Midlands)", square(1.0, 0.0, 2.0, 2.0)),
        )

        assertNull(suggestRegionCode(3.0, 3.0, features))
        assertNull(suggestRegionCode(1.0, 1.0, features))
    }

    @Test
    fun rejectsInvalidCoordinates() {
        assertNull(suggestRegionCode(91.0, 0.0))
        assertNull(suggestRegionCode(Double.NaN, 0.0))
    }

    @Test
    fun rejectsFixWhoseAccuracyReachesRegionBoundary() {
        val features = listOf(feature("UKPN (East)", square(0.0, 0.0, 2.0, 2.0)))

        assertTrue(isNearRegionBoundary(1.0, 1.999, 120.0, features))
        assertFalse(isNearRegionBoundary(1.0, 1.0, 120.0, features))
        assertFalse(isNearRegionBoundary(1.0, 1.999, Double.NaN, features))
    }

    @Test
    fun overseasCheckIsConservativeAroundUkAndNearbyLocations() {
        assertTrue(isClearlyOutsideUk(40.7, -74.0))
        assertFalse(isClearlyOutsideUk(51.5, -0.1))
        assertFalse(isClearlyOutsideUk(54.6, -5.9))
        assertFalse(isClearlyOutsideUk(53.3, -6.3))
        assertFalse(isClearlyOutsideUk(Double.NaN, 0.0))
    }

    private fun feature(name: String, ring: List<List<Double>>) = RegionFeature(
        properties = RegionProperties(longname = name),
        geometry = RegionGeometry(
            type = "MultiPolygon",
            coordinates = listOf(listOf(ring)),
        ),
    )

    private fun square(left: Double, bottom: Double, right: Double, top: Double) = listOf(
        listOf(left, bottom),
        listOf(right, bottom),
        listOf(right, top),
        listOf(left, top),
        listOf(left, bottom),
    )
}
