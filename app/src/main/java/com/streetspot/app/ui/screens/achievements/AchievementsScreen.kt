package com.streetspot.app.ui.screens.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.domain.progression.AchievementId
import com.streetspot.app.domain.progression.AchievementProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AchievementsScreen() {
    val application = LocalContext.current.applicationContext as StreetSpotApplication
    val factory = remember(application) {
        viewModelFactory {
            initializer { AchievementsViewModel(application.progressRepository) }
        }
    }
    val viewModel: AchievementsViewModel = viewModel(factory = factory)
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()

    if (achievements.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.achievement_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(achievements, key = AchievementProgress::id) { achievement ->
                AchievementCard(achievement)
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(achievement.title(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (achievement.isUnlocked) R.string.achievement_status_unlocked
                        else R.string.achievement_status_locked,
                    ),
                    color = if (achievement.isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(achievement.description(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { achievement.completionFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                stringResource(R.string.home_achievement_progress, achievement.progress, achievement.target),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            achievement.unlockedAt?.let { timestamp ->
                val date = remember(timestamp) {
                    Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                }
                Text(
                    stringResource(R.string.achievement_unlocked_date, date),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun AchievementProgress.title(): String = stringResource(
    when (id) {
        AchievementId.FIRST_SPOT -> R.string.achievement_first_spot
        AchievementId.JDM_HUNTER -> R.string.achievement_jdm_hunter
        AchievementId.NIGHT_RIDER -> R.string.achievement_night_rider
        AchievementId.RARE_COLLECTOR -> R.string.achievement_rare_collector
        AchievementId.BRAND_HUNTER -> R.string.achievement_brand_hunter
        AchievementId.SPOTS_100 -> R.string.achievement_spots_100
    },
)

@Composable
private fun AchievementProgress.description(): String = stringResource(
    when (id) {
        AchievementId.FIRST_SPOT -> R.string.achievement_first_spot_description
        AchievementId.JDM_HUNTER -> R.string.achievement_jdm_hunter_description
        AchievementId.NIGHT_RIDER -> R.string.achievement_night_rider_description
        AchievementId.RARE_COLLECTOR -> R.string.achievement_rare_collector_description
        AchievementId.BRAND_HUNTER -> R.string.achievement_brand_hunter_description
        AchievementId.SPOTS_100 -> R.string.achievement_spots_100_description
    },
)
