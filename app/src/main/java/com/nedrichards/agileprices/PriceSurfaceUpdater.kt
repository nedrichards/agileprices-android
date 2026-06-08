package com.nedrichards.agileprices

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

fun interface PriceSurfaceUpdater {
    fun requestUpdates()
}

class WearPriceSurfaceUpdater(
    private val context: Context,
) : PriceSurfaceUpdater {
    override fun requestUpdates() {
        val appContext = context.applicationContext

        runCatching {
            TileService.getUpdater(appContext)
                .requestUpdate(AgilePriceTileService::class.java)
        }

        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                appContext,
                ComponentName(appContext, AgilePriceComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}

