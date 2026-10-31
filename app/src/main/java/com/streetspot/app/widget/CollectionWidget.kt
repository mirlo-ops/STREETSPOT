package com.streetspot.app.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import com.streetspot.app.MainActivity
import com.streetspot.app.R

@OptIn(ExperimentalGlanceApi::class)
internal class CollectionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadWidgetSnapshot(context)
        val dynamicColorsEnabled = loadDynamicColorsPreference(context)
        val target = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_INITIAL_ROUTE, MainActivity.ROUTE_SPOTDEX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        provideContent {
            val colors = if (dynamicColorsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceTheme.colors
            } else {
                StreetSpotWidgetTheme.colors
            }
            GlanceTheme(colors = colors) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity(target))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.Start,
                    ) {
                        WidgetMetric(
                            value = snapshot.home.totalSpots.toString(),
                            label = context.getString(R.string.widget_collection_spots),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        WidgetMetric(
                            value = snapshot.home.brandsDiscovered.toString(),
                            label = context.getString(R.string.widget_collection_brands),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                    Text(
                        text = context.getString(R.string.widget_collection_month) + ": " +
                            snapshot.statistics.spotsThisMonth,
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.padding(top = 8.dp),
                    )
                    if (snapshot.home.totalSpots == 0) {
                        Text(
                            text = context.getString(R.string.widget_collection_empty_hint),
                            style = TextStyle(
                                color = GlanceTheme.colors.secondary,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetMetric(
    value: String,
    label: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 11.sp,
            ),
        )
    }
}
