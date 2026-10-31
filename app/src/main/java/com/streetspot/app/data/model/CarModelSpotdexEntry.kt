package com.streetspot.app.data.model

import androidx.room.Embedded

data class CarModelSpotdexEntry(
    @Embedded val model: CarModel,
    @Embedded(prefix = "brand_") val brand: Brand,
    val spotCount: Int,
    val latestObservedAt: Long,
    val coverPhotoPath: String,
)
