package com.streetspot.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "car_models",
    foreignKeys = [
        ForeignKey(
            entity = Brand::class,
            parentColumns = ["id"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["brandId", "normalizedName", "normalizedGeneration"], unique = true),
        Index(value = ["category"]),
        Index(value = ["rarity"]),
        Index(value = ["vehiclesDbId"], unique = true),
    ],
)
data class CarModel(
    @PrimaryKey val id: String,
    val brandId: String,
    val name: String,
    val normalizedName: String,
    val generation: String?,
    val normalizedGeneration: String,
    val countryCode: String?,
    val category: CarCategory,
    val rarity: CarRarity,
    val source: RecordSource,
    val createdAt: Long,
    val updatedAt: Long,
    val vehiclesDbId: String? = null,
    val aliases: String? = null,
    val bodyTypes: String? = null,
    val globalPopularityDecile: Int? = null,
    val massPopularityDecile: Int? = null,
)
