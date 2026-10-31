package com.streetspot.app.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.domain.statistics.StatisticsSnapshot
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatisticsScreen() {
    val application = LocalContext.current.applicationContext as StreetSpotApplication
    val factory = remember(application) {
        viewModelFactory {
            initializer { StatisticsViewModel(application.progressRepository) }
        }
    }
    val viewModel: StatisticsViewModel = viewModel(factory = factory)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()

    if (snapshot == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(24.dp))
        }
    } else {
        StatisticsContent(snapshot!!)
    }
}

@Composable
private fun StatisticsContent(snapshot: StatisticsSnapshot) {
    val monthLabel = remember(snapshot.month) {
        snapshot.month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru")))
    }
    val maxCategoryCount = snapshot.categoryDistribution.maxOfOrNull { it.spotCount }?.coerceAtLeast(1) ?: 1
    val maxBrandCount = snapshot.topBrands.maxOfOrNull { it.spotCount }?.coerceAtLeast(1) ?: 1

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard(snapshot.totalSpots, stringResource(R.string.stats_metric_total_spots), Modifier.weight(1f))
                    SummaryCard(snapshot.uniqueModels, stringResource(R.string.stats_metric_unique_models), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard(snapshot.brandsDiscovered, stringResource(R.string.stats_metric_brands), Modifier.weight(1f))
                    SummaryCard(snapshot.rareFinds, stringResource(R.string.stats_metric_rare), Modifier.weight(1f))
                }
                if (snapshot.totalSpots == 0) {
                    Text(stringResource(R.string.stats_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.stats_month_value), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.stats_month_heading, monthLabel), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(snapshot.spotsThisMonth.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        item {
            StatisticsSection(title = stringResource(R.string.stats_top_brands)) {
                if (snapshot.topBrands.isEmpty()) {
                    Text(stringResource(R.string.stats_top_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    snapshot.topBrands.forEachIndexed { index, brand ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${index + 1}. ${brand.name}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.stats_record_count, brand.spotCount),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                            LinearProgressIndicator(
                                progress = { brand.spotCount.toFloat() / maxBrandCount },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            StatisticsSection(title = stringResource(R.string.stats_category_distribution)) {
                snapshot.categoryDistribution.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(entry.category.categoryLabel(), color = MaterialTheme.colorScheme.onSurface)
                            Text(entry.spotCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { entry.spotCount.toFloat() / maxCategoryCount },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SummaryCard(value: Int, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatisticsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun CarCategory.categoryLabel(): String = when (this) {
    CarCategory.JDM -> stringResource(R.string.category_jdm)
    CarCategory.EURO -> stringResource(R.string.category_euro)
    CarCategory.MUSCLE -> stringResource(R.string.category_muscle)
    CarCategory.SUPERCAR -> stringResource(R.string.category_supercar)
    CarCategory.CLASSIC -> stringResource(R.string.category_classic)
    CarCategory.OTHER -> stringResource(R.string.category_other)
}
