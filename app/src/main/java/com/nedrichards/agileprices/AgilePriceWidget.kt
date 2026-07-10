package com.nedrichards.agileprices

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AgilePriceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgilePriceWidget()
}

class AgilePriceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val snapshot = withContext(Dispatchers.IO) {
            AgileRepository(SettingsStore(context.applicationContext)).snapshots.first()
        }
        provideContent {
            AgilePriceWidgetContent(widgetPresentation(snapshot))
        }
    }
}

internal data class WidgetPresentation(
    val value: String,
    val caption: String,
    val detail: String,
)

internal fun widgetPresentation(snapshot: PriceSnapshot): WidgetPresentation =
    WidgetPresentation(
        value = snapshot.primaryPriceText(),
        caption = snapshot.primaryPriceCaption(),
        detail = if (snapshot.status == SnapshotStatus.Loaded) {
            snapshot.bestWindow?.let { window ->
                "${formatTime(window.start)}-${formatTime(window.end)} ${window.averagePricePencePerKwh.formatPrice()}p"
            } ?: "No complete window"
        } else {
            snapshot.secondaryStatusText()
        },
    )

@Composable
private fun AgilePriceWidgetContent(presentation: WidgetPresentation) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF173E5B)))
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Agile Prices",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = presentation.value,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = presentation.caption,
            style = TextStyle(color = ColorProvider(Color(0xFFD5E8F7))),
        )
        Text(
            text = presentation.detail,
            style = TextStyle(color = ColorProvider(Color(0xFFD5E8F7))),
        )
    }
}
