package com.streetspot.app.data.repository

import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CarModelSpotdexEntry
import com.streetspot.app.data.model.CarRarity
import kotlinx.coroutines.flow.Flow

interface CarCatalogRepository {
    fun observeModels(): Flow<List<CarModelWithBrand>>

    fun observeSpotdex(): Flow<List<CarModelSpotdexEntry>>

    suspend fun findModel(id: String): CarModelWithBrand?

    suspend fun createUserModel(
        brandName: String,
        modelName: String,
        generation: String?,
        countryCode: String?,
        category: CarCategory,
    ): CarModelWithBrand

    suspend fun ensureDefaultCatalog()
}
