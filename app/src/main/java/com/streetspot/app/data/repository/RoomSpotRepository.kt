package com.streetspot.app.data.repository

import androidx.room.withTransaction
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.data.photo.PreparedPhoto
import com.streetspot.app.domain.progression.LevelCalculator
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class RoomSpotRepository(
    private val database: StreetSpotDatabase,
    private val photoStorage: PhotoStorage,
    private val progressRepository: ProgressRepository,
    private val onDataChanged: suspend () -> Unit = {},
) : SpotRepository {
    override fun observeAll(): Flow<List<Spot>> = database.spotDao().observeAll()

    override suspend fun findById(id: String): Spot? = database.spotDao().findById(id)

    override fun observeForModel(carModelId: String): Flow<List<Spot>> =
        database.spotDao().observeForModel(carModelId)

    override suspend fun save(
        draft: SpotDraft,
        preparedPhoto: PreparedPhoto,
        saveExactDuplicate: Boolean,
    ): SpotSaveResult {
        val photoHash = preparedPhoto.sha256
        if (!saveExactDuplicate) {
            val duplicate = database.spotDao().findExactDuplicate(
                carModelId = draft.carModelId,
                observedLocalDate = draft.observedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                photoSha256 = photoHash,
            )
            if (duplicate != null) return SpotSaveResult.ExactDuplicate(duplicate)
        }

        val spotId = UUID.randomUUID().toString()
        val storedPhoto = try {
            photoStorage.persist(preparedPhoto, spotId)
        } catch (error: Exception) {
            photoStorage.discard(preparedPhoto)
            throw error
        }

        val localDateTime = draft.observedDate.atTime(draft.observedTime)
        val spot = Spot(
            id = spotId,
            carModelId = draft.carModelId,
            photoPath = storedPhoto.relativePath,
            photoSha256 = storedPhoto.sha256,
            observedAt = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            observedLocalDate = draft.observedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            observedLocalTime = draft.observedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            createdAt = System.currentTimeMillis(),
            color = draft.color?.trim()?.takeIf(String::isNotEmpty),
            notes = draft.notes?.trim()?.takeIf(String::isNotEmpty),
            placeLabel = draft.placeLabel?.trim()?.takeIf(String::isNotEmpty),
            vehicleYear = draft.vehicleYear,
            bodyStyle = draft.bodyStyle,
        )

        val result = try {
            database.withTransaction {
                val beforeXp = database.xpTransactionDao().getTotalXp()
                database.spotDao().insert(spot)
                val afterXp = progressRepository.recalculateWithinTransaction().totalXp
                val beforeLevel = LevelCalculator.calculate(beforeXp).level
                val afterLevel = LevelCalculator.calculate(afterXp).level
                SpotSaveResult.Saved(
                    spot = spot,
                    reachedLevel = afterLevel.takeIf { it > beforeLevel },
                )
            }
        } catch (error: Exception) {
            photoStorage.deleteStoredPhoto(storedPhoto.relativePath)
            throw error
        } finally {
            photoStorage.discard(preparedPhoto)
        }
        refreshWidgets()
        return result
    }

    override suspend fun allPhotoPaths(): Set<String> = database.spotDao().getAllPhotoPaths().toSet()

    override suspend fun update(
        existing: Spot,
        draft: SpotDraft,
        replacementPhoto: PreparedPhoto?,
    ): Spot {
        val replacement = try {
            replacementPhoto?.let { photoStorage.persistReplacement(it, existing.id) }
        } catch (error: Exception) {
            photoStorage.discard(replacementPhoto)
            throw error
        }
        val updated = existing.copy(
            carModelId = draft.carModelId,
            photoPath = replacement?.relativePath ?: existing.photoPath,
            photoSha256 = replacement?.sha256 ?: existing.photoSha256,
            observedAt = draft.observedDate.atTime(draft.observedTime)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            observedLocalDate = draft.observedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            observedLocalTime = draft.observedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = draft.color?.trim()?.takeIf(String::isNotEmpty),
            notes = draft.notes?.trim()?.takeIf(String::isNotEmpty),
            placeLabel = draft.placeLabel?.trim()?.takeIf(String::isNotEmpty),
            vehicleYear = draft.vehicleYear,
            bodyStyle = draft.bodyStyle,
        )
        try {
            database.withTransaction {
                database.spotDao().update(updated)
                progressRepository.recalculateWithinTransaction()
            }
        } catch (error: Exception) {
            replacement?.let { photoStorage.deleteStoredPhoto(it.relativePath) }
            throw error
        } finally {
            photoStorage.discard(replacementPhoto)
        }
        if (replacement != null) {
            runCatching { photoStorage.deleteStoredPhoto(existing.photoPath) }
        }
        refreshWidgets()
        return updated
    }

    override suspend fun delete(spot: Spot) {
        database.withTransaction {
            database.spotDao().delete(spot)
            progressRepository.recalculateWithinTransaction()
        }
        runCatching { photoStorage.deleteStoredPhoto(spot.photoPath) }
        refreshWidgets()
    }

    private suspend fun refreshWidgets() {
        try {
            onDataChanged()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The database change is already committed; widget refresh is best-effort.
        }
    }
}
