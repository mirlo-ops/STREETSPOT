package com.streetspot.app.data.model

import androidx.room.Embedded

data class CarModelWithBrand(
    @Embedded val model: CarModel,
    @Embedded(prefix = "brand_") val brand: Brand,
)
