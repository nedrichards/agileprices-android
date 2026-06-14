package com.nedrichards.agileprices

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.window.core.layout.WindowSizeClass
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = createRepository(applicationContext)
        val surface = chooseSurface(applicationContext.isWatchDevice())

        setContent {
            AgilePricesApp(
                repository = repository,
                surface = surface,
                adaptiveWidthClass = currentAdaptiveWidthClass(),
            )
        }
    }
}

internal enum class AgileSurface {
    Wear,
    Phone,
}

internal enum class AdaptiveWidthClass {
    Compact,
    Medium,
    Expanded,
}

internal fun chooseSurface(isWatchDevice: Boolean): AgileSurface =
    if (isWatchDevice) AgileSurface.Wear else AgileSurface.Phone

private fun Context.isWatchDevice(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

@Composable
private fun currentAdaptiveWidthClass(): AdaptiveWidthClass {
    val windowSizeClass = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass
    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            AdaptiveWidthClass.Expanded
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            AdaptiveWidthClass.Medium
        else -> AdaptiveWidthClass.Compact
    }
}

@Composable
private fun AgilePricesApp(
    repository: AgileRepository,
    surface: AgileSurface,
    adaptiveWidthClass: AdaptiveWidthClass,
) {
    val appState by repository.appState.collectAsState(
        initial = AgileAppState(
            settings = AgileSettings(null, null, 60, 480, emptyList(), null, null),
            snapshot = PriceSnapshot(null, null, null, null, SnapshotStatus.Loading),
        ),
    )
    val settings = appState.settings
    val snapshot = appState.snapshot
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var choosingRegion by remember { mutableStateOf(false) }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000)
        }
    }

    LaunchedEffect(settings.selectedTariffCode) {
        if (!settings.selectedTariffCode.isNullOrBlank()) {
            RefreshWorker.schedule(appContext)
            if (settings.cachedPrices.isEmpty()) {
                busy = true
                actionMessage = "Refreshing"
                runCatching { repository.refresh() }
                    .onFailure { actionMessage = it.message ?: "Refresh failed" }
                    .onSuccess { actionMessage = null }
                busy = false
            }
        }
    }

    AgilePricesContent(
        surface = surface,
        adaptiveWidthClass = adaptiveWidthClass,
        snapshot = snapshot,
        settings = settings,
        now = now,
        busy = busy,
        message = actionMessage,
        choosingRegion = choosingRegion,
        onSelectRegion = { region ->
            scope.launch {
                busy = true
                actionMessage = "Loading ${region.name}"
                runCatching { repository.configureRegion(region.code) }
                    .onSuccess {
                        RefreshWorker.schedule(appContext)
                        actionMessage = null
                        choosingRegion = false
                    }
                    .onFailure { actionMessage = it.message ?: "Setup failed" }
                busy = false
            }
        },
        onRefresh = {
            scope.launch {
                busy = true
                actionMessage = "Refreshing"
                runCatching { repository.refresh() }
                    .onSuccess { actionMessage = null }
                    .onFailure { actionMessage = it.message ?: "Refresh failed" }
                busy = false
            }
        },
        onLoadDurationChanged = { value ->
            scope.launch { repository.setLoadDurationMinutes(value) }
        },
        onSearchHorizonChanged = { value ->
            scope.launch { repository.setSearchHorizonMinutes(value) }
        },
        onChangeRegion = {
            actionMessage = null
            choosingRegion = true
        },
        onDismissRegionPicker = {
            actionMessage = null
            choosingRegion = false
        },
    )
}

@Composable
internal fun AgilePricesContent(
    surface: AgileSurface = AgileSurface.Wear,
    adaptiveWidthClass: AdaptiveWidthClass = AdaptiveWidthClass.Compact,
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    now: Instant,
    busy: Boolean,
    message: String?,
    choosingRegion: Boolean,
    onSelectRegion: (ElectricityRegion) -> Unit,
    onRefresh: () -> Unit,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
    onChangeRegion: () -> Unit,
    onDismissRegionPicker: () -> Unit,
) {
    val showingSetup = choosingRegion || snapshot.status == SnapshotStatus.NoSetup
    BackHandler(enabled = choosingRegion && snapshot.status != SnapshotStatus.NoSetup) {
        onDismissRegionPicker()
    }

    when (surface) {
        AgileSurface.Wear -> AgilePricesWearContent(
            showingSetup = showingSetup,
            snapshot = snapshot,
            settings = settings,
            now = now,
            busy = busy,
            message = message,
            onSelectRegion = onSelectRegion,
            onRefresh = onRefresh,
            onLoadDurationChanged = onLoadDurationChanged,
            onSearchHorizonChanged = onSearchHorizonChanged,
            onChangeRegion = onChangeRegion,
        )
        AgileSurface.Phone -> AgilePricesPhoneContent(
            showingSetup = showingSetup,
            widthClass = adaptiveWidthClass,
            snapshot = snapshot,
            settings = settings,
            now = now,
            busy = busy,
            message = message,
            onSelectRegion = onSelectRegion,
            onRefresh = onRefresh,
            onLoadDurationChanged = onLoadDurationChanged,
            onSearchHorizonChanged = onSearchHorizonChanged,
            onChangeRegion = onChangeRegion,
        )
    }
}

@Composable
internal fun AgilePricesWearContent(
    showingSetup: Boolean,
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
) {
    AgileWearTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (showingSetup) {
                WearRegionSetupScreen(
                    busy = busy,
                    message = message ?: snapshot.message,
                    onSelectRegion = onSelectRegion,
                )
            } else {
                WearPriceScreen(
                    snapshot = snapshot,
                    settings = settings,
                    now = now,
                    busy = busy,
                    message = message,
                    onRefresh = onRefresh,
                    onLoadDurationChanged = onLoadDurationChanged,
                    onSearchHorizonChanged = onSearchHorizonChanged,
                    onChangeRegion = onChangeRegion,
                )
            }
        }
    }
}

@Composable
private fun AgileWearTheme(content: @Composable () -> Unit) {
    val dynamicColorScheme = dynamicColorScheme(LocalContext.current)
    if (dynamicColorScheme != null) {
        MaterialTheme(colorScheme = dynamicColorScheme, content = content)
    } else {
        MaterialTheme(content = content)
    }
}

@Composable
internal fun RegionSetupScreen(
    busy: Boolean,
    message: String?,
    onSelectRegion: (ElectricityRegion) -> Unit,
) {
    WearRegionSetupScreen(
        busy = busy,
        message = message,
        onSelectRegion = onSelectRegion,
    )
}

@Composable
internal fun WearRegionSetupScreen(
    busy: Boolean,
    message: String?,
    onSelectRegion: (ElectricityRegion) -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    scope.launch { scrollState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Choose region",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (message != null) {
                Text(
                    text = message,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ukElectricityRegions.forEach { region ->
                PillAction(
                    text = region.name,
                    enabled = !busy,
                    onClick = { onSelectRegion(region) },
                )
            }
        }
        ScrollIndicator(
            state = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .testTag("setup_scroll_indicator"),
        )
    }
}

@Composable
internal fun PriceScreen(
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    now: Instant,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
    onChangeRegion: () -> Unit,
) {
    WearPriceScreen(
        snapshot = snapshot,
        settings = settings,
        now = now,
        busy = busy,
        message = message,
        onRefresh = onRefresh,
        onLoadDurationChanged = onLoadDurationChanged,
        onSearchHorizonChanged = onSearchHorizonChanged,
        onChangeRegion = onChangeRegion,
    )
}

@Composable
internal fun WearPriceScreen(
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    now: Instant,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onLoadDurationChanged: (Int) -> Unit,
    onSearchHorizonChanged: (Int) -> Unit,
    onChangeRegion: () -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    scope.launch { listState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .testTag("price_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                PriceHeader(
                    snapshot = snapshot,
                    now = now,
                )
            }
            item {
                StepperRow(
                    label = "Run time",
                    value = Duration.ofMinutes(settings.loadDurationMinutes.toLong()).toCompactDurationText(),
                    minusEnabled = settings.loadDurationMinutes > 30,
                    plusEnabled = settings.loadDurationMinutes < 480,
                    onMinus = { onLoadDurationChanged(settings.loadDurationMinutes - 30) },
                    onPlus = { onLoadDurationChanged(settings.loadDurationMinutes + 30) },
                )
            }
            item {
                StepperRow(
                    label = "Search",
                    value = "${settings.searchHorizonMinutes / 60}h",
                    minusEnabled = settings.searchHorizonMinutes > 60,
                    plusEnabled = settings.searchHorizonMinutes < 1440,
                    onMinus = { onSearchHorizonChanged(settings.searchHorizonMinutes - 60) },
                    onPlus = { onSearchHorizonChanged(settings.searchHorizonMinutes + 60) },
                )
            }
            item {
                Text(
                    text = message ?: snapshot.message ?: snapshot.secondaryStatusText(),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (snapshot.sparklinePrices.isNotEmpty()) {
                item {
                    PriceSparkline(
                        prices = snapshot.sparklinePrices,
                        bestWindow = snapshot.bestWindow,
                        now = now,
                    )
                }
            }
            if (snapshot.upcoming.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Next slots", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                items(snapshot.upcoming) { price ->
                    PriceRow(price)
                }
            }
            item {
                PillAction(
                    text = if (busy) "Refreshing" else "Refresh",
                    enabled = !busy,
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_action"),
                    filled = false,
                )
            }
            item {
                SetupSummary(
                    snapshot = snapshot,
                    settings = settings,
                    onChangeRegion = onChangeRegion,
                )
            }
        }
        ScrollIndicator(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .testTag("price_scroll_indicator"),
        )
    }
}

@Composable
private fun SetupSummary(
    snapshot: PriceSnapshot,
    settings: AgileSettings,
    onChangeRegion: () -> Unit,
) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Setup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = settings.selectedRegionCode?.let { regionCodeToName[it] ?: it } ?: "No region",
                fontSize = 12.sp,
            )
            Text(
                text = settings.selectedTariffCode ?: "No tariff",
                fontSize = 10.sp,
            )
            Text(
                text = snapshot.fetchedAt?.let { "Updated ${formatDateTime(it)}" } ?: "Not refreshed yet",
                fontSize = 10.sp,
            )
            Text(
                text = snapshot.validUntil?.let { "Cache until ${formatDateTime(it)}" } ?: "No cached rates",
                fontSize = 10.sp,
            )
            Text(
                text = "Data: Octopus Energy API",
                fontSize = 10.sp,
            )
            PillAction(
                text = "Change region",
                onClick = onChangeRegion,
                filled = false,
            )
        }
    }
}

@Composable
private fun PriceSparkline(
    prices: List<PriceWindow>,
    bestWindow: BestWindow?,
    now: Instant,
) {
    val visiblePrices = prices
        .filter { it.validTo > now && it.validFrom < now.plus(Duration.ofHours(24)) }
    if (visiblePrices.size < 2) return
    val label = sparklineLabel(visiblePrices, now)

    val minPrice = visiblePrices.minOf { it.pricePencePerKwh }
    val maxPrice = visiblePrices.maxOf { it.pricePencePerKwh }
    val priceRange = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val panelColor = MaterialTheme.colorScheme.surfaceContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(panelColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("price_sparkline")
                .semantics {
                    contentDescription = "$label price graph"
                },
        ) {
            val top = 3f
            val bottom = size.height - 3f
            val plotHeight = bottom - top
            fun xFor(index: Int): Float =
                if (visiblePrices.size == 1) {
                    size.width / 2f
                } else {
                    size.width * index / (visiblePrices.lastIndex).toFloat()
                }
            fun yFor(price: Double): Float =
                (top + ((maxPrice - price) / priceRange).toFloat() * plotHeight)

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
                    cornerRadius = CornerRadius(6f, 6f),
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
                style = Stroke(width = 3f),
            )
            drawCircle(
                color = markerColor,
                radius = 3.5f,
                center = Offset(xFor(0), yFor(visiblePrices.first().pricePencePerKwh)),
            )
        }
    }
}

internal fun sparklineLabel(prices: List<PriceWindow>, now: Instant): String {
    val availableMinutes = Duration.between(now, prices.last().validTo)
        .toMinutes()
        .coerceAtLeast(30)
    val availableHours = ((availableMinutes + 59) / 60).coerceAtMost(24)
    return if (availableHours >= 24) "Next 24h" else "Next ${availableHours}h"
}

@Composable
private fun PriceHeader(
    snapshot: PriceSnapshot,
    now: Instant,
) {
    val bestWindow = snapshot.bestWindow

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = snapshot.primaryPriceValueForApp(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = snapshot.primaryPriceCaption(),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = bestWindow?.let { formatWindowRange(it.start, it.end, now) } ?: "Best window --",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = bestWindow?.averagePricePencePerKwh?.let { "${it.formatPrice()}p/kWh avg" } ?: "No complete window",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
            if (bestWindow != null) {
                Text(
                    text = bestWindow.compactTimingText(now),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        RoundAction(text = "-", contentDescription = "Decrease $label", enabled = minusEnabled, onClick = onMinus)
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
        RoundAction(text = "+", contentDescription = "Increase $label", enabled = plusEnabled, onClick = onPlus)
    }
}

@Composable
private fun PillAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainer
        filled -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        filled -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(CircleShape)
            .background(background)
            .tapTarget(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoundAction(
    text: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val foreground = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .semantics { this.contentDescription = contentDescription }
            .tapTarget(enabled = enabled, onClick = onClick),
    ) {
        Text(text = text, color = foreground, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

private fun Modifier.tapTarget(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier =
    this
        .pointerInput(enabled, onClick) {
            detectTapGestures(
                onTap = {
                    if (enabled) onClick()
                },
            )
        }
        .semantics {
            if (enabled) {
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
        }

@Composable
private fun PriceRow(price: PriceWindow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(formatTime(price.validFrom), fontSize = 12.sp)
        Text("${price.pricePencePerKwh.formatPrice()}p", fontSize = 12.sp)
    }
}

fun Double.formatPrice(): String =
    String.format(Locale.UK, "%.1f", this)

fun formatTime(instant: Instant): String =
    timeFormatter.format(instant.atZone(ZoneId.systemDefault()))

fun formatDateTime(instant: Instant): String =
    dateTimeFormatter.format(instant.atZone(ZoneId.systemDefault()))

fun formatWindowRange(
    start: Instant,
    end: Instant,
    reference: Instant = Instant.now(),
): String {
    val zone = ZoneId.systemDefault()
    val startDate = start.atZone(zone).toLocalDate()
    val referenceDate = reference.atZone(zone).toLocalDate()
    val prefix = when (startDate) {
        referenceDate -> ""
        referenceDate.plusDays(1) -> "Tomorrow "
        else -> "${dateFormatter.format(start.atZone(zone))} "
    }
    return "$prefix${formatTime(start)}-${formatTime(end)}"
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.UK)
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

@Preview
@Composable
private fun PriceScreenPreview() {
    MaterialTheme {
        WearPriceScreen(
            snapshot = PriceSnapshot(
                currentPrice = PriceWindow(Instant.now(), Instant.now().plusSeconds(1800), 8.2),
                bestWindow = BestWindow(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), -1.4),
                fetchedAt = Instant.now(),
                validUntil = Instant.now().plusSeconds(86400),
                status = SnapshotStatus.Loaded,
                upcoming = emptyList(),
            ),
            settings = AgileSettings("_C", "E-1R-AGILE-24-10-01-C", 60, 480, emptyList(), Instant.now(), null),
            now = Instant.now(),
            busy = false,
            message = null,
            onRefresh = {},
            onLoadDurationChanged = {},
            onSearchHorizonChanged = {},
            onChangeRegion = {},
        )
    }
}
