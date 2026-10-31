package com.streetspot.app.domain.progression

enum class Rank {
    NEWCOMER,
    STREET_OBSERVER,
    CAR_ENTHUSIAST,
    SPOTTER,
    STREET_HUNTER,
    CAR_COLLECTOR,
    RARE_HUNTER,
    STREET_LEGEND,
}

data class LevelDefinition(
    val level: Int,
    val minimumXp: Int,
    val rank: Rank,
)

data class LevelProgress(
    val level: Int,
    val rank: Rank,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpToNextLevel: Int,
    val progressFraction: Float,
    val isMaxLevel: Boolean,
)

object LevelCalculator {
    val levels: List<LevelDefinition> = (1..MAX_LEVEL).map { level ->
        LevelDefinition(
            level = level,
            minimumXp = if (level == 1) 0 else thresholdFor(level),
            rank = rankFor(level),
        )
    }

    fun calculate(totalXp: Int): LevelProgress {
        require(totalXp >= 0) { "Total XP cannot be negative" }
        val currentIndex = levels.indexOfLast { it.minimumXp <= totalXp }.coerceAtLeast(0)
        val current = levels[currentIndex]
        val next = levels.getOrNull(currentIndex + 1)
            ?: return LevelProgress(
                level = current.level,
                rank = current.rank,
                totalXp = totalXp,
                xpIntoLevel = totalXp - current.minimumXp,
                xpToNextLevel = 0,
                progressFraction = 1f,
                isMaxLevel = true,
            )
        val span = next.minimumXp - current.minimumXp
        val into = totalXp - current.minimumXp
        return LevelProgress(
            level = current.level,
            rank = current.rank,
            totalXp = totalXp,
            xpIntoLevel = into,
            xpToNextLevel = next.minimumXp - totalXp,
            progressFraction = (into.toFloat() / span).coerceIn(0f, 1f),
            isMaxLevel = false,
        )
    }

    fun thresholdFor(level: Int): Int {
        require(level in 1..MAX_LEVEL) { "Level must be in 1..$MAX_LEVEL" }
        if (level == 1) return 0
        val completedSteps = level - 1
        return 100 * completedSteps + 25 * (completedSteps - 1) * completedSteps / 2
    }

    private fun rankFor(level: Int): Rank = when (level) {
        1 -> Rank.NEWCOMER
        in 2..4 -> Rank.STREET_OBSERVER
        in 5..8 -> Rank.CAR_ENTHUSIAST
        in 9..12 -> Rank.SPOTTER
        in 13..16 -> Rank.STREET_HUNTER
        in 17..20 -> Rank.CAR_COLLECTOR
        in 21..24 -> Rank.RARE_HUNTER
        else -> Rank.STREET_LEGEND
    }

    private const val MAX_LEVEL = 30
}
