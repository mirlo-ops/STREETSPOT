package com.streetspot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CarModelSpotdexEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CarModelDao {
    @Query(
        """
        SELECT car_models.*,
               brand.id AS brand_id,
               brand.name AS brand_name,
               brand.normalizedName AS brand_normalizedName,
               brand.source AS brand_source,
               brand.createdAt AS brand_createdAt,
               brand.vehiclesDbId AS brand_vehiclesDbId,
               brand.aliases AS brand_aliases,
               COUNT(spots.id) AS spotCount,
               MAX(spots.observedAt) AS latestObservedAt,
               (SELECT photoPath FROM spots AS cover
                WHERE cover.carModelId = car_models.id
                ORDER BY cover.observedAt DESC, cover.createdAt DESC, cover.id
                LIMIT 1) AS coverPhotoPath
        FROM car_models
        INNER JOIN brands AS brand ON brand.id = car_models.brandId
        INNER JOIN spots ON spots.carModelId = car_models.id
        GROUP BY car_models.id
        ORDER BY latestObservedAt DESC, brand.normalizedName, car_models.normalizedName
        """,
    )
    fun observeSpotdex(): Flow<List<CarModelSpotdexEntry>>

    @Query(
        """
        SELECT car_models.*,
               brand.id AS brand_id,
               brand.name AS brand_name,
               brand.normalizedName AS brand_normalizedName,
               brand.source AS brand_source,
               brand.createdAt AS brand_createdAt,
               brand.vehiclesDbId AS brand_vehiclesDbId,
               brand.aliases AS brand_aliases
        FROM car_models
        INNER JOIN brands AS brand ON brand.id = car_models.brandId
        ORDER BY brand.normalizedName, car_models.normalizedName, car_models.normalizedGeneration
        """,
    )
    fun observeAllWithBrand(): Flow<List<CarModelWithBrand>>

    @Query(
        """
        SELECT car_models.*,
               brand.id AS brand_id,
               brand.name AS brand_name,
               brand.normalizedName AS brand_normalizedName,
               brand.source AS brand_source,
               brand.createdAt AS brand_createdAt,
               brand.vehiclesDbId AS brand_vehiclesDbId,
               brand.aliases AS brand_aliases
        FROM car_models
        INNER JOIN brands AS brand ON brand.id = car_models.brandId
        WHERE car_models.id = :id
        LIMIT 1
        """,
    )
    suspend fun findWithBrand(id: String): CarModelWithBrand?

    @Query(
        """
        SELECT car_models.*,
               brand.id AS brand_id,
               brand.name AS brand_name,
               brand.normalizedName AS brand_normalizedName,
               brand.source AS brand_source,
               brand.createdAt AS brand_createdAt,
               brand.vehiclesDbId AS brand_vehiclesDbId,
               brand.aliases AS brand_aliases
        FROM car_models
        INNER JOIN brands AS brand ON brand.id = car_models.brandId
        """,
    )
    suspend fun getAllWithBrand(): List<CarModelWithBrand>

    @Query("SELECT * FROM car_models ORDER BY brandId, normalizedName, normalizedGeneration, id")
    suspend fun getAll(): List<CarModel>

    @Query(
        """
        SELECT * FROM car_models
        WHERE brandId = :brandId
          AND normalizedName = :normalizedName
          AND normalizedGeneration = :normalizedGeneration
        LIMIT 1
        """,
    )
    suspend fun findByIdentity(
        brandId: String,
        normalizedName: String,
        normalizedGeneration: String,
    ): CarModel?

    @Query("SELECT COUNT(*) FROM car_models")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM car_models WHERE vehiclesDbId IS NOT NULL")
    suspend fun countVehiclesDbModels(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(model: CarModel)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(models: List<CarModel>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringConflict(models: List<CarModel>)

    @Query("DELETE FROM car_models")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(model: CarModel)

    @Update
    suspend fun update(model: CarModel)

    @Delete
    suspend fun delete(model: CarModel)
}
