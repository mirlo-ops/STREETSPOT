package com.streetspot.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.model.SpotWithModel
import com.streetspot.app.domain.progression.AchievementId
import com.streetspot.app.domain.progression.AchievementProgress
import com.streetspot.app.domain.progression.LevelCalculator
import com.streetspot.app.domain.progression.Rank
import com.streetspot.app.ui.components.StoredPhotoImage

@Composable
fun HomeScreen(
    onAddSpot: () -> Unit,
    onOpenModel: (String) -> Unit,
    onOpenAchievements: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as StreetSpotApplication
    val factory = remember(application) {
        viewModelFactory {
            initializer { HomeViewModel(application.progressRepository) }
        }
    }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val level = remember(snapshot.totalXp) { LevelCalculator.calculate(snapshot.totalXp) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("STREETSPOT", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.home_level_title, level.level),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(rankLabel(level.rank), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_xp_total, level.totalXp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { level.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = if (level.isMaxLevel) {
                        stringResource(R.string.home_max_level)
                    } else {
                        stringResource(
                            R.string.home_xp_progress,
                            level.xpIntoLevel,
                            level.xpIntoLevel + level.xpToNextLevel,
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeMetric(
                value = snapshot.totalSpots,
                label = stringResource(R.string.home_stat_spots),
                modifier = Modifier.weight(1f),
            )
            HomeMetric(
                value = snapshot.brandsDiscovered,
                label = stringResource(R.string.home_stat_brands),
                modifier = Modifier.weight(1f),
            )
            HomeMetric(
                value = snapshot.rareFinds,
                label = stringResource(R.string.home_stat_rare),
                modifier = Modifier.weight(1f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.home_recent_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            if (snapshot.recentSpots.isEmpty()) {
                Text(stringResource(R.string.home_recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAddSpot, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_no_spots_action))
                }
            } else {
                snapshot.recentSpots.forEach { spot ->
                    RecentSpotCard(spot, onClick = { onOpenModel(spot.spot.carModelId) })
                }
            }
        }

        snapshot.nextAchievement?.let { next ->
            NextAchievementCard(next, onOpenAchievements)
        }
    }
}

@Composable
private fun HomeMetric(value: Int, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(value.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentSpotCard(spot: SpotWithModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoredPhotoImage(
                relativePath = spot.spot.photoPath,
                contentDescription = "${spot.brandName} ${spot.modelName}",
                modifier = Modifier.width(96.dp).height(76.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${spot.brandName} ${spot.modelName}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${spot.spot.observedLocalDate} · ${spot.spot.observedLocalTime}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                spot.spot.color?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun NextAchievementCard(achievement: AchievementProgress, onOpenAchievements: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.home_next_achievement), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(achievement.title(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(achievement.description(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { achievement.completionFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_achievement_progress, achievement.progress, achievement.target),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = onOpenAchievements) {
                    Text(stringResource(R.string.home_open_achievements), color = MaterialTheme.colorScheme.primary)
                }
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

@Composable
private fun rankLabel(rank: Rank): String = stringResource(
    when (rank) {
        Rank.NEWCOMER -> R.string.home_rank_newcomer
        Rank.STREET_OBSERVER -> R.string.home_rank_street_observer
        Rank.CAR_ENTHUSIAST -> R.string.home_rank_car_enthusiast
        Rank.SPOTTER -> R.string.home_rank_spotter
        Rank.STREET_HUNTER -> R.string.home_rank_street_hunter
        Rank.CAR_COLLECTOR -> R.string.home_rank_car_collector
        Rank.RARE_HUNTER -> R.string.home_rank_rare_hunter
        Rank.STREET_LEGEND -> R.string.home_rank_street_legend
    },
)
