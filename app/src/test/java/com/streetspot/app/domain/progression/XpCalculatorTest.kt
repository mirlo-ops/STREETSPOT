package com.streetspot.app.domain.progression

import com.streetspot.app.data.model.Brand
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.RecordSource
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.XpReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpCalculatorTest {
    @Test
    fun firstDiscoveryAwardsBaseNewModelAndNewBrandByRarity() {
        val brand = brand("shared-brand", "Mazda")
        val rarities = listOf(
            CarRarity.COMMON to 10,
            CarRarity.UNCOMMON to 30,
            CarRarity.SPORT to 60,
            CarRarity.RARE to 120,
            CarRarity.LEGENDARY to 250,
        )
        val models = rarities.mapIndexed { index, (rarity, _) ->
            modelWithBrand("model-$index", "model-$index", rarity, brand)
        }
        val spots = models.mapIndexed { index, model ->
            spot("spot-$index", model.model.id, index.toLong() + 1L, "hash-$index")
        }

        val calculation = XpCalculator.calculate(spots, models)
        val baseXp = calculation.transactions
            .filter { it.reason == XpReason.BASE_RARITY }
            .associate { it.spotId to it.amount }
        val newModelXp = calculation.transactions.filter { it.reason == XpReason.NEW_MODEL }
        val newBrandXp = calculation.transactions.filter { it.reason == XpReason.NEW_BRAND }

        rarities.forEachIndexed { index, (_, amount) -> assertEquals(amount, baseXp["spot-$index"]) }
        rarities.forEachIndexed { index, (rarity, amount) ->
            val expected = amount + 40 + if (index == 0) 20 else 0
            assertEquals("Unexpected total for $rarity", expected, calculation.transactions
                .filter { it.spotId == "spot-$index" }
                .sumOf { it.amount })
        }
        assertEquals(5, newModelXp.size)
        assertTrue(newModelXp.all { it.amount == 40 })
        assertEquals(1, newBrandXp.size)
        assertEquals(20, newBrandXp.single().amount)
        assertEquals(690, calculation.totalXp)
    }

    @Test
    fun repeatXpIsLimitedPerModelAndLocalDateAndExactDuplicatesGetNone() {
        val brand = brand("brand", "Honda")
        val model = modelWithBrand("model", "Civic", CarRarity.COMMON, brand)
        val spots = listOf(
            spot("first", model.model.id, 1L, "same-photo", date = "2026-01-01"),
            spot("exact-copy", model.model.id, 2L, "same-photo", date = "2026-01-01"),
            spot("repeat-one", model.model.id, 3L, "different-photo-1", date = "2026-01-01"),
            spot("repeat-limit", model.model.id, 4L, "different-photo-2", date = "2026-01-01"),
            spot("next-day", model.model.id, 5L, "different-photo-3", date = "2026-01-02"),
        )

        val calculation = XpCalculator.calculate(spots, listOf(model))
        val repeatSpots = calculation.transactions.filter { it.reason == XpReason.REPEAT_SPOT }

        assertEquals(listOf("repeat-one", "next-day"), repeatSpots.map { it.spotId })
        assertEquals(listOf(5, 5), repeatSpots.map { it.amount })
        assertTrue(calculation.transactions.none { it.spotId == "exact-copy" })
        assertEquals(80, calculation.totalXp)
    }

    @Test
    fun calculationIsIndependentOfInputOrderAndUsesStableTransactionIds() {
        val brand = brand("brand", "Toyota")
        val model = modelWithBrand("model", "Supra", CarRarity.RARE, brand)
        val spots = listOf(
            spot("later", model.model.id, 2L, "b"),
            spot("earlier", model.model.id, 1L, "a"),
        )

        val first = XpCalculator.calculate(spots, listOf(model)).transactions
        val second = XpCalculator.calculate(spots.reversed(), listOf(model)).transactions

        assertEquals(first, second)
        assertTrue(first.all { it.id == "${it.spotId}_${it.reason.name}" })
    }

    private fun brand(id: String, name: String) = Brand(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        source = RecordSource.DEFAULT,
        createdAt = 1L,
    )

    private fun modelWithBrand(
        id: String,
        name: String,
        rarity: CarRarity,
        brand: Brand,
    ) = CarModelWithBrand(
        model = CarModel(
            id = id,
            brandId = brand.id,
            name = name,
            normalizedName = name.lowercase(),
            generation = null,
            normalizedGeneration = "",
            countryCode = null,
            category = CarCategory.OTHER,
            rarity = rarity,
            source = RecordSource.DEFAULT,
            createdAt = 1L,
            updatedAt = 1L,
        ),
        brand = brand,
    )

    private fun spot(
        id: String,
        modelId: String,
        timestamp: Long,
        hash: String,
        date: String = "2026-01-01",
    ) = Spot(
        id = id,
        carModelId = modelId,
        photoPath = "photos/$id.jpg",
        photoSha256 = hash,
        observedAt = timestamp,
        observedLocalDate = date,
        observedLocalTime = "12:00",
        createdAt = timestamp,
        color = null,
        notes = null,
        placeLabel = null,
    )
}
