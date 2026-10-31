package com.streetspot.app.domain.statistics

import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.SpotWithModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StatisticsCalculatorTest {
    @Test
    fun emptyJournalReturnsZeroTotalsAndAllCategoryRows() {
        val snapshot = StatisticsCalculator.calculate(
            spots = emptyList(),
            now = Instant.parse("2026-10-29T12:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(0, snapshot.totalSpots)
        assertEquals(0, snapshot.uniqueModels)
        assertEquals(0, snapshot.brandsDiscovered)
        assertEquals(0, snapshot.rareFinds)
        assertEquals(0, snapshot.spotsThisMonth)
        assertEquals(CarCategory.entries.size, snapshot.categoryDistribution.size)
        assertEquals(0, snapshot.categoryDistribution.sumOf(CategorySpotCount::spotCount))
    }

    @Test
    fun summariesCountUniqueModelsAndRareFindsButCategoriesAndMonthCountSpots() {
        val spots = listOf(
            spot("january", "model-a", "brand-a", "Toyota", "2026-01-31T23:59:00Z", CarCategory.JDM, CarRarity.RARE),
            spot("february-1", "model-a", "brand-a", "Toyota", "2026-02-01T00:00:00Z", CarCategory.JDM, CarRarity.RARE),
            spot("february-2", "model-b", "brand-b", "Alfa Romeo", "2026-02-01T01:00:00Z", CarCategory.EURO, CarRarity.COMMON),
            spot("future", "model-c", "brand-c", "BMW", "2026-02-01T02:00:00Z", CarCategory.EURO, CarRarity.LEGENDARY),
        )

        val snapshot = StatisticsCalculator.calculate(
            spots = spots,
            now = Instant.parse("2026-02-01T01:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(4, snapshot.totalSpots)
        assertEquals(3, snapshot.uniqueModels)
        assertEquals(3, snapshot.brandsDiscovered)
        assertEquals(2, snapshot.rareFinds)
        assertEquals(2, snapshot.spotsThisMonth)
        assertEquals(2, snapshot.categoryDistribution.single { it.category == CarCategory.JDM }.spotCount)
        assertEquals(2, snapshot.categoryDistribution.single { it.category == CarCategory.EURO }.spotCount)
    }

    @Test
    fun topBrandsUseSpotCountsThenAlphabeticalOrder() {
        val spots = listOf(
            spot("t1", "t1", "toyota", "Toyota", "2026-10-01T00:00:00Z", CarCategory.JDM, CarRarity.COMMON),
            spot("t2", "t2", "toyota", "Toyota", "2026-10-02T00:00:00Z", CarCategory.JDM, CarRarity.COMMON),
            spot("a1", "a1", "alfa", "Alfa Romeo", "2026-10-03T00:00:00Z", CarCategory.EURO, CarRarity.COMMON),
            spot("a2", "a2", "alfa", "Alfa Romeo", "2026-10-04T00:00:00Z", CarCategory.EURO, CarRarity.COMMON),
            spot("b1", "b1", "bmw", "BMW", "2026-10-05T00:00:00Z", CarCategory.EURO, CarRarity.COMMON),
        )

        val top = StatisticsCalculator.calculate(
            spots = spots,
            now = Instant.parse("2026-10-29T12:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        ).topBrands

        assertEquals(listOf("Alfa Romeo", "Toyota", "BMW"), top.map(BrandSpotCount::name))
        assertEquals(listOf(2, 2, 1), top.map(BrandSpotCount::spotCount))
    }

    private fun spot(
        id: String,
        modelId: String,
        brandId: String,
        brandName: String,
        timestamp: String,
        category: CarCategory,
        rarity: CarRarity,
    ) = SpotWithModel(
        spot = Spot(
            id = id,
            carModelId = modelId,
            photoPath = "photos/$id.jpg",
            photoSha256 = id,
            observedAt = Instant.parse(timestamp).toEpochMilli(),
            observedLocalDate = timestamp.take(10),
            observedLocalTime = "12:00",
            createdAt = 1L,
            color = null,
            notes = null,
            placeLabel = null,
        ),
        brandId = brandId,
        brandName = brandName,
        modelName = modelId,
        modelGeneration = null,
        modelCategory = category,
        modelRarity = rarity,
    )
}
