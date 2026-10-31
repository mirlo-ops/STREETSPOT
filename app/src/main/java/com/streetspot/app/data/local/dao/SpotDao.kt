package com.streetspot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.SpotWithModel
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao {
    @Query(
        """
        SELECT spots.*,
               brands.id AS brandId,
               brands.name AS brandName,
               car_models.name AS modelName,
               car_models.generation AS modelGeneration,
               car_models.category AS modelCategory,
               car_models.rarity AS modelRarity
        FROM spots
        INNER JOIN car_models ON car_models.id = spots.carModelId
        INNER JOIN brands ON brands.id = car_models.brandId
        ORDER BY spots.observedAt DESC, spots.createdAt DESC, spots.id
        """,
    )
    fun observeAllWithModel(): Flow<List<SpotWithModel>>

    @Query("SELECT * FROM spots ORDER BY observedAt DESC, createdAt DESC, id")
    fun observeAll(): Flow<List<Spot>>

    @Query(
        """
        SELECT * FROM spots
        WHERE carModelId = :carModelId
        ORDER BY observedAt DESC, createdAt DESC, id
        """,
    )
    fun observeForModel(carModelId: String): Flow<List<Spot>>

    @Query("SELECT * FROM spots WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Spot?

    @Query(
        """
        SELECT * FROM spots
        WHERE carModelId = :carModelId
          AND observedLocalDate = :observedLocalDate
          AND photoSha256 = :photoSha256
        ORDER BY createdAt, id
        LIMIT 1
        """,
    )
    suspend fun findExactDuplicate(
        carModelId: String,
        observedLocalDate: String,
        photoSha256: String,
    ): Spot?

    @Query("SELECT photoPath FROM spots")
    suspend fun getAllPhotoPaths(): List<String>

    @Query("SELECT COUNT(*) FROM spots")
    suspend fun countAll(): Int

    @Query("SELECT * FROM spots ORDER BY observedAt, createdAt, id")
    suspend fun getAll(): List<Spot>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(spot: Spot)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(spots: List<Spot>)

    @Query("DELETE FROM spots")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(spot: Spot)

    @Update
    suspend fun update(spot: Spot)
}
