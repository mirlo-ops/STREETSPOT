package com.streetspot.app.data.repository

import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.CarBodyStyle
import com.streetspot.app.data.photo.PreparedPhoto
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

data class SpotDraft(
    val carModelId: String,
    val observedDate: LocalDate,
    val observedTime: LocalTime,
    val color: String?,
    val notes: String?,
    val placeLabel: String?,
    val vehicleYear: Int? = null,
    val bodyStyle: CarBodyStyle? = null,
)

sealed interface SpotSaveResult {
    data class Saved(val spot: Spot, val reachedLevel: Int? = null) : SpotSaveResult

    data class ExactDuplicate(val existingSpot: Spot) : SpotSaveResult
}

interface SpotRepository {
    fun observeAll(): Flow<List<Spot>>

    suspend fun findById(id: String): Spot?

    fun observeForModel(carModelId: String): Flow<List<Spot>>

    suspend fun save(
        draft: SpotDraft,
        preparedPhoto: com.streetspot.app.data.photo.PreparedPhoto,
        saveExactDuplicate: Boolean = false,
    ): SpotSaveResult

    suspend fun allPhotoPaths(): Set<String>

    suspend fun update(
        existing: Spot,
        draft: SpotDraft,
        replacementPhoto: PreparedPhoto? = null,
    ): Spot

    suspend fun delete(spot: Spot)
}
