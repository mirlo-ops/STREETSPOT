package com.streetspot.app.ui.screens.spotdex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarModelSpotdexEntry
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.ui.components.StoredPhotoImage
import java.util.Locale

@Composable
fun SpotdexScreen(
    onOpenModel: (String) -> Unit,
    onAddSpot: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as StreetSpotApplication
    val factory = remember(application) {
        viewModelFactory {
            initializer { SpotdexViewModel(application.carCatalogRepository) }
        }
    }
    val viewModel: SpotdexViewModel = viewModel(factory = factory)
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var selectedRarity by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filteredEntries = remember(entries, normalizedQuery, selectedCategory, selectedRarity) {
        entries.filter { entry ->
            (selectedCategory.isEmpty() || entry.model.category.name == selectedCategory) &&
                (selectedRarity.isEmpty() || entry.model.rarity.name == selectedRarity) &&
                (normalizedQuery.isEmpty() || listOf(
                    entry.brand.name,
                    entry.model.name,
                    entry.model.generation.orEmpty(),
                ).any { it.lowercase(Locale.ROOT).contains(normalizedQuery) })
        }
    }
    val gridState = rememberLazyGridState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.spotdex_search_label)) },
            placeholder = { Text(stringResource(R.string.spotdex_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterRow(
            label = stringResource(R.string.spotdex_category_filters),
            selected = selectedCategory,
            choices = CarCategory.entries.map { it.name to it.categoryLabel() },
            onSelect = { selectedCategory = it },
        )
        FilterRow(
            label = stringResource(R.string.spotdex_rarity_filters),
            selected = selectedRarity,
            choices = CarRarity.entries.map { it.name to it.rarityLabel() },
            onSelect = { selectedRarity = it },
        )

        when {
            entries.isEmpty() -> EmptyCollection(onAddSpot, modifier = Modifier.weight(1f))
            filteredEntries.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.spotdex_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredEntries, key = { it.model.id }) { entry ->
                    SpotdexCard(entry, onClick = { onOpenModel(entry.model.id) })
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    selected: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected.isEmpty(),
                onClick = { onSelect("") },
                label = { Text(stringResource(R.string.spotdex_all_filters)) },
            )
            choices.forEach { (key, value) ->
                FilterChip(
                    selected = selected == key,
                    onClick = { onSelect(if (selected == key) "" else key) },
                    label = { Text(value) },
                )
            }
        }
    }
}

@Composable
private fun SpotdexCard(entry: CarModelSpotdexEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            StoredPhotoImage(
                relativePath = entry.coverPhotoPath,
                contentDescription = "${entry.brand.name} ${entry.model.name}",
                modifier = Modifier.fillMaxWidth().aspectRatio(1.12f).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.brand.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.model.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.spotdex_spot_count, entry.spotCount, entry.spotCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = entry.model.rarity.rarityLabel(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyCollection(onAddSpot: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.spotdex_empty_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.spotdex_empty_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAddSpot) { Text(stringResource(R.string.spotdex_add_spot)) }
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

@Composable
private fun CarRarity.rarityLabel(): String = when (this) {
    CarRarity.COMMON -> stringResource(R.string.rarity_common)
    CarRarity.UNCOMMON -> stringResource(R.string.rarity_uncommon)
    CarRarity.SPORT -> stringResource(R.string.rarity_sport)
    CarRarity.RARE -> stringResource(R.string.rarity_rare)
    CarRarity.LEGENDARY -> stringResource(R.string.rarity_legendary)
}
