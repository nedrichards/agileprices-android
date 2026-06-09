package com.nedrichards.agileprices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import androidx.wear.compose.material3.MaterialTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun setupScreenShowsRegionsAndReportsSelection() {
        var selected: ElectricityRegion? = null

        compose.setContent {
            MaterialTheme {
                RegionSetupScreen(
                    busy = false,
                    message = "Network unavailable.",
                    onSelectRegion = { selected = it },
                )
            }
        }

        compose.onNodeWithText("Choose region").assertIsDisplayed()
        compose.onNodeWithText("Network unavailable.").assertIsDisplayed()
        compose.onNodeWithTag("setup_scroll_indicator").fetchSemanticsNode()

        compose.onNodeWithText("London")
            .performScrollTo()
            .performClick()

        compose.runOnIdle {
            assertEquals("_C", selected?.code)
        }
    }

    @Test
    fun loadedStateShowsCurrentPriceBestWindowAndRecomputesControls() {
        val now = Instant.parse("2026-01-05T12:00:00Z")
        var settings by mutableStateOf(
            AgileSettings(
                selectedRegionCode = "_C",
                selectedTariffCode = "E-1R-AGILE-26-05-01-C",
                loadDurationMinutes = 60,
                searchHorizonMinutes = 480,
                cachedPrices = emptyList(),
                fetchedAt = now,
                lastRefreshMessage = null,
            ),
        )
        var refreshed = false

        compose.setContent {
            MaterialTheme {
                PriceScreen(
                    snapshot = PriceSnapshot(
                        currentPrice = PriceWindow(now, now.plusSeconds(30 * 60), -1.2),
                        bestWindow = BestWindow(
                            start = Instant.parse("2026-01-05T13:00:00Z"),
                            end = Instant.parse("2026-01-05T14:00:00Z"),
                            averagePricePencePerKwh = -2.0,
                        ),
                        fetchedAt = now,
                        validUntil = Instant.parse("2026-01-06T00:00:00Z"),
                        status = SnapshotStatus.Loaded,
                        upcoming = sampleUpcomingPrices(now),
                        sparklinePrices = sampleUpcomingPrices(now),
                    ),
                    settings = settings,
                    now = now,
                    busy = false,
                    message = null,
                    onRefresh = { refreshed = true },
                    onLoadDurationChanged = { settings = settings.copy(loadDurationMinutes = it) },
                    onSearchHorizonChanged = { settings = settings.copy(searchHorizonMinutes = it) },
                    onChangeRegion = {},
                )
            }
        }

        compose.onNodeWithText("-1.2").assertIsDisplayed()
        compose.onNodeWithText("p/kWh now").assertIsDisplayed()
        compose.onNodeWithTag("price_scroll_indicator").fetchSemanticsNode()
        compose.onNodeWithTag("price_list").performScrollToNode(hasText("13:00-14:00", substring = true))
        compose.onNodeWithText("13:00-14:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("-2.0p/kWh avg").assertIsDisplayed()
        compose.onNodeWithText("In 1h / Ends 2h").assertIsDisplayed()
        compose.onNodeWithTag("price_list").performScrollToNode(hasTestTag("price_sparkline"))
        compose.onNodeWithText("Next 24h").assertIsDisplayed()
        compose.onNodeWithTag("price_sparkline").assertIsDisplayed()
        compose.onNodeWithTag("price_list").performScrollToNode(hasText("8h"))
        compose.onNodeWithText("8h").assertIsDisplayed()

        compose.onNodeWithTag("price_list").performScrollToNode(hasText("Run time"))
        compose.onNodeWithContentDescription("Increase Run time").performClick()
        compose.onNodeWithText("1h 30m").assertIsDisplayed()

        compose.onNodeWithTag("price_list").performScrollToNode(hasText("Search"))
        compose.onNodeWithContentDescription("Increase Search").performClick()
        compose.onNodeWithText("9h").assertIsDisplayed()

        compose.onNodeWithTag("price_list").performScrollToNode(hasTestTag("refresh_action"))
        compose.onNodeWithTag("refresh_action").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(refreshed)
        }
    }

    @Test
    fun errorStateShowsUnavailablePriceAndMessage() {
        val now = Instant.parse("2026-01-05T12:00:00Z")

        compose.setContent {
            MaterialTheme {
                PriceScreen(
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
                    onRefresh = {},
                    onLoadDurationChanged = {},
                    onSearchHorizonChanged = {},
                    onChangeRegion = {},
                )
            }
        }

        compose.onAllNodesWithText("--")[0].assertIsDisplayed()
        compose.onNodeWithText("Price data unavailable").assertIsDisplayed()
        compose.onNodeWithTag("price_list").performScrollToNode(hasText("No complete window"))
        compose.onNodeWithText("No complete window").assertIsDisplayed()
        compose.onNodeWithTag("price_list").performScrollToNode(hasText("Network unavailable."))
        compose.onNodeWithText("Network unavailable.").assertIsDisplayed()
    }

    @Test
    fun staleStateShowsExpiredCacheStatus() {
        val now = Instant.parse("2026-01-05T12:00:00Z")

        compose.setContent {
            MaterialTheme {
                PriceScreen(
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
                    onRefresh = {},
                    onLoadDurationChanged = {},
                    onSearchHorizonChanged = {},
                    onChangeRegion = {},
                )
            }
        }

        compose.onAllNodesWithText("--")[0].assertIsDisplayed()
        compose.onNodeWithText("Cached data expired").assertIsDisplayed()
        compose.onNodeWithTag("price_list").performScrollToNode(hasText("Cache ended", substring = true))
        compose.onNodeWithText("Cache ended", substring = true).assertIsDisplayed()
    }

    @Test
    fun backFromRegionPickerReturnsToConfiguredPriceScreen() {
        val now = Instant.parse("2026-01-05T12:00:00Z")
        var dismissed = false

        compose.setContent {
            AgilePricesContent(
                snapshot = PriceSnapshot(
                    currentPrice = PriceWindow(now, now.plusSeconds(30 * 60), 8.2),
                    bestWindow = BestWindow(
                        start = Instant.parse("2026-01-05T13:00:00Z"),
                        end = Instant.parse("2026-01-05T14:00:00Z"),
                        averagePricePencePerKwh = 4.0,
                    ),
                    fetchedAt = now,
                    validUntil = Instant.parse("2026-01-06T00:00:00Z"),
                    status = SnapshotStatus.Loaded,
                ),
                settings = settings(),
                now = now,
                busy = false,
                message = null,
                choosingRegion = true,
                onSelectRegion = {},
                onRefresh = {},
                onLoadDurationChanged = {},
                onSearchHorizonChanged = {},
                onChangeRegion = {},
                onDismissRegionPicker = { dismissed = true },
            )
        }

        compose.onNodeWithText("Choose region").assertIsDisplayed()
        pressBack()

        compose.runOnIdle {
            assertTrue(dismissed)
        }
    }

    private fun settings(): AgileSettings =
        AgileSettings(
            selectedRegionCode = "_C",
            selectedTariffCode = "E-1R-AGILE-26-05-01-C",
            loadDurationMinutes = 60,
            searchHorizonMinutes = 480,
            cachedPrices = emptyList(),
            fetchedAt = Instant.parse("2026-01-05T09:00:00Z"),
            lastRefreshMessage = null,
        )

    private fun sampleUpcomingPrices(start: Instant): List<PriceWindow> =
        List(48) { index ->
            val validFrom = start.plusSeconds(index * 30L * 60L)
            PriceWindow(
                validFrom = validFrom,
                validTo = validFrom.plusSeconds(30 * 60),
                pricePencePerKwh = when (index) {
                    0 -> -1.2
                    2, 3 -> -2.0
                    else -> 6.0 + (index % 8)
                },
            )
        }
}
