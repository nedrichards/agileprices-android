package com.nedrichards.agileprices

import java.time.Duration
import java.time.Instant

fun PriceSnapshot.primaryPriceText(): String =
    if (status == SnapshotStatus.Loaded) {
        currentPrice?.pricePencePerKwh?.let { "${it.formatPrice()}p" } ?: "No current"
    } else {
        when (status) {
            SnapshotStatus.NoSetup -> "Setup"
            SnapshotStatus.Loading -> "Loading"
            SnapshotStatus.Loaded -> "No current"
            SnapshotStatus.Stale -> "Stale"
            SnapshotStatus.Error -> "No data"
        }
    }

fun PriceSnapshot.primaryPriceValueForApp(): String =
    if (status == SnapshotStatus.Loaded) {
        currentPrice?.pricePencePerKwh?.formatPrice() ?: "--"
    } else {
        "--"
    }

fun PriceSnapshot.primaryPriceCaption(): String =
    when (status) {
        SnapshotStatus.NoSetup -> "Choose region"
        SnapshotStatus.Loading -> "Loading prices"
        SnapshotStatus.Loaded -> if (currentPrice != null) "p/kWh now" else "No current price"
        SnapshotStatus.Stale -> "Cached data expired"
        SnapshotStatus.Error -> "Price data unavailable"
    }

fun PriceSnapshot.secondaryStatusText(): String =
    when (status) {
        SnapshotStatus.NoSetup -> "Choose a region to load Agile prices"
        SnapshotStatus.Loading -> "Loading prices"
        SnapshotStatus.Loaded -> fetchedAt?.let { "Updated ${formatDateTime(it)}" } ?: "Current price loaded"
        SnapshotStatus.Stale -> validUntil?.let { "Cache ended ${formatDateTime(it)}" } ?: "Cached prices are stale"
        SnapshotStatus.Error -> message ?: "No cached price data"
    }

fun PriceSnapshot.complicationDescription(): String =
    when (status) {
        SnapshotStatus.Loaded -> currentPrice?.pricePencePerKwh?.let {
            "Current Agile electricity price ${it.formatPrice()} pence per kilowatt hour"
        } ?: "No current Agile electricity price is available"
        SnapshotStatus.NoSetup -> "Agile Prices needs a region setup"
        SnapshotStatus.Loading -> "Agile electricity prices are loading"
        SnapshotStatus.Stale -> "Cached Agile electricity prices are stale"
        SnapshotStatus.Error -> "Agile electricity price data is unavailable"
    }

data class TilePresentation(
    val currentText: String,
    val caption: String,
    val detail: String,
)

fun PriceSnapshot.tilePresentation(): TilePresentation =
    TilePresentation(
        currentText = primaryPriceText(),
        caption = primaryPriceCaption(),
        detail = if (status == SnapshotStatus.Loaded) {
            bestWindow?.let {
                "${formatTime(it.start)}-${formatTime(it.end)} ${it.averagePricePencePerKwh.formatPrice()}p"
            } ?: "No complete window"
        } else {
            secondaryStatusText()
        },
    )

data class ComplicationPresentation(
    val text: String,
    val contentDescription: String,
)

fun PriceSnapshot.complicationPresentation(): ComplicationPresentation =
    ComplicationPresentation(
        text = primaryPriceText(),
        contentDescription = complicationDescription(),
    )

fun BestWindow.startsInText(now: Instant): String {
    val remaining = Duration.between(now, start)
    return if (remaining.isNegative || remaining.isZero) {
        "Starts now"
    } else {
        "Starts in ${remaining.toCompactDurationText()}"
    }
}

fun BestWindow.endsInText(now: Instant): String {
    val remaining = Duration.between(now, end)
    return if (remaining.isNegative || remaining.isZero) {
        "Already ended"
    } else {
        "Ends in ${remaining.toCompactDurationText()}"
    }
}

fun BestWindow.compactTimingText(now: Instant): String =
    "${compactStartsText(now)} / ${compactEndsText(now)}"

private fun BestWindow.compactStartsText(now: Instant): String {
    val remaining = Duration.between(now, start)
    return if (remaining.isNegative || remaining.isZero) {
        "Now"
    } else {
        "In ${remaining.toCompactDurationText()}"
    }
}

private fun BestWindow.compactEndsText(now: Instant): String {
    val remaining = Duration.between(now, end)
    return if (remaining.isNegative || remaining.isZero) {
        "Ended"
    } else {
        "Ends ${remaining.toCompactDurationText()}"
    }
}

data class TimerRecommendationPresentation(
    val label: String,
    val timerValue: String,
    val detail: String,
    val detailOptions: List<String>,
    val averagePricePencePerKwh: Double,
)

fun PriceSnapshot.timerRecommendationPresentations(now: Instant): List<TimerRecommendationPresentation> {
    val exact = bestWindow ?: return emptyList()
    return listOfNotNull(
        startTimerWindow?.let {
            TimerRecommendationPresentation(
                label = "Start in",
                timerValue = "${it.timerDelayHours(now, ApplianceTimerMode.Start)}h",
                detail = it.timerDetail(exact, now),
                detailOptions = it.timerDetailOptions(exact, now),
                averagePricePencePerKwh = it.averagePricePencePerKwh,
            )
        },
        finishTimerWindow?.let {
            TimerRecommendationPresentation(
                label = "Finish in",
                timerValue = "${it.timerDelayHours(now, ApplianceTimerMode.Finish)}h",
                detail = it.timerDetail(exact, now),
                detailOptions = it.timerDetailOptions(exact, now),
                averagePricePencePerKwh = it.averagePricePencePerKwh,
            )
        },
    )
}

data class WearTimerPresentation(
    val startNowText: String?,
    val recommendationRows: List<String>,
)

fun PriceSnapshot.wearTimerPresentation(now: Instant): WearTimerPresentation =
    WearTimerPresentation(
        startNowText = startNowWindow?.let {
            "Start now · ${it.averagePricePencePerKwh.formatPrice()}p avg"
        },
        recommendationRows = timerRecommendationPresentations(now).map {
            "${it.label} ${it.timerValue} · ${it.averagePricePencePerKwh.formatPrice()}p avg"
        },
    )

private fun BestWindow.timerDelayHours(now: Instant, mode: ApplianceTimerMode): Long {
    val timerTime = when (mode) {
        ApplianceTimerMode.Start -> start
        ApplianceTimerMode.Finish -> end
    }
    return Duration.between(now.nextMinuteBoundary(), timerTime).toHours()
}

private fun BestWindow.timerDetail(exact: BestWindow, now: Instant): String =
    timerDetailOptions(exact, now).first()

private fun BestWindow.timerDetailOptions(exact: BestWindow, now: Instant): List<String> {
    val range = formatWindowRange(start, end, now)
    val price = averagePricePencePerKwh.formatPrice()
    val fullDelta = formatPriceDelta(averagePricePencePerKwh, exact.averagePricePencePerKwh)
    val compactDelta = formatCompactPriceDelta(averagePricePencePerKwh, exact.averagePricePencePerKwh)
    return buildList {
        add(listOfNotNull(range, "${price}p/kWh average", fullDelta).joinToString(" · "))
        add(listOfNotNull(range, "${price}p/kWh avg", fullDelta).joinToString(" · "))
        add(listOfNotNull(range, "${price}p avg", compactDelta).joinToString(" · "))
        add("$range · ${price}p/kWh avg")
        add("$range · ${price}p")
    }.distinct()
}

internal fun formatPriceDelta(averagePrice: Double, exactAveragePrice: Double): String? {
    val deltaPence = (averagePrice - exactAveragePrice).coerceAtLeast(0.0)
    if (deltaPence < 0.05) return null
    return "+${String.format(java.util.Locale.UK, "%.1f", deltaPence)}p/kWh"
}

private fun formatCompactPriceDelta(averagePrice: Double, exactAveragePrice: Double): String? =
    formatPriceDelta(averagePrice, exactAveragePrice)?.removeSuffix("/kWh")

fun BestWindow.durationText(): String =
    "Duration ${Duration.between(start, end).toCompactDurationText()}"

fun Duration.toCompactDurationText(): String {
    val totalMinutes = toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
