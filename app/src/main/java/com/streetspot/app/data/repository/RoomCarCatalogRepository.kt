package com.streetspot.app.data.repository

import androidx.room.withTransaction
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.Brand
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CarModelSpotdexEntry
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.NameNormalizer
import com.streetspot.app.data.model.RecordSource
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID

class RoomCarCatalogRepository(
    private val database: StreetSpotDatabase,
) : CarCatalogRepository {
    override fun observeModels(): Flow<List<CarModelWithBrand>> =
        database.carModelDao().observeAllWithBrand()

    override fun observeSpotdex(): Flow<List<CarModelSpotdexEntry>> =
        database.carModelDao().observeSpotdex()

    override suspend fun findModel(id: String): CarModelWithBrand? =
        database.carModelDao().findWithBrand(id)

    override suspend fun createUserModel(
        brandName: String,
        modelName: String,
        generation: String?,
        countryCode: String?,
        category: CarCategory,
    ): CarModelWithBrand {
        val displayBrandName = NameNormalizer.displayName(brandName)
        val normalizedBrandName = NameNormalizer.normalizedName(displayBrandName)
        require(normalizedBrandName.isNotEmpty()) { "Brand name must not be blank" }

        val displayModelName = NameNormalizer.displayName(modelName)
        val normalizedModelName = NameNormalizer.normalizedName(displayModelName)
        require(normalizedModelName.isNotEmpty()) { "Model name must not be blank" }

        val displayGeneration = NameNormalizer.displayGeneration(generation)
        val normalizedGeneration = NameNormalizer.normalizedGeneration(displayGeneration)
        val normalizedCountryCode = countryCode
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.uppercase(Locale.ROOT)
        require(normalizedCountryCode == null || normalizedCountryCode.matches(Regex("[A-Z]{2}"))) {
            "Country code must be a two-letter ISO 3166-1 alpha-2 code"
        }

        return database.withTransaction {
            val now = System.currentTimeMillis()
            val brandDao = database.brandDao()
            val modelDao = database.carModelDao()

            brandDao.insertIgnoringConflict(
                Brand(
                    id = UUID.randomUUID().toString(),
                    name = displayBrandName,
                    normalizedName = normalizedBrandName,
                    source = RecordSource.USER,
                    createdAt = now,
                ),
            )
            val brand = brandDao.findByNormalizedName(normalizedBrandName)
                ?: error("Inserted brand could not be read")

            modelDao.insertIgnoringConflict(
                CarModel(
                    id = UUID.randomUUID().toString(),
                    brandId = brand.id,
                    name = displayModelName,
                    normalizedName = normalizedModelName,
                    generation = displayGeneration,
                    normalizedGeneration = normalizedGeneration,
                    countryCode = normalizedCountryCode,
                    category = category,
                    rarity = CarRarity.COMMON,
                    source = RecordSource.USER,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val model = modelDao.findByIdentity(
                brandId = brand.id,
                normalizedName = normalizedModelName,
                normalizedGeneration = normalizedGeneration,
            ) ?: error("Inserted model could not be read")

            modelDao.findWithBrand(model.id) ?: error("Inserted model relationship could not be read")
        }
    }

    override suspend fun ensureDefaultCatalog() {
        DefaultCatalogSeeder(database).seed()
    }
}
