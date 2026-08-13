package com.nedrichards.agileprices

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

@Composable
internal fun AgilePricesPhoneContent(
    showingSetup: Boolean,
    widthClass: AdaptiveWidthClass,
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    now: Instant,
    busy: Boolean,
    message: String?,
    onSelectRegion: (ElectricityRegion) -> Unit,
    onRefresh: () -> Unit,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
    onChangeRegion: () -> Unit,
    onSuggestRegion: () -> Unit,
    onEnableNegativePriceAlerts: () -> Unit,
) {
    AgilePhoneTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            if (showingSetup) {
                PhoneRegionSetupScreen(
                    widthClass = widthClass,
                    busy = busy,
                    message = message ?: snapshot.message,
                    onSelectRegion = onSelectRegion,
                    onSuggestRegion = onSuggestRegion,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                PhonePriceScreen(
                    widthClass = widthClass,
                    snapshot = snapshot,
                    settings = settings,
                    now = now,
                    busy = busy,
                    message = message,
                    onRefresh = onRefresh,
                    onLoadDurationChanged = onLoadDurationChanged,
                    onSearchHorizonChanged = onSearchHorizonChanged,
                    onChangeRegion = onChangeRegion,
                    onEnableNegativePriceAlerts = onEnableNegativePriceAlerts,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun AgilePhoneTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
internal fun PhoneRegionSetupScreen(
    widthClass: AdaptiveWidthClass,
    busy: Boolean,
    message: String?,
    onSelectRegion: (ElectricityRegion) -> Unit,
    onSuggestRegion: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
    val regionButton: @Composable (ElectricityRegion) -> Unit = { region ->
        Button(
            onClick = { onSelectRegion(region) },
            enabled = !busy,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = region.name,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        }
    }

    if (widthClass != AdaptiveWidthClass.Compact) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            modifier = modifier
                .fillMaxSize()
                .testTag("phone_setup_grid"),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Choose region",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (message != null) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Optional. Uses one location fix locally and does not store it. Check the suggestion against your electricity account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                OutlinedButton(
                    onClick = onSuggestRegion,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Use my location") }
            }
            items(ukElectricityRegions) { region -> regionButton(region) }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("phone_setup_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Choose region",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (message != null) {
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                text = "Optional. Uses one location fix locally and does not store it. Check the suggestion against your electricity account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(
                onClick = onSuggestRegion,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use my location") }
        }
        items(ukElectricityRegions) { region ->
            regionButton(region)
        }
    }
}

@Composable
internal fun PhonePriceScreen(
    widthClass: AdaptiveWidthClass,
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    now: Instant,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
    onChangeRegion: () -> Unit,
    onEnableNegativePriceAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (widthClass) {
        AdaptiveWidthClass.Compact -> CompactPhonePriceScreen(
            snapshot = snapshot,
            settings = settings,
            now = now,
            busy = busy,
            message = message,
            onRefresh = onRefresh,
            onLoadDurationChanged = onLoadDurationChanged,
            onSearchHorizonChanged = onSearchHorizonChanged,
            onChangeRegion = onChangeRegion,
            onEnableNegativePriceAlerts = onEnableNegativePriceAlerts,
            modifier = modifier,
        )
        AdaptiveWidthClass.Medium,
        AdaptiveWidthClass.Expanded -> WidePhonePriceScreen(
            widthClass = widthClass,
            snapshot = snapshot,
            settings = settings,
            now = now,
            busy = busy,
            message = message,
            onRefresh = onRefresh,
            onLoadDurationChanged = onLoadDurationChanged,
            onSearchHorizonChanged = onSearchHorizonChanged,
            onChangeRegion = onChangeRegion,
            onEnableNegativePriceAlerts = onEnableNegativePriceAlerts,
            modifier = modifier,
        )
    }
}

@Composable
private fun CompactPhonePriceScreen(
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    now: Instant,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
    onChangeRegion: () -> Unit,
    onEnableNegativePriceAlerts: () -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("phone_compact_price_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PhonePriceHero(snapshot = snapshot)
        }
        item {
            PhoneBestWindowPanel(
                snapshot = snapshot,
                loadDurationMinutes = settings.loadDurationMinutes,
                now = now,
            )
        }
        item {
            PhoneControlsPanel(
                settings = settings,
                onLoadDurationChanged = onLoadDurationChanged,
                onSearchHorizonChanged = onSearchHorizonChanged,
            )
        }
        if (snapshot.sparklinePrices.isNotEmpty()) {
            item {
                PhoneInteractivePriceGraph(
                    prices = snapshot.sparklinePrices,
                    bestWindow = snapshot.bestWindow,
                    now = now,
                )
            }
        }
        item {
            PhoneStatusAndSetupPanel(
                snapshot = snapshot,
                settings = settings,
                busy = busy,
                message = message,
                onRefresh = onRefresh,
                onChangeRegion = onChangeRegion,
                onEnableNegativePriceAlerts = onEnableNegativePriceAlerts,
            )
        }
    }
}

@Composable
private fun WidePhonePriceScreen(
    widthClass: AdaptiveWidthClass,
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    now: Instant,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
    onChangeRegion: () -> Unit,
    onEnableNegativePriceAlerts: () -> Unit,
    modifier: Modifier,
) {
    val paneMaxWidth = if (widthClass == AdaptiveWidthClass.Expanded) 420.dp else 360.dp

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("phone_two_pane"),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1.7f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PhonePriceHero(snapshot = snapshot) }
            item {
                PhoneBestWindowPanel(
                    snapshot = snapshot,
                    loadDurationMinutes = settings.loadDurationMinutes,
                    now = now,
                )
            }
            item {
                PhoneControlsPanel(
                    settings = settings,
                    onLoadDurationChanged = onLoadDurationChanged,
                    onSearchHorizonChanged = onSearchHorizonChanged,
                )
            }
            if (snapshot.sparklinePrices.isNotEmpty()) {
                item {
                    PhoneInteractivePriceGraph(
                        prices = snapshot.sparklinePrices,
                        bestWindow = snapshot.bestWindow,
                        now = now,
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .widthIn(max = paneMaxWidth)
                .weight(1f)
                .fillMaxHeight()
                .testTag("phone_supporting_pane"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PhoneStatusAndSetupPanel(
                    snapshot = snapshot,
                    settings = settings,
                    busy = busy,
                    message = message,
                    onRefresh = onRefresh,
                    onChangeRegion = onChangeRegion,
                    onEnableNegativePriceAlerts = onEnableNegativePriceAlerts,
                )
            }
        }
    }
}

@Composable
private fun PhonePriceHero(
    snapshot: PriceSnapshot,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Agile Prices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = snapshot.phoneCurrentPriceText(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("phone_current_price"),
            )
        }
    }
}

@Composable
private fun PhoneBestWindowPanel(
    snapshot: PriceSnapshot,
    loadDurationMinutes: Int,
    now: Instant,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = cheapestWindowLabel(loadDurationMinutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val bestWindow = snapshot.bestWindow
            if (bestWindow == null) {
                Text(
                    text = "No complete window",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = snapshot.secondaryStatusText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Cheapest",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatWindowRange(bestWindow.start, bestWindow.end, now),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${bestWindow.averagePricePencePerKwh.formatPrice()}p/kWh average",
                    style = MaterialTheme.typography.titleMedium,
                )
                val timerRecommendations = snapshot.timerRecommendationPresentations(now)
                if (timerRecommendations.isNotEmpty()) {
                    Text(
                        text = "Appliance timers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    timerRecommendations.forEach { recommendation ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(recommendation.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                recommendation.timerValue,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        MeasuredTimerDetail(recommendation)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasuredTimerDetail(recommendation: TimerRecommendationPresentation) {
    val style = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maximumWidthPx = with(density) { maxWidth.roundToPx() }
        val displayedText = recommendation.detailOptions.firstOrNull { option ->
            textMeasurer.measure(
                text = option,
                style = style,
                softWrap = false,
                maxLines = 1,
            ).size.width <= maximumWidthPx
        } ?: recommendation.detailOptions.last()
        Text(
            text = displayedText,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics {
                contentDescription = recommendation.detail
            }.testTag("phone_timer_detail_${recommendation.label.lowercase().replace(' ', '_')}"),
        )
    }
}

@Composable
private fun PhoneInteractivePriceGraph(
    prices: List<PriceWindow>,
    bestWindow: BestWindow?,
    now: Instant,
) {
    val visiblePrices = prices
        .filter { it.validTo > now && it.validFrom < now.plus(Duration.ofHours(24)) }
    if (visiblePrices.size < 2) return

    val label = sparklineLabel(visiblePrices, now)
    val defaultIndex = visiblePrices.indexOfFirst { it.validFrom <= now && it.validTo > now }
        .takeIf { it >= 0 }
        ?: 0
    var selectedStart by remember(prices) { mutableStateOf<Instant?>(null) }
    val selectedIndex = selectedStart
        ?.let { start -> visiblePrices.indexOfFirst { it.validFrom == start } }
        ?.takeIf { it >= 0 }
        ?: defaultIndex
    val coercedSelectedIndex = selectedIndex.coerceIn(visiblePrices.indices)
    val selectedPrice = visiblePrices[coercedSelectedIndex]
    val cheapestIndex = visiblePrices.indices.minBy { visiblePrices[it].pricePencePerKwh }
    val cheapestPrice = visiblePrices[cheapestIndex]
    val minPrice = visiblePrices.minOf { it.pricePencePerKwh }
    val maxPrice = visiblePrices.maxOf { it.pricePencePerKwh }
    val priceRange = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
    val graphInsights = priceGraphInsights(
        prices = visiblePrices,
        cheapestPrice = cheapestPrice,
        minPrice = minPrice,
        maxPrice = maxPrice,
        now = now,
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val cheapestColor = MaterialTheme.colorScheme.error
    val selectedStatus = selectedPrice.bestWindowStatus(bestWindow)
    val selectedPriceText = buildString {
        append(selectedPrice.pricePencePerKwh.formatPrice())
        append("p/kWh")
        if (selectedStatus != null) {
            append(" · ")
            append(selectedStatus)
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Upcoming prices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_selected_price"),
            ) {
                Text(
                    text = formatWindowRange(selectedPrice.validFrom, selectedPrice.validTo, now),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = selectedPriceText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("phone_price_sparkline")
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                selectedStart =
                                    visiblePrices[(coercedSelectedIndex - 1).coerceAtLeast(0)].validFrom
                                true
                            }
                            Key.DirectionRight -> {
                                selectedStart =
                                    visiblePrices[(coercedSelectedIndex + 1).coerceAtMost(visiblePrices.lastIndex)].validFrom
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .pointerInput(visiblePrices) {
                        fun updateSelection(x: Float) {
                            selectedStart = visiblePrices[
                                x.nearestPriceIndex(width = size.width, lastIndex = visiblePrices.lastIndex)
                            ].validFrom
                        }
                        detectTapGestures { offset -> updateSelection(offset.x) }
                    }
                    .pointerInput(visiblePrices) {
                        fun updateSelection(x: Float) {
                            selectedStart = visiblePrices[
                                x.nearestPriceIndex(width = size.width, lastIndex = visiblePrices.lastIndex)
                            ].validFrom
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> updateSelection(offset.x) },
                            onHorizontalDrag = { change, _ -> updateSelection(change.position.x) },
                        )
                    }
                    .semantics {
                        contentDescription = "$label price graph. Selected ${formatWindowRange(selectedPrice.validFrom, selectedPrice.validTo, now)} at ${selectedPrice.pricePencePerKwh.formatPrice()} pence per kilowatt hour"
                    },
            ) {
                val top = 8f
                val bottom = size.height - 8f
                val plotHeight = bottom - top
                fun xFor(index: Int): Float =
                    if (visiblePrices.size == 1) {
                        size.width / 2f
                    } else {
                        size.width * index / visiblePrices.lastIndex.toFloat()
                    }
                fun yFor(price: Double): Float =
                    top + ((maxPrice - price) / priceRange).toFloat() * plotHeight

                val bestStartIndex = bestWindow?.let { window ->
                    visiblePrices.indexOfFirst { it.validTo > window.start && it.validFrom < window.end }
                } ?: -1
                val bestEndIndex = bestWindow?.let { window ->
                    visiblePrices.indexOfLast { it.validTo > window.start && it.validFrom < window.end }
                } ?: -1
                if (bestStartIndex >= 0 && bestEndIndex >= bestStartIndex) {
                    val left = xFor(bestStartIndex).coerceAtLeast(0f)
                    val right = xFor(bestEndIndex).coerceAtLeast(left + 1f)
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                }

                if (minPrice < 0.0 && maxPrice > 0.0) {
                    val zeroY = yFor(0.0)
                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, zeroY),
                        end = Offset(size.width, zeroY),
                        strokeWidth = 1f,
                    )
                }

                val path = Path()
                visiblePrices.forEachIndexed { index, price ->
                    val pointX = xFor(index)
                    val pointY = yFor(price.pricePencePerKwh)
                    if (index == 0) {
                        path.moveTo(pointX, pointY)
                    } else {
                        path.lineTo(pointX, pointY)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4f),
                )
                drawCircle(
                    color = markerColor,
                    radius = 5f,
                    center = Offset(xFor(0), yFor(visiblePrices.first().pricePencePerKwh)),
                )
                drawCircle(
                    color = cheapestColor,
                    radius = 5.5f,
                    center = Offset(xFor(cheapestIndex), yFor(cheapestPrice.pricePencePerKwh)),
                )
                val selectedX = xFor(coercedSelectedIndex)
                drawLine(
                    color = selectedColor,
                    start = Offset(selectedX, 0f),
                    end = Offset(selectedX, size.height),
                    strokeWidth = 2f,
                )
                drawCircle(
                    color = selectedColor,
                    radius = 7f,
                    center = Offset(selectedX, yFor(selectedPrice.pricePencePerKwh)),
                )
            }
            GraphTimeLabels(
                start = "Current",
                middle = formatTime(visiblePrices[visiblePrices.lastIndex / 2].validFrom),
                end = formatTime(visiblePrices.last().validTo),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.testTag("phone_price_insights"),
            ) {
                graphInsights.forEachIndexed { index, insight ->
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (index == 0) {
                            Modifier.testTag("phone_cheapest_price")
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

private fun Float.nearestPriceIndex(
    width: Int,
    lastIndex: Int,
): Int {
    if (lastIndex <= 0 || width <= 0) return 0
    return ((coerceIn(0f, width.toFloat()) / width.toFloat()) * lastIndex)
        .roundToInt()
        .coerceIn(0, lastIndex)
}

private fun PriceWindow.bestWindowStatus(bestWindow: BestWindow?): String? {
    if (bestWindow == null) return null
    return if (validTo > bestWindow.start && validFrom < bestWindow.end) {
        "In cheapest run"
    } else {
        null
    }
}

private fun priceGraphInsights(
    prices: List<PriceWindow>,
    cheapestPrice: PriceWindow,
    minPrice: Double,
    maxPrice: Double,
    now: Instant,
): List<String> {
    val insights = mutableListOf(
        "Cheapest visible slot ${formatWindowRange(cheapestPrice.validFrom, cheapestPrice.validTo, now)} · ${cheapestPrice.pricePencePerKwh.formatPrice()}p/kWh",
    )
    val negativeSlots = prices.count { it.pricePencePerKwh < 0.0 }
    if (negativeSlots > 0) {
        val slotText = if (negativeSlots == 1) "half-hour" else "half-hours"
        insights += "Negative prices for $negativeSlots $slotText"
    }
    val swing = maxPrice - minPrice
    if (swing >= 10.0) {
        insights += "Price swing ${swing.formatPrice()}p/kWh across the visible range"
    }
    return insights
}

private fun cheapestWindowLabel(durationMinutes: Int): String =
    "Cheapest ${Duration.ofMinutes(durationMinutes.toLong()).toCompactDurationText()} window"

@Composable
private fun GraphTimeLabels(
    start: String,
    middle: String,
    end: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(middle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(end, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhoneControlsPanel(
    settings: AgileSettings,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Planning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PhoneDurationControl(
                label = "Run time",
                valueText = Duration.ofMinutes(settings.loadDurationMinutes.toLong()).toCompactDurationText(),
                value = settings.loadDurationMinutes,
                range = 30f..480f,
                steps = 14,
                onValueChanged = { onLoadDurationChanged(it.roundToStep(30, 30, 480)) },
            )
            PhoneDurationControl(
                label = "Search horizon",
                valueText = "${settings.searchHorizonMinutes / 60}h",
                value = settings.searchHorizonMinutes,
                range = 60f..1440f,
                steps = 22,
                onValueChanged = { onSearchHorizonChanged(it.roundToStep(60, 60, 1440)) },
            )
        }
    }
}

@Composable
private fun PhoneDurationControl(
    label: String,
    valueText: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChanged: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChanged,
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = "Adjust phone $label" },
        )
    }
}

@Composable
private fun PhoneStatusAndSetupPanel(
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onChangeRegion: () -> Unit,
    onEnableNegativePriceAlerts: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = message ?: snapshot.message ?: snapshot.secondaryStatusText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !busy,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_refresh_action"),
            ) {
                Text(if (busy) "Refreshing" else "Refresh")
            }
            HorizontalDivider()
            PhoneSetupLine("Region", settings.selectedRegionCode?.let { regionCodeToName[it] ?: it } ?: "No region")
            PhoneSetupLine("Tariff", settings.selectedTariffCode ?: "No tariff")
            PhoneSetupLine("Updated", snapshot.fetchedAt?.let { formatDateTime(it) } ?: "Not refreshed yet")
            PhoneSetupLine("Cache", snapshot.validUntil?.let { "Until ${formatDateTime(it)}" } ?: "No cached rates")
            PhoneSetupLine(
                "Alerts",
                if (NegativePriceNotifier.canPost(LocalContext.current)) "At/below zero" else "Off",
            )
            if (!NegativePriceNotifier.canPost(LocalContext.current)) {
                OutlinedButton(
                    onClick = onEnableNegativePriceAlerts,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_enable_negative_alerts"),
                ) {
                    Text("Enable at-or-below-zero alerts")
                }
            }
            PhoneSetupLine("Data", "Octopus Energy API")
            OutlinedButton(
                onClick = onChangeRegion,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change region")
            }
        }
    }
}

@Composable
private fun PhoneSetupLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun PriceSnapshot.phoneCurrentPriceText(): String =
    if (status == SnapshotStatus.Loaded) {
        currentPrice?.pricePencePerKwh?.let { "${it.formatPrice()}p/kWh" } ?: "--"
    } else {
        primaryPriceValueForApp()
    }

private fun Float.roundToStep(
    step: Int,
    minimum: Int,
    maximum: Int,
): Int {
    val rounded = (this / step).roundToInt() * step
    return rounded.coerceIn(minimum, maximum)
}
