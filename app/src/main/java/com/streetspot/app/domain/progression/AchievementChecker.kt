package com.streetspot.app.domain.progression

import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.SpotWithModel
import java.time.LocalTime

enum class AchievementId(val storageId: String) {
    FIRST_SPOT("FIRST_SPOT"),
    JDM_HUNTER("JDM_HUNTER"),
    NIGHT_RIDER("NIGHT_RIDER"),
    RARE_COLLECTOR("RARE_COLLECTOR"),
    BRAND_HUNTER("BRAND_HUNTER"),
    SPOTS_100("100_SPOTS");
}

data class AchievementProgress(
    val id: AchievementId,
    val progress: Int,
    val target: Int,
    val isUnlocked: Boolean,
    val unlockedAt: Long? = null,
) {
    val completionFraction: Float
        get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

object AchievementChecker {
    val idsInDisplayOrder = listOf(
        AchievementId.FIRST_SPOT,
        AchievementId.JDM_HUNTER,
        AchievementId.NIGHT_RIDER,
        AchievementId.RARE_COLLECTOR,
        AchievementId.BRAND_HUNTER,
        AchievementId.SPOTS_100,
    )

    fun calculate(spots: List<SpotWithModel>): List<AchievementProgress> {
        val counts = mapOf(
            AchievementId.FIRST_SPOT to spots.size,
            AchievementId.JDM_HUNTER to spots.asSequence()
                .filter { it.modelCategory == CarCategory.JDM }
                .map { it.spot.carModelId }
                .distinct()
                .count(),
            AchievementId.NIGHT_RIDER to spots.count { isNightTime(it.spot.observedLocalTime) },
            AchievementId.RARE_COLLECTOR to spots.asSequence()
                .filter { it.modelRarity == CarRarity.RARE || it.modelRarity == CarRarity.LEGENDARY }
                .map { it.spot.carModelId }
                .distinct()
                .count(),
            AchievementId.BRAND_HUNTER to spots.map(SpotWithModel::brandId).distinct().size,
            AchievementId.SPOTS_100 to spots.size,
        )

        return idsInDisplayOrder.map { id ->
            val target = id.target()
            val actual = counts.getValue(id)
            AchievementProgress(
                id = id,
                progress = actual.coerceAtMost(target),
                target = target,
                isUnlocked = actual >= target,
            )
        }
    }

    fun next(progress: List<AchievementProgress>): AchievementProgress? {
        val byId = progress.associateBy(AchievementProgress::id)
        var best: AchievementProgress? = null
        idsInDisplayOrder.mapNotNull(byId::get).forEach { achievement ->
            if (achievement.isUnlocked) return@forEach
            val currentBest = best
            if (currentBest == null || achievement.completionFraction > currentBest.completionFraction) {
                best = achievement
            }
        }
        return best
    }

    private fun isNightTime(localTime: String): Boolean {
        val time = runCatching { LocalTime.parse(localTime) }.getOrNull() ?: return false
        return time >= NIGHT_START || time < DAY_START
    }

    private fun AchievementId.target(): Int = when (this) {
        AchievementId.FIRST_SPOT -> 1
        AchievementId.JDM_HUNTER -> 10
        AchievementId.NIGHT_RIDER -> 5
        AchievementId.RARE_COLLECTOR -> 5
        AchievementId.BRAND_HUNTER -> 20
        AchievementId.SPOTS_100 -> 100
    }

    private val NIGHT_START = LocalTime.of(20, 0)
    private val DAY_START = LocalTime.of(6, 0)
}
