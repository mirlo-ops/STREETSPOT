package com.streetspot.app.data.model

import androidx.room.Embedded

data class SpotWithModel(
    @Embedded val spot: Spot,
    val brandId: String,
    val brandName: String,
    val modelName: String,
    val modelGeneration: String?,
    val modelCategory: CarCategory,
    val modelRarity: CarRarity,
)
