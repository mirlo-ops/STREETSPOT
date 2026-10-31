package com.streetspot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streetspot.app.data.model.Brand
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY normalizedName")
    fun observeAll(): Flow<List<Brand>>

    @Query("SELECT * FROM brands WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Brand?

    @Query("SELECT * FROM brands ORDER BY normalizedName, id")
    suspend fun getAll(): List<Brand>

    @Query("SELECT * FROM brands WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): Brand?

    @Query("SELECT COUNT(*) FROM brands")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(brand: Brand)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(brands: List<Brand>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringConflict(brands: List<Brand>)

    @Query("DELETE FROM brands")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(brand: Brand)

    @Update
    suspend fun update(brand: Brand)

    @Delete
    suspend fun delete(brand: Brand)
}
