package com.streetspot.app.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCalculatorTest {
    @Test
    fun thresholdsMatchDocumentedLevelTable() {
        val documented = listOf(
            0, 100, 225, 375, 550, 750, 975, 1_225, 1_500, 1_800,
            2_125, 2_475, 2_850, 3_250, 3_675, 4_125, 4_600, 5_100,
            5_625, 6_175, 6_750, 7_350, 7_975, 8_625, 9_300, 10_000,
            10_725, 11_475, 12_250, 13_050,
        )

        assertEquals(documented, LevelCalculator.levels.map(LevelDefinition::minimumXp))
    }

    @Test
    fun levelBoundariesAndProgressAreCalculatedCorrectly() {
        val beforeLevelTwo = LevelCalculator.calculate(99)
        assertEquals(1, beforeLevelTwo.level)
        assertEquals(99, beforeLevelTwo.xpIntoLevel)
        assertEquals(1, beforeLevelTwo.xpToNextLevel)
        assertEquals(0.99f, beforeLevelTwo.progressFraction, 0.001f)

        val atLevelTwo = LevelCalculator.calculate(100)
        assertEquals(2, atLevelTwo.level)
        assertEquals(Rank.STREET_OBSERVER, atLevelTwo.rank)
        assertEquals(0, atLevelTwo.xpIntoLevel)
        assertEquals(125, atLevelTwo.xpToNextLevel)

        val atLevelThirty = LevelCalculator.calculate(13_050)
        assertEquals(30, atLevelThirty.level)
        assertEquals(Rank.STREET_LEGEND, atLevelThirty.rank)
        assertTrue(atLevelThirty.isMaxLevel)
        assertEquals(1f, atLevelThirty.progressFraction, 0f)

        val afterLevelThirty = LevelCalculator.calculate(13_500)
        assertEquals(30, afterLevelThirty.level)
        assertEquals(450, afterLevelThirty.xpIntoLevel)
        assertEquals(0, afterLevelThirty.xpToNextLevel)
        assertTrue(afterLevelThirty.isMaxLevel)
    }
}
