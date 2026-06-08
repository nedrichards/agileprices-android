package com.nedrichards.agileprices

import android.content.ComponentName
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.DISPLAY_MEDIUM
import androidx.wear.protolayout.material3.Typography.LABEL_MEDIUM
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AgilePriceTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            serviceScope.launch {
                runCatching { buildTile(requestParams) }
                    .onSuccess(completer::set)
                    .onFailure(completer::setException)
            }
            "AgilePriceTileService.onTileRequest"
        }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(Resources.Builder().setVersion(resourcesVersion).build())
            "AgilePriceTileService.onTileResourcesRequest"
        }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun buildTile(requestParams: TileRequest): Tile {
        val snapshot = createRepository(applicationContext).snapshots.first()
        val presentation = snapshot.tilePresentation()

        return Tile.Builder()
            .setResourcesVersion(resourcesVersion)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    materialScope(
                        context = this,
                        deviceConfiguration = requestParams.deviceConfiguration,
                    ) {
                        primaryLayout(
                            titleSlot = {
                                text(
                                    text = "Agile".layoutString,
                                    typography = LABEL_MEDIUM,
                                )
                            },
                            mainSlot = {
                                column(
                                    text(
                                        text = presentation.currentText.layoutString,
                                        typography = DISPLAY_MEDIUM,
                                    ),
                                    text(
                                        text = presentation.caption.layoutString,
                                        typography = BODY_MEDIUM,
                                    ),
                                    text(
                                        text = presentation.detail.layoutString,
                                        typography = LABEL_MEDIUM,
                                    ),
                                )
                            },
                            bottomSlot = {
                                textEdgeButton(
                                    labelContent = { text("Open".layoutString) },
                                    onClick = clickable(
                                        action = launchAction(
                                            ComponentName(packageName, MainActivity::class.java.name),
                                        ),
                                    ),
                                )
                            },
                        )
                    },
                ),
            )
            .setFreshnessIntervalMillis(tileFreshnessIntervalMillis)
            .build()
    }
    private companion object {
        const val resourcesVersion = "1"
        const val tileFreshnessIntervalMillis = 30 * 60 * 1000L
    }
}
