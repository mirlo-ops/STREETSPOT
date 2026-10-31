package com.streetspot.app.ui.screens.spotdetails

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarBodyStyle
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.Spot
import com.streetspot.app.ui.components.StoredPhotoImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SpotDetailsScreen(
    modelId: String,
    onBackToCollection: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as StreetSpotApplication
    val factory = remember(application, modelId) {
        viewModelFactory {
            initializer {
                SpotDetailsViewModel(
                    modelId = modelId,
                    catalogRepository = application.carCatalogRepository,
                    spotRepository = application.spotRepository,
                    photoStorage = application.photoStorage,
                )
            }
        }
    }
    val viewModel: SpotDetailsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingSpot by remember { mutableStateOf<Spot?>(null) }
    var deletingSpot by remember { mutableStateOf<Spot?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val spot = editingSpot
        if (uri != null && spot != null) viewModel.prepareReplacementPhoto(uri, spot.id)
    }

    LaunchedEffect(state.updateCompleteForSpotId) {
        if (state.updateCompleteForSpotId != null && state.updateCompleteForSpotId == editingSpot?.id) {
            viewModel.clearUpdateComplete()
            editingSpot = null
        }
    }

    val model = state.model
    if (model == null) {
        val loadError = state.error
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadError == null) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            else Text(stringResource(loadError.messageResource()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        ModelHeader(model)
        Text(
            text = stringResource(R.string.spot_details_count),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
        )
        when {
            state.spots.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.spot_details_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onBackToCollection, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.spot_details_deleted_empty_action))
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
            ) {
                items(state.spots, key = Spot::id) { spot ->
                    SpotRecordCard(
                        spot = spot,
                        enabled = !state.isBusy,
                        onEdit = { editingSpot = spot },
                        onDelete = { deletingSpot = spot },
                    )
                }
            }
        }
        state.error?.let { error ->
            Text(
                text = stringResource(error.messageResource()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    editingSpot?.let { spot ->
        EditSpotDialog(
            spot = spot,
            models = state.models,
            replacementReady = state.replacementForSpotId == spot.id && state.replacementPhoto != null,
            isBusy = state.isBusy,
            error = state.error,
            onChoosePhoto = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onSave = { selectedModelId, date, time, color, notes, placeLabel, vehicleYear, bodyStyle ->
                viewModel.updateSpot(spot, selectedModelId, date, time, color, notes, placeLabel, vehicleYear, bodyStyle)
            },
            onDismiss = {
                viewModel.clearReplacementPhoto()
                editingSpot = null
            },
        )
    }

    deletingSpot?.let { spot ->
        AlertDialog(
            onDismissRequest = { deletingSpot = null },
            title = { Text(stringResource(R.string.spot_details_delete_title)) },
            text = { Text(stringResource(R.string.spot_details_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingSpot = null
                        viewModel.deleteSpot(spot)
                    },
                    enabled = !state.isBusy,
                ) { Text(stringResource(R.string.spot_details_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSpot = null }) {
                    Text(stringResource(R.string.spot_details_cancel))
                }
            },
        )
    }
}

@Composable
private fun ModelHeader(model: CarModelWithBrand) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${model.brand.name} ${model.model.name}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
            )
            model.model.generation?.let {
                Text(stringResource(R.string.spot_details_generation, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            model.model.countryCode?.let {
                Text(stringResource(R.string.spot_details_country, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(model.model.category.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(model.model.rarity.label(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SpotRecordCard(
    spot: Spot,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            StoredPhotoImage(
                relativePath = spot.photoPath,
                contentDescription = stringResource(R.string.add_photo_preview_label),
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val date = runCatching {
                    LocalDate.parse(spot.observedLocalDate).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                }.getOrDefault(spot.observedLocalDate)
                Text(
                    text = stringResource(R.string.spot_details_date_value, date, spot.observedLocalTime),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                spot.vehicleYear?.let { Text(stringResource(R.string.spot_details_vehicle_year, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                spot.bodyStyle?.let { Text(stringResource(R.string.spot_details_body_style, it.label()), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                spot.color?.let { Text(stringResource(R.string.spot_details_color_value, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                spot.placeLabel?.let { Text(stringResource(R.string.spot_details_place_value, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                spot.notes?.let {
                    Text(
                        text = stringResource(R.string.spot_details_notes_value, it),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.spot_details_edit))
                    }
                    TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.spot_details_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSpotDialog(
    spot: Spot,
    models: List<CarModelWithBrand>,
    replacementReady: Boolean,
    isBusy: Boolean,
    error: SpotDetailsError?,
    onChoosePhoto: () -> Unit,
    onSave: (String, String, String, String, String, String, String, CarBodyStyle?) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by rememberSaveable(spot.id) { mutableStateOf(spot.observedLocalDate) }
    var time by rememberSaveable(spot.id) { mutableStateOf(spot.observedLocalTime) }
    var color by rememberSaveable(spot.id) { mutableStateOf(spot.color.orEmpty()) }
    var notes by rememberSaveable(spot.id) { mutableStateOf(spot.notes.orEmpty()) }
    var placeLabel by rememberSaveable(spot.id) { mutableStateOf(spot.placeLabel.orEmpty()) }
    var vehicleYear by rememberSaveable(spot.id) { mutableStateOf(spot.vehicleYear?.toString().orEmpty()) }
    var bodyStyle by rememberSaveable(spot.id) { mutableStateOf(spot.bodyStyle) }
    var selectedModelId by rememberSaveable(spot.id) { mutableStateOf(spot.carModelId) }
    var showModelPicker by rememberSaveable(spot.id) { mutableStateOf(false) }
    val selectedModel = models.firstOrNull { it.model.id == selectedModelId }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.spot_details_edit_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { showModelPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = selectedModel?.let { "${it.brand.name} ${it.model.name}" }
                            ?: stringResource(R.string.spot_details_choose_model),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.spot_details_date_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text(stringResource(R.string.spot_details_time_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = vehicleYear,
                    onValueChange = { value -> vehicleYear = value.filter { character -> character in '0'..'9' }.take(4) },
                    label = { Text(stringResource(R.string.add_vehicle_year_label)) },
                    placeholder = { Text(stringResource(R.string.add_vehicle_year_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.add_body_style_label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = bodyStyle == null,
                        onClick = { bodyStyle = null },
                        label = { Text(stringResource(R.string.add_body_style_unspecified)) },
                    )
                    CarBodyStyle.entries.forEach { style ->
                        FilterChip(
                            selected = bodyStyle == style,
                            onClick = { bodyStyle = style },
                            label = { Text(style.label()) },
                        )
                    }
                }
                TextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text(stringResource(R.string.spot_details_color_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.spot_details_notes_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = placeLabel,
                    onValueChange = { placeLabel = it },
                    label = { Text(stringResource(R.string.spot_details_place_label)) },
                    supportingText = { Text(stringResource(R.string.spot_details_place_helper)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onChoosePhoto, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (replacementReady) R.string.spot_details_photo_selected
                            else R.string.spot_details_replace_photo,
                        ),
                    )
                }
                error?.let {
                    Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error)
                }
                if (isBusy) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedModelId, date, time, color, notes, placeLabel, vehicleYear, bodyStyle) },
                enabled = !isBusy && selectedModelId.isNotBlank(),
            ) { Text(stringResource(R.string.spot_details_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.spot_details_cancel))
            }
        },
    )

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text(stringResource(R.string.spot_details_model_picker_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    models.forEach { model ->
                        Text(
                            text = "${model.brand.name} ${model.model.name}" +
                                model.model.generation?.let { " · $it" }.orEmpty(),
                            color = if (model.model.id == selectedModelId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedModelId = model.model.id
                                    showModelPicker = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) {
                    Text(stringResource(R.string.spot_details_cancel))
                }
            },
        )
    }
}

@Composable
private fun CarCategory.label(): String = when (this) {
    CarCategory.JDM -> stringResource(R.string.category_jdm)
    CarCategory.EURO -> stringResource(R.string.category_euro)
    CarCategory.MUSCLE -> stringResource(R.string.category_muscle)
    CarCategory.SUPERCAR -> stringResource(R.string.category_supercar)
    CarCategory.CLASSIC -> stringResource(R.string.category_classic)
    CarCategory.OTHER -> stringResource(R.string.category_other)
}

@Composable
private fun CarBodyStyle.label(): String = when (this) {
    CarBodyStyle.SEDAN -> stringResource(R.string.body_style_sedan)
    CarBodyStyle.HATCHBACK -> stringResource(R.string.body_style_hatchback)
    CarBodyStyle.COUPE -> stringResource(R.string.body_style_coupe)
    CarBodyStyle.WAGON -> stringResource(R.string.body_style_wagon)
    CarBodyStyle.LIFTBACK -> stringResource(R.string.body_style_liftback)
    CarBodyStyle.SUV -> stringResource(R.string.body_style_suv)
    CarBodyStyle.CROSSOVER -> stringResource(R.string.body_style_crossover)
    CarBodyStyle.PICKUP -> stringResource(R.string.body_style_pickup)
    CarBodyStyle.VAN -> stringResource(R.string.body_style_van)
    CarBodyStyle.CONVERTIBLE -> stringResource(R.string.body_style_convertible)
    CarBodyStyle.MPV -> stringResource(R.string.body_style_mpv)
    CarBodyStyle.ROADSTER -> stringResource(R.string.body_style_roadster)
    CarBodyStyle.OTHER -> stringResource(R.string.body_style_other)
}

@Composable
private fun CarRarity.label(): String = when (this) {
    CarRarity.COMMON -> stringResource(R.string.rarity_common)
    CarRarity.UNCOMMON -> stringResource(R.string.rarity_uncommon)
    CarRarity.SPORT -> stringResource(R.string.rarity_sport)
    CarRarity.RARE -> stringResource(R.string.rarity_rare)
    CarRarity.LEGENDARY -> stringResource(R.string.rarity_legendary)
}

@Composable
private fun SpotDetailsError.messageResource(): Int = when (this) {
    SpotDetailsError.LOAD -> R.string.spot_details_load_error
    SpotDetailsError.PHOTO -> R.string.spot_details_photo_error
    SpotDetailsError.DATE_TIME -> R.string.spot_details_date_error
    SpotDetailsError.VEHICLE_YEAR -> R.string.add_vehicle_year_error
    SpotDetailsError.UPDATE -> R.string.spot_details_update_error
    SpotDetailsError.DELETE -> R.string.spot_details_delete_error
}
