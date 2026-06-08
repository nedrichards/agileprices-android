package com.nedrichards.agileprices

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.flow.first

class AgilePriceComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        return shortTextData(createRepository(applicationContext).snapshots.first())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return shortTextData(
            PriceSnapshot(
                currentPrice = PriceWindow(
                    validFrom = java.time.Instant.now(),
                    validTo = java.time.Instant.now().plusSeconds(1800),
                    pricePencePerKwh = 7.2,
                ),
                bestWindow = null,
                fetchedAt = java.time.Instant.now(),
                validUntil = java.time.Instant.now().plusSeconds(3600),
                status = SnapshotStatus.Loaded,
            ),
        )
    }

    private fun shortTextData(snapshot: PriceSnapshot): ShortTextComplicationData {
        val presentation = snapshot.complicationPresentation()
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(presentation.text).build(),
            contentDescription = PlainComplicationText.Builder(presentation.contentDescription).build(),
        )
            .setTitle(PlainComplicationText.Builder("Agile").build())
            .setTapAction(agileLaunchPendingIntent(this))
            .build()
    }
}
