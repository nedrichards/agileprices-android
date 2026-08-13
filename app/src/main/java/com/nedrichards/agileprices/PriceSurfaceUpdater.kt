package com.nedrichards.agileprices

import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

class PhonePriceSurfaceUpdater(
    private val context: Context,
) : PriceSurfaceUpdater {
    override fun requestUpdates() {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                AgilePriceWidget().updateAll(appContext)
            }
            NegativePriceNotifier.update(appContext)
        }
    }
}
