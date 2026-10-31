package com.streetspot.app.data.repository

import androidx.room.withTransaction
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.Brand
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.NameNormalizer
import com.streetspot.app.data.model.RecordSource

class DefaultCatalogSeeder(
    private val database: StreetSpotDatabase,
) {
    suspend fun seed() {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val brandDao = database.brandDao()
            val modelDao = database.carModelDao()

            DefaultCatalog.brands.forEach { definition ->
                val normalizedName = NameNormalizer.normalizedName(definition.name)
                brandDao.insertIgnoringConflict(
                    Brand(
                        id = definition.id,
                        name = definition.name,
                        normalizedName = normalizedName,
                        source = RecordSource.DEFAULT,
                        createdAt = now,
                    ),
                )
            }

            val brandsById = DefaultCatalog.brands.associate { definition ->
                val normalizedName = NameNormalizer.normalizedName(definition.name)
                val persistedBrand = brandDao.findByNormalizedName(normalizedName)
                    ?: error("Brand seed failed for ${definition.name}")
                definition.id to persistedBrand
            }

            DefaultCatalog.models.forEach { definition ->
                val brand = brandsById[definition.brandId]
                    ?: error("Missing brand seed ${definition.brandId}")
                val normalizedName = NameNormalizer.normalizedName(definition.name)

                modelDao.insertIgnoringConflict(
                    CarModel(
                        id = definition.id,
                        brandId = brand.id,
                        name = definition.name,
                        normalizedName = normalizedName,
                        generation = null,
                        normalizedGeneration = "",
                        countryCode = definition.countryCode,
                        category = definition.category,
                        rarity = definition.rarity,
                        source = RecordSource.DEFAULT,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}
