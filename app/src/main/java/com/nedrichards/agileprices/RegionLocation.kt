package com.nedrichards.agileprices

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private val regionJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class RegionFeatureCollection(val features: List<RegionFeature> = emptyList())

@Serializable
internal data class RegionFeature(
    val properties: RegionProperties = RegionProperties(),
    val geometry: RegionGeometry? = null,
)

@Serializable
internal data class RegionProperties(val longname: String? = null)

@Serializable
internal data class RegionGeometry(
    val type: String,
    val coordinates: List<List<List<List<Double>>>> = emptyList(),
)

private val regionNameToCode = mapOf(
    "UKPN (East)" to "_A",
    "WPD (East Midlands)" to "_B",
    "UKPN (London)" to "_C",
    "SPEN (SP MANWEB)" to "_D",
    "WPD (Midlands)" to "_E",
    "NPG (Northern Electric)" to "_F",
    "ENWL" to "_G",
    "SSE (Southern)" to "_H",
    "UKPN (South)" to "_J",
    "WPD (South Wales)" to "_K",
    "WPD (South West)" to "_L",
    "NPG (Yorkshire Electric)" to "_M",
    "SPEN (SP Distribution)" to "_N",
    "SSE" to "_P",
)

internal const val outsideUkLocationMessage =
    "Agile Prices is for Octopus Energy electricity tariffs in Great Britain. " +
        "It looks like you're outside the UK, so we can't suggest a region. Thanks for trying Agile Prices."

private const val minimumUkLatitude = 49.0
private const val maximumUkLatitude = 61.5
private const val minimumUkLongitude = -10.0
private const val maximumUkLongitude = 3.0

internal fun suggestRegionCode(
    latitude: Double,
    longitude: Double,
    features: List<RegionFeature> = emptyList(),
): String? {
    if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }
    val matches = features.mapNotNull { feature ->
        val geometry = feature.geometry ?: return@mapNotNull null
        val polygons = if (geometry.type == "MultiPolygon") geometry.coordinates else emptyList()
        if (polygons.any { polygon -> polygon.any { ring ->
            ring.indices.any { index ->
                pointOnSegment(longitude, latitude, ring[index], ring[(index + 1) % ring.size])
            }
        } }) {
            return null
        }
        if (polygons.any { polygon -> pointInPolygon(longitude, latitude, polygon) }) {
            regionNameToCode[feature.properties.longname]
        } else {
            null
        }
    }
    return matches.singleOrNull()
}

internal fun isClearlyOutsideUk(latitude: Double, longitude: Double): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    return latitude !in minimumUkLatitude..maximumUkLatitude ||
        longitude !in minimumUkLongitude..maximumUkLongitude
}

internal fun isNearRegionBoundary(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double,
    features: List<RegionFeature>,
): Boolean {
    if (!accuracyMeters.isFinite() || accuracyMeters <= 0.0) return false
    return features.any { feature ->
        if (regionNameToCode[feature.properties.longname] == null) return@any false
        feature.geometry?.coordinates.orEmpty().any { polygon ->
            polygon.any { ring ->
                ring.indices.any { index ->
                    distanceToSegmentMeters(
                        longitude = longitude,
                        latitude = latitude,
                        start = ring[(index + ring.size - 1) % ring.size],
                        end = ring[index],
                    ) <= accuracyMeters
                }
            }
        }
    }
}

private fun pointInPolygon(longitude: Double, latitude: Double, rings: List<List<List<Double>>>): Boolean {
    if (rings.isEmpty() || !pointInRing(longitude, latitude, rings.first())) return false
    return rings.drop(1).none { pointInRing(longitude, latitude, it) }
}

private fun pointInRing(longitude: Double, latitude: Double, ring: List<List<Double>>): Boolean {
    var inside = false
    ring.indices.forEach { index ->
        val previous = ring[(index + ring.size - 1) % ring.size]
        val current = ring[index]
        if ((previous[1] > latitude) != (current[1] > latitude) &&
            longitude < (current[0] - previous[0]) * (latitude - previous[1]) /
            (current[1] - previous[1]) + previous[0]
        ) {
            inside = !inside
        }
    }
    return inside
}

private fun pointOnSegment(longitude: Double, latitude: Double, start: List<Double>, end: List<Double>): Boolean {
    val cross = (latitude - start[1]) * (end[0] - start[0]) -
        (longitude - start[0]) * (end[1] - start[1])
    return kotlin.math.abs(cross) <= 1e-10 &&
        longitude in minOf(start[0], end[0])..maxOf(start[0], end[0]) &&
        latitude in minOf(start[1], end[1])..maxOf(start[1], end[1])
}

private fun distanceToSegmentMeters(
    longitude: Double,
    latitude: Double,
    start: List<Double>,
    end: List<Double>,
): Double {
    val metersPerDegreeLatitude = 111_320.0
    val metersPerDegreeLongitude = metersPerDegreeLatitude * cos(Math.toRadians(latitude))
    val startX = (start[0] - longitude) * metersPerDegreeLongitude
    val startY = (start[1] - latitude) * metersPerDegreeLatitude
    val endX = (end[0] - longitude) * metersPerDegreeLongitude
    val endY = (end[1] - latitude) * metersPerDegreeLatitude
    val deltaX = endX - startX
    val deltaY = endY - startY
    val lengthSquared = deltaX * deltaX + deltaY * deltaY
    if (lengthSquared == 0.0) return hypot(startX, startY)
    val factor = max(0.0, min(1.0, -(startX * deltaX + startY * deltaY) / lengthSquared))
    return hypot(startX + factor * deltaX, startY + factor * deltaY)
}

internal fun loadRegionFeatures(context: Context): List<RegionFeature> =
    context.assets.open("gb-electricity-regions.geojson").bufferedReader().use { reader ->
        regionJson.decodeFromString<RegionFeatureCollection>(reader.readText()).features
    }

internal class RegionLocationSuggester(private val context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val lookupExecutor = Executors.newSingleThreadExecutor()
    private var cancellationSignal: CancellationSignal? = null
    private var requestGeneration = 0

    fun request(onResult: (Result<String>) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onResult(Result.failure(IllegalStateException("Location permission was not granted.")))
            return
        }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            onResult(Result.failure(IllegalStateException("No location provider is enabled.")))
            return
        }
        cancel()
        cancellationSignal = CancellationSignal()
        val generation = ++requestGeneration
        runCatching {
            locationManager.getCurrentLocation(provider, cancellationSignal, mainExecutor) { location: Location? ->
                if (location == null) {
                    if (generation == requestGeneration) {
                        onResult(Result.failure(IllegalStateException("Could not determine your location.")))
                    }
                } else {
                    lookupExecutor.execute {
                        val result = runCatching { matchLocationToRegion(location) }
                        mainExecutor.execute {
                            if (generation == requestGeneration) onResult(result)
                        }
                    }
                }
            }
        }.onFailure {
            if (generation == requestGeneration) onResult(Result.failure(it))
        }
    }

    fun cancel() {
        requestGeneration++
        cancellationSignal?.cancel()
        cancellationSignal = null
    }

    private fun matchLocationToRegion(location: Location): String {
        val latitude = location.latitude
        val longitude = location.longitude
        if (isClearlyOutsideUk(latitude, longitude)) {
            throw IllegalStateException(outsideUkLocationMessage)
        }
        val features = loadRegionFeatures(context)
        val regionCode = suggestRegionCode(latitude, longitude, features)
            ?: throw IllegalStateException(
                "This location isn't within the Great Britain electricity regions supported by the app. " +
                    "Choose your region manually.",
            )
        if (
            location.hasAccuracy() &&
            isNearRegionBoundary(latitude, longitude, location.accuracy.toDouble(), features)
        ) {
            throw IllegalStateException(
                "This location is close to a region boundary. Confirm or choose your region manually.",
            )
        }
        return regionCode
    }

    fun close() {
        cancel()
        lookupExecutor.shutdownNow()
    }
}
