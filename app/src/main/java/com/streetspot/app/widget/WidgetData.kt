package com.streetspot.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.repository.HomeSnapshot
import com.streetspot.app.domain.progression.LevelCalculator
import com.streetspot.app.domain.progression.Rank
import com.streetspot.app.domain.statistics.StatisticsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class WidgetSnapshot(
    val home: HomeSnapshot,
    val statistics: StatisticsSnapshot,
)

internal suspend fun loadWidgetSnapshot(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
    val application = context.applicationContext as StreetSpotApplication
    combine(
        application.progressRepository.observeHome(),
        application.progressRepository.observeStatistics(),
        ::WidgetSnapshot,
    ).first()
}

internal suspend fun loadDynamicColorsPreference(context: Context): Boolean = withContext(Dispatchers.IO) {
    val application = context.applicationContext as StreetSpotApplication
    application.userPreferencesRepository.dynamicColorsEnabled.first()
}

internal fun rankResource(rank: Rank): Int = when (rank) {
    Rank.NEWCOMER -> R.string.home_rank_newcomer
    Rank.STREET_OBSERVER -> R.string.home_rank_street_observer
    Rank.CAR_ENTHUSIAST -> R.string.home_rank_car_enthusiast
    Rank.SPOTTER -> R.string.home_rank_spotter
    Rank.STREET_HUNTER -> R.string.home_rank_street_hunter
    Rank.CAR_COLLECTOR -> R.string.home_rank_car_collector
    Rank.RARE_HUNTER -> R.string.home_rank_rare_hunter
    Rank.STREET_LEGEND -> R.string.home_rank_street_legend
}

internal object StreetSpotWidgets {
    suspend fun updateAll(context: Context) {
        LevelWidget().updateAll(context)
        CollectionWidget().updateAll(context)
    }
}

class LevelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LevelWidget()
}

class CollectionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollectionWidget()
}
