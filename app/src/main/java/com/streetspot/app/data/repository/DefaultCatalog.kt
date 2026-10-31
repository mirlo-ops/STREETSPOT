package com.streetspot.app.data.repository

import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarRarity

data class DefaultBrandDefinition(
    val id: String,
    val name: String,
)

data class DefaultModelDefinition(
    val id: String,
    val brandId: String,
    val name: String,
    val countryCode: String,
    val category: CarCategory,
    val rarity: CarRarity,
)

object DefaultCatalog {
    val brands = listOf(
        DefaultBrandDefinition("default-brand-toyota", "Toyota"),
        DefaultBrandDefinition("default-brand-mazda", "Mazda"),
        DefaultBrandDefinition("default-brand-nissan", "Nissan"),
        DefaultBrandDefinition("default-brand-honda", "Honda"),
        DefaultBrandDefinition("default-brand-bmw", "BMW"),
        DefaultBrandDefinition("default-brand-porsche", "Porsche"),
        DefaultBrandDefinition("default-brand-volkswagen", "Volkswagen"),
        DefaultBrandDefinition("default-brand-ford", "Ford"),
        DefaultBrandDefinition("default-brand-chevrolet", "Chevrolet"),
        DefaultBrandDefinition("default-brand-dodge", "Dodge"),
        DefaultBrandDefinition("default-brand-ferrari", "Ferrari"),
        DefaultBrandDefinition("default-brand-lamborghini", "Lamborghini"),
        DefaultBrandDefinition("default-brand-jaguar", "Jaguar"),
        DefaultBrandDefinition("default-brand-mercedes-benz", "Mercedes-Benz"),
        DefaultBrandDefinition("default-brand-land-rover", "Land Rover"),
        DefaultBrandDefinition("default-brand-jeep", "Jeep"),
    )

    val models = listOf(
        DefaultModelDefinition("default-model-toyota-supra", "default-brand-toyota", "Supra", "JP", CarCategory.JDM, CarRarity.SPORT),
        DefaultModelDefinition("default-model-mazda-mx-5", "default-brand-mazda", "MX-5", "JP", CarCategory.JDM, CarRarity.COMMON),
        DefaultModelDefinition("default-model-nissan-skyline-gt-r", "default-brand-nissan", "Skyline GT-R", "JP", CarCategory.JDM, CarRarity.RARE),
        DefaultModelDefinition("default-model-honda-civic-type-r", "default-brand-honda", "Civic Type R", "JP", CarCategory.JDM, CarRarity.UNCOMMON),
        DefaultModelDefinition("default-model-bmw-m3", "default-brand-bmw", "M3", "DE", CarCategory.EURO, CarRarity.UNCOMMON),
        DefaultModelDefinition("default-model-porsche-911", "default-brand-porsche", "911", "DE", CarCategory.EURO, CarRarity.RARE),
        DefaultModelDefinition("default-model-volkswagen-golf-gti", "default-brand-volkswagen", "Golf GTI", "DE", CarCategory.EURO, CarRarity.COMMON),
        DefaultModelDefinition("default-model-ford-mustang", "default-brand-ford", "Mustang", "US", CarCategory.MUSCLE, CarRarity.COMMON),
        DefaultModelDefinition("default-model-chevrolet-corvette", "default-brand-chevrolet", "Corvette", "US", CarCategory.MUSCLE, CarRarity.SPORT),
        DefaultModelDefinition("default-model-dodge-challenger", "default-brand-dodge", "Challenger", "US", CarCategory.MUSCLE, CarRarity.UNCOMMON),
        DefaultModelDefinition("default-model-ferrari-296-gtb", "default-brand-ferrari", "296 GTB", "IT", CarCategory.SUPERCAR, CarRarity.LEGENDARY),
        DefaultModelDefinition("default-model-lamborghini-huracan", "default-brand-lamborghini", "Huracán", "IT", CarCategory.SUPERCAR, CarRarity.RARE),
        DefaultModelDefinition("default-model-jaguar-e-type", "default-brand-jaguar", "E-Type", "GB", CarCategory.CLASSIC, CarRarity.LEGENDARY),
        DefaultModelDefinition("default-model-mercedes-benz-300-sl", "default-brand-mercedes-benz", "300 SL", "DE", CarCategory.CLASSIC, CarRarity.LEGENDARY),
        DefaultModelDefinition("default-model-land-rover-defender", "default-brand-land-rover", "Defender", "GB", CarCategory.OTHER, CarRarity.COMMON),
        DefaultModelDefinition("default-model-jeep-wrangler", "default-brand-jeep", "Wrangler", "US", CarCategory.OTHER, CarRarity.COMMON),
    )
}
