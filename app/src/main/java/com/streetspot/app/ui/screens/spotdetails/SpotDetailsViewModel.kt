package com.streetspot.app.ui.screens.spotdetails

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CarBodyStyle
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.data.photo.PreparedPhoto
import com.streetspot.app.data.repository.CarCatalogRepository
import com.streetspot.app.data.repository.SpotDraft
import com.streetspot.app.data.repository.SpotRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class SpotDetailsError {
    LOAD,
    PHOTO,
    DATE_TIME,
    VEHICLE_YEAR,
    UPDATE,
    DELETE,
}

data class SpotDetailsUiState(
    val model: CarModelWithBrand? = null,
    val models: List<CarModelWithBrand> = emptyList(),
    val spots: List<Spot> = emptyList(),
    val replacementPhoto: PreparedPhoto? = null,
    val replacementForSpotId: String? = null,
    val isBusy: Boolean = false,
    val error: SpotDetailsError? = null,
    val updateCompleteForSpotId: String? = null,
)

class SpotDetailsViewModel(
    private val modelId: String,
    private val catalogRepository: CarCatalogRepository,
    private val spotRepository: SpotRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SpotDetailsUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val model = catalogRepository.findModel(modelId)
                if (model == null) {
                    mutableState.update { it.copy(error = SpotDetailsError.LOAD) }
                    return@launch
                }
                mutableState.update { it.copy(model = model) }
                launch {
                    catalogRepository.observeModels()
                        .catch { mutableState.update { state -> state.copy(error = SpotDetailsError.LOAD) } }
                        .collect { models -> mutableState.update { it.copy(models = models) } }
                }
                spotRepository.observeForModel(modelId)
                    .catch { mutableState.update { state -> state.copy(error = SpotDetailsError.LOAD) } }
                    .collect { spots -> mutableState.update { it.copy(spots = spots) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = SpotDetailsError.LOAD) }
            }
        }
    }

    fun prepareReplacementPhoto(uri: Uri, spotId: String) {
        viewModelScope.launch {
            val before = mutableState.value
            val previous = before.replacementPhoto
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                val prepared = photoStorage.prepare(uri)
                if (previous != null) photoStorage.discard(previous)
                mutableState.update {
                    it.copy(
                        replacementPhoto = prepared,
                        replacementForSpotId = spotId,
                        isBusy = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(isBusy = false, error = SpotDetailsError.PHOTO) }
            }
        }
    }

    fun clearReplacementPhoto() {
        val photo = mutableState.value.replacementPhoto
        mutableState.update { it.copy(replacementPhoto = null, replacementForSpotId = null) }
        if (photo != null) viewModelScope.launch { photoStorage.discard(photo) }
    }

    fun updateSpot(
        existing: Spot,
        carModelId: String,
        date: String,
        time: String,
        color: String,
        notes: String,
        placeLabel: String,
        vehicleYear: String,
        bodyStyle: CarBodyStyle?,
    ) {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        val parsedTime = runCatching { LocalTime.parse(time) }.getOrNull()
        if (parsedDate == null || parsedTime == null) {
            mutableState.update { it.copy(error = SpotDetailsError.DATE_TIME) }
            return
        }
        val parsedVehicleYear = vehicleYear.takeIf(String::isNotBlank)?.toIntOrNull()
        if (vehicleYear.isNotBlank() && (parsedVehicleYear == null || parsedVehicleYear !in 1886..(LocalDate.now().year + 1))) {
            mutableState.update { it.copy(error = SpotDetailsError.VEHICLE_YEAR) }
            return
        }
        viewModelScope.launch {
            val before = mutableState.value
            val replacement = before.replacementPhoto?.takeIf {
                before.replacementForSpotId == existing.id
            }
            if (before.replacementPhoto != null && replacement == null) {
                photoStorage.discard(before.replacementPhoto)
                mutableState.update { it.copy(replacementPhoto = null, replacementForSpotId = null) }
            }
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                spotRepository.update(
                    existing = existing,
                    draft = SpotDraft(
                        carModelId = carModelId,
                        observedDate = parsedDate,
                        observedTime = parsedTime,
                        color = color,
                        notes = notes,
                        placeLabel = placeLabel,
                        vehicleYear = parsedVehicleYear,
                        bodyStyle = bodyStyle,
                    ),
                    replacementPhoto = replacement,
                )
                mutableState.update {
                    it.copy(
                        isBusy = false,
                        replacementPhoto = null,
                        replacementForSpotId = null,
                        error = null,
                        updateCompleteForSpotId = existing.id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        isBusy = false,
                        replacementPhoto = null,
                        replacementForSpotId = null,
                        error = SpotDetailsError.UPDATE,
                    )
                }
            }
        }
    }

    fun clearUpdateComplete() {
        mutableState.update { it.copy(updateCompleteForSpotId = null) }
    }

    fun deleteSpot(spot: Spot) {
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                spotRepository.delete(spot)
                mutableState.update { it.copy(isBusy = false, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(isBusy = false, error = SpotDetailsError.DELETE) }
            }
        }
    }

    override fun onCleared() {
        mutableState.value.replacementPhoto?.file?.delete()
        super.onCleared()
    }
}
