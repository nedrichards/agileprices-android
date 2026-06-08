package com.nedrichards.agileprices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearProfileLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun smallRoundLoadedProfileSupportsLargerFont() {
        compose.setContent {
            WearProfile(width = 192.dp, height = 192.dp, fontScale = 1.3f) {
                PriceScreen(
                    snapshot = loadedSnapshot(),
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

        compose.onNodeWithText("8.2").assertIsDisplayed()
        compose.onNodeWithText("p/kWh now").assertIsDisplayed()
        compose.onNodeWithText("13:00-14:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("4.0p/kWh avg").assertIsDisplayed()
        compose.onNodeWithTag("price_list").performScrollToNode(hasText("Setup"))
        compose.onNodeWithText("Setup").assertIsDisplayed()
    }

    @Test
    fun largeRoundLoadedProfileSupportsLargerFont() {
        compose.setContent {
            WearProfile(width = 240.dp, height = 240.dp, fontScale = 1.3f) {
                PriceScreen(
                    snapshot = loadedSnapshot(),
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

        compose.onNodeWithText("8.2").assertIsDisplayed()
        compose.onNodeWithText("p/kWh now").assertIsDisplayed()
        compose.onNodeWithTag("price_list").performScrollToNode(hasText("13:00-14:00", substring = true))
        compose.onNodeWithText("13:00-14:00", substring = true).assertIsDisplayed()
    }

    @Test
    fun smallRoundSetupProfileSupportsLargerFont() {
        var selected: ElectricityRegion? = null

        compose.setContent {
            WearProfile(width = 192.dp, height = 192.dp, fontScale = 1.3f) {
                RegionSetupScreen(
                    busy = false,
                    message = "Choose a region to load Agile prices",
                    onSelectRegion = { selected = it },
                )
            }
        }

        compose.onNodeWithText("Choose region").assertIsDisplayed()
        compose.onNodeWithText("London")
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithText("London").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("_C", selected?.code)
        }
    }

    @Composable
    private fun WearProfile(
        width: Dp,
        height: Dp,
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .requiredSize(width = width, height = height)
                        .clipToBounds(),
                ) {
                    content()
                }
            }
        }
    }

    private fun loadedSnapshot(): PriceSnapshot =
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
            fetchedAt = now,
            validUntil = Instant.parse("2026-01-06T00:00:00Z"),
            status = SnapshotStatus.Loaded,
            upcoming = sampleUpcomingPrices(),
        )

    private fun settings(): AgileSettings =
        AgileSettings(
            selectedRegionCode = "_C",
            selectedTariffCode = "E-1R-AGILE-26-05-01-C",
            loadDurationMinutes = 60,
            searchHorizonMinutes = 480,
            cachedPrices = emptyList(),
            fetchedAt = now,
            lastRefreshMessage = null,
        )

    private companion object {
        val now: Instant = Instant.parse("2026-01-05T12:00:00Z")

        fun sampleUpcomingPrices(): List<PriceWindow> =
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
