package com.streetspot.app.ui.screens.addspot

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarBodyStyle
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CatalogMetadataCodec
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.data.photo.PreparedPhoto
import com.streetspot.app.data.model.Spot
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun AddSpotScreen(
    onFinish: () -> Unit,
    onOpenExisting: (String) -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as StreetSpotApplication
    val factory = remember(application) {
        viewModelFactory {
            initializer {
                AddSpotViewModel(
                    catalogRepository = application.carCatalogRepository,
                    spotRepository = application.spotRepository,
                    photoStorage = application.photoStorage,
                )
            }
        }
    }
    val viewModel: AddSpotViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var showCreateModel by rememberSaveable { mutableStateOf(false) }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        showCamera = false
        if (uri != null) viewModel.preparePhoto(uri)
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        cameraPermissionDenied = !granted
        showCamera = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AddSpotStepIndicator(currentStep = state.step, onSelectStep = viewModel::setStep)

        when {
            state.savedSpot != null -> SavedSpotCard(
                reachedLevel = state.reachedLevel,
                onFinish = onFinish,
            )
            state.step == AddSpotStep.PHOTO -> PhotoStep(
                preparedPhoto = state.preparedPhoto,
                isBusy = state.isBusy,
                showCamera = showCamera,
                cameraPermissionGranted = cameraPermissionGranted,
                cameraPermissionDenied = cameraPermissionDenied,
                photoStorage = application.photoStorage,
                onRequestCamera = {
                    if (cameraPermissionGranted) {
                        showCamera = !showCamera
                    } else {
                        permissionRequest.launch(Manifest.permission.CAMERA)
                    }
                },
                onChoosePhoto = {
                    showCamera = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCameraPhotoReady = viewModel::prepareCameraPhoto,
                onCameraError = { viewModel.showError(AddSpotError.PHOTO) },
                onContinue = { viewModel.setStep(AddSpotStep.IDENTIFY) },
            )
            state.step == AddSpotStep.IDENTIFY -> IdentifyStep(
                models = state.models,
                brandQuery = state.brandQuery,
                selectedBrandId = state.selectedBrandId,
                modelQuery = state.modelQuery,
                selectedModelId = state.selectedModelId,
                vehicleYear = state.vehicleYear,
                bodyStyle = state.bodyStyle,
                isBusy = state.isBusy,
                onBrandQueryChanged = viewModel::setBrandQuery,
                onSelectBrand = viewModel::selectBrand,
                onClearBrand = viewModel::clearBrandSelection,
                onQueryChanged = viewModel::setModelQuery,
                onSelectModel = viewModel::selectModel,
                onVehicleYearChanged = viewModel::updateVehicleYear,
                onSelectBodyStyle = viewModel::selectBodyStyle,
                onCreateModel = { showCreateModel = true },
                onBack = { viewModel.setStep(AddSpotStep.PHOTO) },
                onContinue = { viewModel.setStep(AddSpotStep.SAVE) },
            )
            else -> SaveStep(
                models = state.models,
                selectedModelId = state.selectedModelId,
                vehicleYear = state.vehicleYear,
                bodyStyle = state.bodyStyle,
                observedDate = state.observedDate,
                observedTime = state.observedTime,
                color = state.color,
                notes = state.notes,
                placeLabel = state.placeLabel,
                isBusy = state.isBusy,
                onDateChanged = viewModel::updateObservedDate,
                onTimeChanged = viewModel::updateObservedTime,
                onColorChanged = viewModel::updateColor,
                onNotesChanged = viewModel::updateNotes,
                onPlaceLabelChanged = viewModel::updatePlaceLabel,
                onBack = { viewModel.setStep(AddSpotStep.IDENTIFY) },
                onSave = { viewModel.save() },
            )
        }

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageResource()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.isBusy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.add_photo_processing),
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCreateModel) {
        CreateModelDialog(
            onDismiss = { showCreateModel = false },
            initialBrandName = state.models.firstOrNull { it.brand.id == state.selectedBrandId }?.brand?.name,
            brandSeed = state.brandQuery,
            initialModelName = state.modelQuery,
            onCreate = { brand, name, generation, country, category ->
                showCreateModel = false
                viewModel.createUserModel(brand, name, generation, country, category)
            },
        )
    }

    state.duplicateSpot?.let { duplicate ->
        ExactDuplicateDialog(
            onOpenExisting = {
                viewModel.dismissDuplicate(openExisting = true)
                onOpenExisting(duplicate.carModelId)
            },
            onSaveSeparately = { viewModel.save(saveExactDuplicate = true) },
            onDismiss = { viewModel.dismissDuplicate(openExisting = false) },
        )
    }
}

@Composable
private fun AddSpotStepIndicator(
    currentStep: AddSpotStep,
    onSelectStep: (AddSpotStep) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AddSpotStep.entries.forEach { step ->
            FilterChip(
                selected = currentStep == step,
                onClick = { onSelectStep(step) },
                label = { Text(stringResource(step.labelResource())) },
            )
        }
    }
}

@Composable
private fun PhotoStep(
    preparedPhoto: PreparedPhoto?,
    isBusy: Boolean,
    showCamera: Boolean,
    cameraPermissionGranted: Boolean,
    cameraPermissionDenied: Boolean,
    photoStorage: PhotoStorage,
    onRequestCamera: () -> Unit,
    onChoosePhoto: () -> Unit,
    onCameraPhotoReady: (java.io.File) -> Unit,
    onCameraError: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (preparedPhoto != null) {
            PhotoPreview(preparedPhoto)
        } else {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    text = stringResource(R.string.add_photo_empty),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRequestCamera,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_action_take_photo), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onChoosePhoto,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null)
                Text(stringResource(R.string.add_action_choose_photo), modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (showCamera) {
            if (cameraPermissionGranted) {
                CameraCapturePanel(
                    photoStorage = photoStorage,
                    onPhotoReady = onCameraPhotoReady,
                    onError = onCameraError,
                )
            } else if (!cameraPermissionDenied) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text(
                        text = stringResource(R.string.add_camera_permission_description),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.add_camera_denied),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (preparedPhoto != null) {
            Button(onClick = onContinue, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_action_continue))
            }
        }
    }
}

@Composable
private fun PhotoPreview(preparedPhoto: PreparedPhoto) {
    val bitmap by produceState<Bitmap?>(initialValue = null, preparedPhoto.file.absolutePath) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(preparedPhoto.file.absolutePath) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.add_photo_preview_label),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CameraCapturePanel(
    photoStorage: PhotoStorage,
    onPhotoReady: (java.io.File) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    var capturing by remember { mutableStateOf(false) }
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        try {
            controller.bindToLifecycle(lifecycleOwner)
        } catch (error: Exception) {
            currentOnError()
        }
        onDispose { controller.unbind() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Button(
            onClick = {
                scope.launch {
                    capturing = true
                    val outputFile = try {
                        photoStorage.createCameraCaptureFile()
                    } catch (error: Exception) {
                        capturing = false
                        currentOnError()
                        return@launch
                    }
                    val output = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                    controller.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                capturing = false
                                onPhotoReady(outputFile)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                outputFile.delete()
                                capturing = false
                                currentOnError()
                            }
                        },
                    )
                }
            },
            enabled = !capturing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_action_take_photo))
        }
    }
}

@Composable
private fun IdentifyStep(
    models: List<CarModelWithBrand>,
    brandQuery: String,
    selectedBrandId: String?,
    modelQuery: String,
    selectedModelId: String?,
    vehicleYear: String,
    bodyStyle: CarBodyStyle?,
    isBusy: Boolean,
    onBrandQueryChanged: (String) -> Unit,
    onSelectBrand: (String) -> Unit,
    onClearBrand: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onVehicleYearChanged: (String) -> Unit,
    onSelectBodyStyle: (CarBodyStyle?) -> Unit,
    onCreateModel: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val brands = remember(models) { models.map { it.brand }.distinctBy { it.id }.sortedBy { it.normalizedName } }
    val selectedBrand = brands.firstOrNull { it.id == selectedBrandId }
    val normalizedBrandQuery = brandQuery.trim().lowercase()
    val brandSearchTerms = remember(brands) {
        brands.associate { brand ->
            brand.id to (listOf(brand.name) + CatalogMetadataCodec.decode(brand.aliases)).map { it.lowercase() }
        }
    }
    val filteredBrands = remember(brands, normalizedBrandQuery, brandSearchTerms) {
        brands.filter { brand ->
            normalizedBrandQuery.isEmpty() || brandSearchTerms[brand.id].orEmpty().any { it.contains(normalizedBrandQuery) }
        }.take(8)
    }
    val normalizedModelQuery = modelQuery.trim().lowercase()
    val modelSearchTerms = remember(models) {
        models.associate { item ->
            item.model.id to (
                listOf(item.model.name, item.model.generation.orEmpty()) +
                    CatalogMetadataCodec.decode(item.model.aliases)
                ).joinToString(" ").lowercase()
        }
    }
    val filteredModels = remember(models, selectedBrandId, normalizedModelQuery, modelSearchTerms) {
        models.asSequence()
            .filter { item ->
                item.brand.id == selectedBrandId &&
                    (normalizedModelQuery.isEmpty() ||
                        modelSearchTerms[item.model.id].orEmpty().contains(normalizedModelQuery))
            }
            .sortedWith(
                compareBy<CarModelWithBrand>({ it.model.massPopularityDecile ?: Int.MAX_VALUE })
                    .thenBy { it.model.globalPopularityDecile ?: Int.MAX_VALUE }
                    .thenBy { it.model.normalizedName },
            )
            .take(if (normalizedModelQuery.isEmpty()) 8 else 30)
            .toList()
    }
    val selectedModel = models.firstOrNull { it.model.id == selectedModelId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_model_brand_step), style = MaterialTheme.typography.titleMedium)
        if (selectedBrand == null) {
            TextField(
                value = brandQuery,
                onValueChange = onBrandQueryChanged,
                label = { Text(stringResource(R.string.add_model_brand_label)) },
                placeholder = { Text(stringResource(R.string.add_model_brand_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (filteredBrands.isEmpty()) {
                Text(stringResource(R.string.add_model_no_brands), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                filteredBrands.forEach { brand ->
                    OutlinedButton(
                        onClick = { onSelectBrand(brand.id) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(brand.name) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedBrand.name,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onClearBrand, enabled = !isBusy) {
                    Text(stringResource(R.string.add_model_change_brand))
                }
            }
            TextField(
                value = modelQuery,
                onValueChange = onQueryChanged,
                label = { Text(stringResource(R.string.add_model_name_label)) },
                placeholder = { Text(stringResource(R.string.add_model_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selectedBrand.vehiclesDbId != null && filteredModels.any {
                    it.model.massPopularityDecile != null || it.model.globalPopularityDecile != null
                }
            ) {
                Text(
                    stringResource(R.string.add_model_catalog_order_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (filteredModels.isEmpty()) {
                Text(stringResource(R.string.add_model_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                filteredModels.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBusy) { onSelectModel(item.model.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.model.id == selectedModelId) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.model.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                val generation = item.model.generation ?: stringResource(R.string.add_model_generation_unspecified)
                                Text(
                                    text = "${stringResource(R.string.add_model_generation_short, generation)} · ${stringResource(item.model.category.labelResource())}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (item.model.id == selectedModelId) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onCreateModel, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = selectedBrand?.let { stringResource(R.string.add_model_create_for_brand_action, it.name) }
                    ?: stringResource(R.string.add_model_create_with_brand_action),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (selectedModel != null) {
            Text(
                text = stringResource(
                    R.string.add_model_selected,
                    selectedModel.brand.name,
                    selectedModel.model.name,
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextField(
                value = vehicleYear,
                onValueChange = onVehicleYearChanged,
                label = { Text(stringResource(R.string.add_vehicle_year_label)) },
                placeholder = { Text(stringResource(R.string.add_vehicle_year_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.add_body_style_label), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = bodyStyle == null,
                    onClick = { onSelectBodyStyle(null) },
                    label = { Text(stringResource(R.string.add_body_style_unspecified)) },
                )
                val catalogBodyStyles = remember(selectedModel?.model?.bodyTypes) {
                    CatalogMetadataCodec.decode(selectedModel?.model?.bodyTypes)
                        .mapNotNull(String::toCatalogBodyStyle)
                        .distinct()
                }
                val bodyStyleOptions = catalogBodyStyles.ifEmpty { CarBodyStyle.entries }
                bodyStyleOptions.forEach { option ->
                    FilterChip(
                        selected = bodyStyle == option,
                        onClick = { onSelectBodyStyle(option) },
                        label = { Text(stringResource(option.labelResource())) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.add_action_previous))
            }
            Button(
                onClick = onContinue,
                enabled = !isBusy && selectedModel != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.add_action_continue))
            }
        }
    }
}

@Composable
private fun SaveStep(
    models: List<CarModelWithBrand>,
    selectedModelId: String?,
    vehicleYear: String,
    bodyStyle: CarBodyStyle?,
    observedDate: String,
    observedTime: String,
    color: String,
    notes: String,
    placeLabel: String,
    isBusy: Boolean,
    onDateChanged: (String) -> Unit,
    onTimeChanged: (String) -> Unit,
    onColorChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onPlaceLabelChanged: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val selectedModel = models.firstOrNull { it.model.id == selectedModelId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        selectedModel?.let {
            val bodyStyleLabel = bodyStyle?.let { style ->
                stringResource(R.string.add_body_style_summary, stringResource(style.labelResource()))
            }
            val yearLabel = vehicleYear.takeIf(String::isNotBlank)?.let {
                stringResource(R.string.add_vehicle_year_summary, it)
            }
            val categoryLabel = stringResource(it.model.category.labelResource())
            val selectedDetails = listOfNotNull(
                it.model.generation,
                stringResource(R.string.add_model_category_summary, categoryLabel),
                yearLabel,
                bodyStyleLabel,
            )
                .joinToString(" · ")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${it.brand.name} ${it.model.name}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (selectedDetails.isNotEmpty()) {
                        Text(selectedDetails, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        TextField(
            value = observedDate,
            onValueChange = onDateChanged,
            label = { Text(stringResource(R.string.add_date_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = observedTime,
            onValueChange = onTimeChanged,
            label = { Text(stringResource(R.string.add_time_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = color,
            onValueChange = onColorChanged,
            label = { Text(stringResource(R.string.add_color_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = notes,
            onValueChange = onNotesChanged,
            label = { Text(stringResource(R.string.add_notes_label)) },
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = placeLabel,
            onValueChange = onPlaceLabelChanged,
            label = { Text(stringResource(R.string.add_place_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = { Text(stringResource(R.string.add_place_helper)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.add_action_previous))
            }
            Button(
                onClick = onSave,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.add_save_action))
            }
        }
    }
}

@Composable
private fun CreateModelDialog(
    onDismiss: () -> Unit,
    initialBrandName: String?,
    brandSeed: String,
    initialModelName: String,
    onCreate: (String, String, String?, String?, CarCategory) -> Unit,
) {
    var brandName by rememberSaveable(initialBrandName, brandSeed) { mutableStateOf(initialBrandName ?: brandSeed) }
    var modelName by rememberSaveable(initialBrandName, initialModelName) { mutableStateOf(initialModelName) }
    var generation by rememberSaveable { mutableStateOf("") }
    var countryCode by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CarCategory.OTHER.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_model_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (initialBrandName == null) {
                    TextField(
                        value = brandName,
                        onValueChange = { brandName = it },
                        label = { Text(stringResource(R.string.add_model_brand_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.add_model_for_brand, initialBrandName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text(stringResource(R.string.add_model_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = generation,
                    onValueChange = { generation = it },
                    label = { Text(stringResource(R.string.add_model_generation_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = countryCode,
                    onValueChange = { countryCode = it },
                    label = { Text(stringResource(R.string.add_model_country_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.add_model_category_label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                CarCategory.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { category = option.name },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = category == option.name,
                            onClick = { category = option.name },
                        )
                        Text(stringResource(option.labelResource()), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        initialBrandName ?: brandName,
                        modelName,
                        generation,
                        countryCode,
                        CarCategory.valueOf(category),
                    )
                },
                enabled = brandName.isNotBlank() && modelName.isNotBlank(),
            ) {
                Text(stringResource(R.string.add_model_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_model_cancel_action)) }
        },
    )
}

@Composable
private fun ExactDuplicateDialog(
    onOpenExisting: () -> Unit,
    onSaveSeparately: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_duplicate_title)) },
        text = { Text(stringResource(R.string.add_duplicate_description)) },
        confirmButton = {
            TextButton(onClick = onOpenExisting) {
                Text(stringResource(R.string.add_duplicate_open_action))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSaveSeparately) {
                    Text(stringResource(R.string.add_duplicate_save_action))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.add_model_cancel_action))
                }
            }
        },
    )
}

@Composable
private fun SavedSpotCard(reachedLevel: Int?, onFinish: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(stringResource(R.string.add_saved_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.add_saved_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reachedLevel != null) {
                Text(
                    stringResource(R.string.home_level_up, reachedLevel),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.home_level_up_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_saved_finish_action))
            }
        }
    }
}

private fun AddSpotStep.labelResource(): Int = when (this) {
    AddSpotStep.PHOTO -> R.string.add_step_photo
    AddSpotStep.IDENTIFY -> R.string.add_step_identify
    AddSpotStep.SAVE -> R.string.add_step_save
}

private fun AddSpotError.messageResource(): Int = when (this) {
    AddSpotError.PHOTO -> R.string.add_photo_empty_error
    AddSpotError.MODEL -> R.string.add_model_error
    AddSpotError.DATE_TIME -> R.string.add_date_error
    AddSpotError.SAVE -> R.string.add_save_error
    AddSpotError.CATALOG -> R.string.add_catalog_error
    AddSpotError.VEHICLE_YEAR -> R.string.add_vehicle_year_error
}

private fun CarCategory.labelResource(): Int = when (this) {
    CarCategory.JDM -> R.string.category_jdm
    CarCategory.EURO -> R.string.category_euro
    CarCategory.MUSCLE -> R.string.category_muscle
    CarCategory.SUPERCAR -> R.string.category_supercar
    CarCategory.CLASSIC -> R.string.category_classic
    CarCategory.OTHER -> R.string.category_other
}

private fun CarBodyStyle.labelResource(): Int = when (this) {
    CarBodyStyle.SEDAN -> R.string.body_style_sedan
    CarBodyStyle.HATCHBACK -> R.string.body_style_hatchback
    CarBodyStyle.COUPE -> R.string.body_style_coupe
    CarBodyStyle.WAGON -> R.string.body_style_wagon
    CarBodyStyle.LIFTBACK -> R.string.body_style_liftback
    CarBodyStyle.SUV -> R.string.body_style_suv
    CarBodyStyle.CROSSOVER -> R.string.body_style_crossover
    CarBodyStyle.PICKUP -> R.string.body_style_pickup
    CarBodyStyle.VAN -> R.string.body_style_van
    CarBodyStyle.ROADSTER -> R.string.body_style_roadster
    CarBodyStyle.CONVERTIBLE -> R.string.body_style_convertible
    CarBodyStyle.MPV -> R.string.body_style_mpv
    CarBodyStyle.OTHER -> R.string.body_style_other
}

private fun String.toCatalogBodyStyle(): CarBodyStyle? = when (lowercase()) {
    "sedan" -> CarBodyStyle.SEDAN
    "hatchback" -> CarBodyStyle.HATCHBACK
    "coupe" -> CarBodyStyle.COUPE
    "wagon" -> CarBodyStyle.WAGON
    "suv" -> CarBodyStyle.SUV
    "mpv" -> CarBodyStyle.MPV
    "convertible" -> CarBodyStyle.CONVERTIBLE
    "roadster" -> CarBodyStyle.ROADSTER
    "pickup" -> CarBodyStyle.PICKUP
    "van" -> CarBodyStyle.VAN
    else -> null
}
