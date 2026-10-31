package com.streetspot.app.domain.progression

import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.SpotWithModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCheckerTest {
    @Test
    fun nightRiderUsesInclusiveStartAndExclusiveEndTimes() {
        val spots = listOf("19:59", "20:00", "05:59", "06:00").mapIndexed { index, time ->
            spotWithModel("spot-$index", "model-$index", "brand", time, CarCategory.OTHER, CarRarity.COMMON)
        }

        val achievements = AchievementChecker.calculate(spots).associateBy(AchievementProgress::id)

        assertEquals(2, achievements.getValue(AchievementId.NIGHT_RIDER).progress)
        assertFalse(achievements.getValue(AchievementId.NIGHT_RIDER).isUnlocked)
    }

    @Test
    fun modelAndBrandAchievementsCountUniqueEntitiesWhileHundredSpotsCountsRecords() {
        val spots = (0 until 100).map { index ->
            spotWithModel(
                id = "spot-$index",
                modelId = "single-model",
                brandId = "single-brand",
                localTime = "12:00",
                category = CarCategory.JDM,
                rarity = CarRarity.LEGENDARY,
            )
        }

        val achievements = AchievementChecker.calculate(spots).associateBy(AchievementProgress::id)

        assertEquals(1, achievements.getValue(AchievementId.FIRST_SPOT).progress)
        assertTrue(achievements.getValue(AchievementId.FIRST_SPOT).isUnlocked)
        assertEquals(1, achievements.getValue(AchievementId.JDM_HUNTER).progress)
        assertEquals(1, achievements.getValue(AchievementId.RARE_COLLECTOR).progress)
        assertEquals(1, achievements.getValue(AchievementId.BRAND_HUNTER).progress)
        assertTrue(achievements.getValue(AchievementId.SPOTS_100).isUnlocked)
    }

    @Test
    fun nextAchievementUsesFixedOrderWhenProgressIsTiedAndReturnsNullWhenComplete() {
        val tie = listOf(
            AchievementProgress(AchievementId.JDM_HUNTER, progress = 5, target = 10, isUnlocked = false),
            AchievementProgress(AchievementId.FIRST_SPOT, progress = 1, target = 2, isUnlocked = false),
        )
        assertEquals(AchievementId.FIRST_SPOT, AchievementChecker.next(tie)?.id)

        val complete = AchievementChecker.idsInDisplayOrder.map { id ->
            AchievementProgress(id, progress = 1, target = 1, isUnlocked = true)
        }
        assertNull(AchievementChecker.next(complete))
    }

    private fun spotWithModel(
        id: String,
        modelId: String,
        brandId: String,
        localTime: String,
        category: CarCategory,
        rarity: CarRarity,
    ) = SpotWithModel(
        spot = Spot(
            id = id,
            carModelId = modelId,
            photoPath = "photos/$id.jpg",
            photoSha256 = id,
            observedAt = id.hashCode().toLong(),
            observedLocalDate = "2026-10-29",
            observedLocalTime = localTime,
            createdAt = 1L,
            color = null,
            notes = null,
            placeLabel = null,
        ),
        brandId = brandId,
        brandName = "Марка",
        modelName = "Модель",
        modelGeneration = null,
        modelCategory = category,
        modelRarity = rarity,
    )
}
