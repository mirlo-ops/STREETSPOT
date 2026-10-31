package com.streetspot.app.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import com.streetspot.app.MainActivity
import com.streetspot.app.R
import com.streetspot.app.domain.progression.LevelCalculator

@OptIn(ExperimentalGlanceApi::class)
internal class LevelWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadWidgetSnapshot(context)
        val dynamicColorsEnabled = loadDynamicColorsPreference(context)
        val level = LevelCalculator.calculate(snapshot.home.totalXp)
        val title = context.getString(R.string.home_level_title, level.level)
        val rank = context.getString(rankResource(level.rank))
        val progress = if (level.isMaxLevel) {
            context.getString(R.string.widget_level_xp_max, level.totalXp)
        } else {
            context.getString(
                R.string.widget_level_xp_progress,
                level.xpIntoLevel,
                level.xpIntoLevel + level.xpToNextLevel,
            )
        }
        val emptyHint = if (snapshot.home.totalSpots == 0) {
            context.getString(R.string.widget_level_empty_hint)
        } else {
            null
        }
        val target = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_INITIAL_ROUTE, MainActivity.ROUTE_HOME)
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
                    verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
                ) {
                    Text(
                        text = context.getString(R.string.widget_level_label),
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = title,
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = rank,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = level.progressFraction,
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.primaryContainer,
                    )
                    Text(
                        text = progress,
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 11.sp,
                        ),
                    )
                    if (emptyHint != null) {
                        Text(
                            text = emptyHint,
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
