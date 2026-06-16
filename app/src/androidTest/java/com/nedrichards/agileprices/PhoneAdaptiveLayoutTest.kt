package com.nedrichards.agileprices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneAdaptiveLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun phoneSetupShowsRegionsAndReportsSelection() {
        var selected: ElectricityRegion? = null

        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = AdaptiveWidthClass.Compact,
                snapshot = PriceSnapshot(
                    currentPrice = null,
                    bestWindow = null,
                    fetchedAt = null,
                    validUntil = null,
                    status = SnapshotStatus.NoSetup,
                    message = "Choose a region to load Agile prices",
                ),
                settings = noSetupSettings(),
                now = now,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = { selected = it },
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = {},
            )
        }

        compose.onNodeWithTag("phone_setup_list").assertIsDisplayed()
        compose.onNodeWithText("Choose region").assertIsDisplayed()
        compose.onNodeWithTag("phone_setup_list").performScrollToNode(hasText("London"))
        compose.onNodeWithText("London").performClick()

        compose.runOnIdle {
            assertEquals("_C", selected?.code)
        }
    }

    @Test
    fun compactPhoneLoadedStateShowsTimelineGraphAndControls() {
        var settings by mutableStateOf(settings())
        var refreshed = false
        var changingRegion = false

        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = AdaptiveWidthClass.Compact,
                snapshot = loadedSnapshot(settings),
                settings = settings,
                now = now,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = {},
                onRefresh = { refreshed = true },
                onLoadDurationChanged = { settings = settings.copy(loadDurationMinutes = it) },
                onSearchHorizonChanged = { settings = settings.copy(searchHorizonMinutes = it) },
                onChangeRegion = { changingRegion = true },
                onDismissRegionPicker = {},
            )
        }

        compose.onNodeWithTag("phone_compact_price_list").assertIsDisplayed()
        compose.onNodeWithTag("phone_current_price").assertIsDisplayed()
        compose.onNodeWithText("8.2p/kWh").assertIsDisplayed()
        compose.onAllNodesWithText("p/kWh now").assertCountEquals(0)
        compose.onAllNodesWithText("Current period 12:00-12:30").assertCountEquals(0)
        compose.onAllNodesWithText("Cheapest 1h window")[0].assertIsDisplayed()
        compose.onNodeWithText("13:00-14:00").assertIsDisplayed()
        compose.onNodeWithText("Starts in 1h - Ends in 2h").assertIsDisplayed()
        compose.onAllNodesWithText("Duration 1h").assertCountEquals(0)
        compose.onAllNodesWithText("Current price period").assertCountEquals(0)
        compose.onAllNodesWithTag("phone_time_relationship").assertCountEquals(0)
        compose.onAllNodesWithText("Now").assertCountEquals(0)

        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasText("Planning"))
        compose.onNodeWithContentDescription("Adjust phone Run time")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        compose.onNodeWithText("1h 30m").assertIsDisplayed()
        compose.onNodeWithContentDescription("Adjust phone Search horizon")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(540f) }
        compose.onNodeWithText("9h").assertIsDisplayed()

        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasTestTag("phone_price_sparkline"))
        compose.onNodeWithText("Upcoming prices").assertIsDisplayed()
        compose.onNodeWithText("12:00-12:30").assertIsDisplayed()
        compose.onNodeWithText("8.2p/kWh").assertIsDisplayed()
        compose.onNodeWithTag("phone_cheapest_price").assertIsDisplayed()
        compose.onNodeWithText("Price swing 13.0p/kWh across the visible range").assertIsDisplayed()
        compose.onNodeWithTag("phone_price_sparkline").assertIsDisplayed()
        compose.onAllNodesWithText("Next slots").assertCountEquals(0)

        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasTestTag("phone_refresh_action"))
        compose.onNodeWithTag("phone_refresh_action").performClick()
        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasText("Change region"))
        compose.onNodeWithText("Change region").performClick()
        compose.runOnIdle {
            assertTrue(refreshed)
            assertTrue(changingRegion)
        }
    }

    @Test
    fun phoneErrorStateKeepsSetupActionsReachable() {
        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = AdaptiveWidthClass.Compact,
                snapshot = PriceSnapshot(
                    currentPrice = null,
                    bestWindow = null,
                    fetchedAt = null,
                    validUntil = null,
                    status = SnapshotStatus.Error,
                    message = "Network unavailable.",
                ),
                settings = settings(),
                now = now,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = {},
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = {},
            )
        }

        compose.onAllNodesWithText("--")[0].assertIsDisplayed()
        compose.onAllNodesWithText("No complete window")[0].assertIsDisplayed()
        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasText("Network unavailable."))
        compose.onAllNodesWithText("Network unavailable.")[0].assertIsDisplayed()
    }

    @Test
    fun phoneStaleStateKeepsSetupActionsReachable() {
        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = AdaptiveWidthClass.Compact,
                snapshot = PriceSnapshot(
                    currentPrice = null,
                    bestWindow = null,
                    fetchedAt = Instant.parse("2026-01-05T09:00:00Z"),
                    validUntil = Instant.parse("2026-01-05T11:00:00Z"),
                    status = SnapshotStatus.Stale,
                ),
                settings = settings(),
                now = now,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = {},
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = {},
            )
        }

        compose.onAllNodesWithText("--")[0].assertIsDisplayed()
        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasText("Cache ended", substring = true))
        compose.onAllNodesWithText("Cache ended", substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun mediumAndExpandedPhoneWidthsUseSupportingPane() {
        var widthClass by mutableStateOf(AdaptiveWidthClass.Medium)

        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = widthClass,
                snapshot = loadedSnapshot(settings()),
                settings = settings(),
                now = now,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = {},
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = {},
            )
        }

        compose.onNodeWithTag("phone_two_pane").assertIsDisplayed()
        compose.onNodeWithTag("phone_supporting_pane").assertIsDisplayed()

        compose.runOnIdle {
            widthClass = AdaptiveWidthClass.Expanded
        }
        compose.onNodeWithTag("phone_two_pane").assertIsDisplayed()
        compose.onNodeWithTag("phone_supporting_pane").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun phoneGraphSupportsHorizontalKeySelection() {
        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = AdaptiveWidthClass.Compact,
                snapshot = loadedSnapshot(settings()),
                settings = settings(),
                now = now,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = {},
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = {},
            )
        }

        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasTestTag("phone_price_sparkline"))
        compose.onNodeWithText("12:00-12:30").assertIsDisplayed()
        compose.onNodeWithText("8.2p/kWh").assertIsDisplayed()

        compose.onNodeWithTag("phone_price_sparkline")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("phone_price_sparkline")
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("12:30-13:00").assertIsDisplayed()
        compose.onNodeWithText("11.0p/kWh").assertIsDisplayed()

        compose.onNodeWithTag("phone_price_sparkline")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("12:00-12:30").assertIsDisplayed()
        compose.onNodeWithText("8.2p/kWh").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun phoneGraphSelectionSurvivesNowTick() {
        var clockNow by mutableStateOf(now)

        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = AdaptiveWidthClass.Compact,
                snapshot = loadedSnapshot(settings()),
                settings = settings(),
                now = clockNow,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = {},
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = {},
            )
        }

        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasTestTag("phone_price_sparkline"))
        compose.onNodeWithTag("phone_price_sparkline")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("phone_price_sparkline")
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("12:30-13:00").assertIsDisplayed()

        compose.runOnIdle {
            clockNow = now.plusSeconds(60)
        }

        compose.onNodeWithText("12:30-13:00").assertIsDisplayed()
        compose.onAllNodesWithText("12:00-12:30").assertCountEquals(0)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun phoneGraphSelectionTracksTimeWhenVisibleSlotsShift() {
        var clockNow by mutableStateOf(now)

        compose.setContent {
            AgilePricesContent(
                surface = AgileSurface.Phone,
                adaptiveWidthClass = AdaptiveWidthClass.Compact,
                snapshot = loadedSnapshot(settings()),
                settings = settings(),
                now = clockNow,
                busy = false,
                message = null,
                choosingRegion = false,
                onSelectRegion = {},
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = {},
            )
        }

        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasTestTag("phone_price_sparkline"))
        compose.onNodeWithTag("phone_price_sparkline")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("phone_price_sparkline")
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionRight)
            }
        compose.onNodeWithText("13:00-13:30").assertIsDisplayed()

        compose.runOnIdle {
            clockNow = now.plusSeconds(30 * 60)
        }

        compose.onNodeWithText("13:00-13:30").assertIsDisplayed()
        compose.onAllNodesWithText("13:30-14:00").assertCountEquals(0)
    }

    @Test
    fun verticalSwipeStartingOnPhoneGraphScrollsThePage() {
        compose.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 420.dp)) {
                AgilePricesContent(
                    surface = AgileSurface.Phone,
                    adaptiveWidthClass = AdaptiveWidthClass.Compact,
                    snapshot = loadedSnapshot(settings()),
                    settings = settings(),
                    now = now,
                    busy = false,
                    message = null,
                    choosingRegion = false,
                    onSelectRegion = {},
                    onRefresh = {},
                    onLoadDurationChanged = {},
                    onSearchHorizonChanged = {},
                    onChangeRegion = {},
                    onDismissRegionPicker = {},
                )
            }
        }

        compose.onNodeWithTag("phone_compact_price_list").performScrollToNode(hasTestTag("phone_price_sparkline"))
        compose.onNodeWithTag("phone_price_sparkline").assertIsDisplayed()
        compose.onNodeWithTag("phone_price_sparkline").performTouchInput { swipeUp() }

        compose.onNodeWithTag("phone_refresh_action").assertIsDisplayed()
    }

    private fun loadedSnapshot(settings: AgileSettings): PriceSnapshot =
        PriceSnapshot(
            currentPrice = PriceWindow(
                validFrom = now,
                validTo = now.plusSeconds(30 * 60),
                pricePencePerKwh = 8.2,
            ),
            bestWindow = BestWindow(
                start = Instant.parse("2026-01-05T13:00:00Z"),
                end = Instant.parse("2026-01-05T14:00:00Z"),
                averagePricePencePerKwh = 4.0,
            ),
            fetchedAt = settings.fetchedAt,
            validUntil = Instant.parse("2026-01-06T00:00:00Z"),
            status = SnapshotStatus.Loaded,
            upcoming = samplePrices().take(8),
            sparklinePrices = samplePrices(),
        )

    private fun settings(): AgileSettings =
        AgileSettings(
            selectedRegionCode = "_C",
            selectedTariffCode = "E-1R-AGILE-26-05-01-C",
            loadDurationMinutes = 60,
            searchHorizonMinutes = 480,
            cachedPrices = emptyList(),
            fetchedAt = now.minusSeconds(120),
            lastRefreshMessage = null,
        )

    private fun noSetupSettings(): AgileSettings =
        AgileSettings(
            selectedRegionCode = null,
            selectedTariffCode = null,
            loadDurationMinutes = 60,
            searchHorizonMinutes = 480,
            cachedPrices = emptyList(),
            fetchedAt = null,
            lastRefreshMessage = null,
        )

    private companion object {
        val now: Instant = Instant.parse("2026-01-05T12:00:00Z")

        fun samplePrices(): List<PriceWindow> =
            List(48) { index ->
                val validFrom = now.plusSeconds(index * 30L * 60L)
                PriceWindow(
                    validFrom = validFrom,
                    validTo = validFrom.plusSeconds(30 * 60),
                    pricePencePerKwh = when (index) {
                        0 -> 8.2
                        2, 3 -> 4.0
                        else -> 10.0 + (index % 8)
                    },
                )
            }
    }
}
