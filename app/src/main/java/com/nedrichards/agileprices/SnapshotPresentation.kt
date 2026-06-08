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
