package com.streetspot.app.ui.screens.addspot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarBodyStyle
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.data.photo.PreparedPhoto
import com.streetspot.app.data.repository.CarCatalogRepository
import com.streetspot.app.data.repository.SpotDraft
import com.streetspot.app.data.repository.SpotRepository
import com.streetspot.app.data.repository.SpotSaveResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class AddSpotStep {
    PHOTO,
    IDENTIFY,
    SAVE,
}

enum class AddSpotError {
    PHOTO,
    MODEL,
    DATE_TIME,
    VEHICLE_YEAR,
    SAVE,
    CATALOG,
}

data class AddSpotUiState(
    val step: AddSpotStep = AddSpotStep.PHOTO,
    val preparedPhoto: PreparedPhoto? = null,
    val models: List<CarModelWithBrand> = emptyList(),
    val brandQuery: String = "",
    val selectedBrandId: String? = null,
    val modelQuery: String = "",
    val selectedModelId: String? = null,
    val vehicleYear: String = "",
    val bodyStyle: CarBodyStyle? = null,
    val observedDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val observedTime: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val color: String = "",
    val notes: String = "",
    val placeLabel: String = "",
    val isBusy: Boolean = false,
    val error: AddSpotError? = null,
    val duplicateSpot: Spot? = null,
    val savedSpot: Spot? = null,
    val reachedLevel: Int? = null,
)

class AddSpotViewModel(
    private val catalogRepository: CarCatalogRepository,
    private val spotRepository: SpotRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AddSpotUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            catalogRepository.observeModels()
                .catch { mutableState.update { state -> state.copy(error = AddSpotError.CATALOG) } }
                .collect { models -> mutableState.update { it.copy(models = models) } }
        }
    }

    fun preparePhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            prepareImage { photoStorage.prepare(uri) }
        }
    }

    fun prepareCameraPhoto(file: File) {
        viewModelScope.launch {
            prepareImage { photoStorage.prepare(file) }
        }
    }

    private suspend fun prepareImage(load: suspend () -> PreparedPhoto) {
        val oldPhoto = mutableState.value.preparedPhoto
        mutableState.update { it.copy(isBusy = true, error = null, duplicateSpot = null, savedSpot = null) }
        try {
            val photo = load()
            if (oldPhoto != null && oldPhoto.file != photo.file) photoStorage.discard(oldPhoto)
            mutableState.update {
                it.copy(
                    preparedPhoto = photo,
                    step = AddSpotStep.PHOTO,
                    isBusy = false,
                    error = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.update { it.copy(isBusy = false, error = AddSpotError.PHOTO) }
        }
    }

    fun setStep(step: AddSpotStep) {
        val current = mutableState.value
        val error = when (step) {
            AddSpotStep.PHOTO -> null
            AddSpotStep.IDENTIFY -> if (current.preparedPhoto == null) AddSpotError.PHOTO else null
            AddSpotStep.SAVE -> when {
                current.selectedModelId == null -> AddSpotError.MODEL
                current.vehicleYear.isNotBlank() && parseVehicleYear(current.vehicleYear) == null -> AddSpotError.VEHICLE_YEAR
                else -> null
            }
        }
        mutableState.update { it.copy(step = if (error == null) step else it.step, error = error) }
    }

    fun setBrandQuery(query: String) = mutableState.update {
        it.copy(brandQuery = query, error = null)
    }

    fun selectBrand(brandId: String) = mutableState.update {
        it.copy(
            selectedBrandId = brandId,
            brandQuery = "",
            modelQuery = "",
            selectedModelId = null,
            vehicleYear = "",
            bodyStyle = null,
            error = null,
        )
    }

    fun clearBrandSelection() = mutableState.update {
        it.copy(
            selectedBrandId = null,
            selectedModelId = null,
            modelQuery = "",
            vehicleYear = "",
            bodyStyle = null,
            error = null,
        )
    }

    fun setModelQuery(query: String) = mutableState.update {
        it.copy(modelQuery = query, error = null)
    }

    fun selectModel(modelId: String) = mutableState.update {
        if (it.selectedModelId == modelId) it.copy(error = null)
        else it.copy(selectedModelId = modelId, vehicleYear = "", bodyStyle = null, error = null)
    }

    fun updateVehicleYear(value: String) = mutableState.update {
        it.copy(vehicleYear = value.filter { character -> character in '0'..'9' }.take(4), error = null)
    }

    fun selectBodyStyle(value: CarBodyStyle?) = mutableState.update {
        it.copy(bodyStyle = value, error = null)
    }

    fun createUserModel(
        brandName: String,
        modelName: String,
        generation: String?,
        countryCode: String?,
        category: CarCategory,
    ) {
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                val model = catalogRepository.createUserModel(
                    brandName = brandName,
                    modelName = modelName,
                    generation = generation,
                    countryCode = countryCode,
                    category = category,
                )
                mutableState.update {
                    it.copy(
                        selectedModelId = model.model.id,
                        selectedBrandId = model.brand.id,
                        brandQuery = "",
                        modelQuery = "",
                        vehicleYear = "",
                        bodyStyle = null,
                        step = AddSpotStep.IDENTIFY,
                        isBusy = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                mutableState.update { it.copy(isBusy = false, error = AddSpotError.MODEL) }
            } catch (error: Exception) {
                mutableState.update { it.copy(isBusy = false, error = AddSpotError.CATALOG) }
            }
        }
    }

    fun updateObservedDate(value: String) = mutableState.update {
        it.copy(observedDate = value, error = null)
    }

    fun updateObservedTime(value: String) = mutableState.update {
        it.copy(observedTime = value, error = null)
    }

    fun updateColor(value: String) = mutableState.update { it.copy(color = value) }

    fun updateNotes(value: String) = mutableState.update { it.copy(notes = value) }

    fun updatePlaceLabel(value: String) = mutableState.update { it.copy(placeLabel = value) }

    fun showError(error: AddSpotError) = mutableState.update { it.copy(error = error) }

    fun save(saveExactDuplicate: Boolean = false) {
        val current = mutableState.value
        if (current.isBusy) return
        val selectedModelId = current.selectedModelId
        if (selectedModelId == null) {
            mutableState.update { it.copy(error = AddSpotError.MODEL) }
            return
        }
        val photo = current.preparedPhoto
        if (photo == null || !photo.file.isFile) {
            mutableState.update { it.copy(error = AddSpotError.PHOTO) }
            return
        }

        val date = runCatching { LocalDate.parse(current.observedDate) }.getOrNull()
        val time = runCatching { LocalTime.parse(current.observedTime) }.getOrNull()
        if (date == null || time == null) {
            mutableState.update { it.copy(error = AddSpotError.DATE_TIME) }
            return
        }
        val vehicleYear = parseVehicleYear(current.vehicleYear)
        if (current.vehicleYear.isNotBlank() && vehicleYear == null) {
            mutableState.update { it.copy(error = AddSpotError.VEHICLE_YEAR) }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                when (
                    val result = spotRepository.save(
                        draft = SpotDraft(
                            carModelId = selectedModelId,
                            observedDate = date,
                            observedTime = time,
                            color = current.color,
                            notes = current.notes,
                            placeLabel = current.placeLabel,
                            vehicleYear = vehicleYear,
                            bodyStyle = current.bodyStyle,
                        ),
                        preparedPhoto = photo,
                        saveExactDuplicate = saveExactDuplicate,
                    )
                ) {
                    is SpotSaveResult.ExactDuplicate -> mutableState.update {
                        it.copy(isBusy = false, duplicateSpot = result.existingSpot)
                    }
                    is SpotSaveResult.Saved -> mutableState.update {
                        it.copy(
                            isBusy = false,
                            preparedPhoto = null,
                            duplicateSpot = null,
                            savedSpot = result.spot,
                            reachedLevel = result.reachedLevel,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(isBusy = false, error = AddSpotError.SAVE) }
            }
        }
    }

    fun dismissDuplicate(openExisting: Boolean) {
        val photo = mutableState.value.preparedPhoto
        mutableState.update { it.copy(duplicateSpot = null) }
        if (openExisting && photo != null) {
            viewModelScope.launch { photoStorage.discard(photo) }
            mutableState.update { it.copy(preparedPhoto = null) }
        }
    }

    override fun onCleared() {
        mutableState.value.preparedPhoto?.file?.delete()
        super.onCleared()
    }

    private fun parseVehicleYear(value: String): Int? = value.toIntOrNull()
        ?.takeIf { it in 1886..(LocalDate.now().year + 1) }
}
