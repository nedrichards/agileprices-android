package com.nedrichards.agileprices

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun extractProductCode(selectedTariffCode: String): String =
    selectedTariffCode.split("-").drop(2).dropLast(1).joinToString("-")

fun currentPriceAt(prices: List<PriceWindow>, now: Instant): PriceWindow? =
    prices.firstOrNull { it.validFrom <= now && now < it.validTo }

internal fun List<PriceWindow>.sortedByValidFrom(): List<PriceWindow> =
    sortedBy { it.validFrom }

fun findBestLoadWindow(
    prices: List<PriceWindow>,
    now: Instant,
    durationMinutes: Int,
    searchHorizonMinutes: Int,
): BestWindow? {
    prices.requireSortedByValidFrom()
    return findCheapestContinuousSlotSorted(prices, now, durationMinutes, searchHorizonMinutes)
        ?: findCheapestBoundarySlotSorted(prices, now, durationMinutes, searchHorizonMinutes, wholeHourStartsOnly = false)
        ?: findCheapestBoundarySlotSorted(prices, now, durationMinutes, searchHorizonMinutes, wholeHourStartsOnly = true)
}

fun findCheapestContinuousSlot(
    prices: List<PriceWindow>,
    now: Instant,
    durationMinutes: Int,
    searchHorizonMinutes: Int,
): BestWindow? {
    prices.requireSortedByValidFrom()
    return findCheapestContinuousSlotSorted(prices, now, durationMinutes, searchHorizonMinutes)
}

private fun findCheapestContinuousSlotSorted(
    prices: List<PriceWindow>,
    now: Instant,
    durationMinutes: Int,
    searchHorizonMinutes: Int,
): BestWindow? {
    if (durationMinutes <= 0 || searchHorizonMinutes <= 0) return null

    val firstCandidate = now.nextMinuteBoundary()
    val duration = Duration.ofMinutes(durationMinutes.toLong())
    val cutoff = firstCandidate.plus(Duration.ofMinutes(searchHorizonMinutes.toLong()))
    val candidates = generateSequence(firstCandidate) { it.plus(Duration.ofMinutes(1)) }
        .takeWhile { it < cutoff }

    return candidates.mapNotNull { start ->
        weightedAveragePrice(prices, start, start.plus(duration))?.let { average ->
            BestWindow(start, start.plus(duration), average)
        }
    }.minByOrNull { it.averagePricePencePerKwh }
}

private fun Instant.nextMinuteBoundary(): Instant {
    val truncated = truncatedTo(ChronoUnit.MINUTES)
    return if (truncated == this) truncated else truncated.plus(Duration.ofMinutes(1))
}

fun findCheapestBoundarySlot(
    prices: List<PriceWindow>,
    now: Instant,
    durationMinutes: Int,
    searchHorizonMinutes: Int,
    wholeHourStartsOnly: Boolean,
): BestWindow? {
    prices.requireSortedByValidFrom()
    return findCheapestBoundarySlotSorted(prices, now, durationMinutes, searchHorizonMinutes, wholeHourStartsOnly)
}

private fun findCheapestBoundarySlotSorted(
    prices: List<PriceWindow>,
    now: Instant,
    durationMinutes: Int,
    searchHorizonMinutes: Int,
    wholeHourStartsOnly: Boolean,
): BestWindow? {
    if (durationMinutes <= 0 || searchHorizonMinutes <= 0) return null

    val duration = Duration.ofMinutes(durationMinutes.toLong())
    val cutoff = now.plus(Duration.ofMinutes(searchHorizonMinutes.toLong()))
    val candidates = prices
        .filter { it.validFrom >= now && it.validFrom < cutoff }
        .filter { !wholeHourStartsOnly || it.validFrom.atZone(ZoneId.systemDefault()).minute == 0 }
        .mapNotNull { price ->
            weightedAveragePrice(prices, price.validFrom, price.validFrom.plus(duration))?.let { average ->
                BestWindow(price.validFrom, price.validFrom.plus(duration), average)
            }
        }

    return candidates.minByOrNull { it.averagePricePencePerKwh }
}

private fun weightedAveragePrice(
    prices: List<PriceWindow>,
    start: Instant,
    end: Instant,
): Double? {
    val durationMillis = Duration.between(start, end).toMillis()
    if (durationMillis <= 0) return null

    var cursor = start
    var totalPriceMillis = 0.0

    for (price in prices) {
        if (price.validTo <= cursor) continue
        if (price.validFrom >= end) break
        if (price.validFrom > cursor) return null

        val overlapStart = maxOf(cursor, price.validFrom)
        val overlapEnd = minOf(end, price.validTo)
        if (overlapEnd <= overlapStart) continue

        totalPriceMillis += price.pricePencePerKwh * Duration.between(overlapStart, overlapEnd).toMillis()
        cursor = overlapEnd
        if (cursor >= end) return totalPriceMillis / durationMillis
    }

    return null
}

private fun List<PriceWindow>.requireSortedByValidFrom() {
    require(isSortedByValidFrom()) {
        "Price windows must be sorted by validFrom before price calculations."
    }
}

private fun List<PriceWindow>.isSortedByValidFrom(): Boolean {
    for (index in 1 until size) {
        if (this[index - 1].validFrom > this[index].validFrom) return false
    }
    return true
}
