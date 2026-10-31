package com.streetspot.app.domain.statistics

import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.SpotWithModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class BrandSpotCount(
    val brandId: String,
    val name: String,
    val spotCount: Int,
)

data class CategorySpotCount(
    val category: CarCategory,
    val spotCount: Int,
)

data class StatisticsSnapshot(
    val month: YearMonth,
    val totalSpots: Int,
    val uniqueModels: Int,
    val brandsDiscovered: Int,
    val rareFinds: Int,
    val spotsThisMonth: Int,
    val topBrands: List<BrandSpotCount>,
    val categoryDistribution: List<CategorySpotCount>,
)

object StatisticsCalculator {
    fun calculate(
        spots: List<SpotWithModel>,
        now: Instant,
        zoneId: ZoneId,
    ): StatisticsSnapshot {
        val currentMonth = YearMonth.from(now.atZone(zoneId))
        val monthStart = currentMonth.atDay(1).atStartOfDay(zoneId).toInstant()
        val monthCount = spots.count { spot ->
            val observedAt = Instant.ofEpochMilli(spot.spot.observedAt)
            observedAt >= monthStart && observedAt <= now
        }
        val brandCounts = spots.groupingBy { it.brandId to it.brandName }.eachCount()
            .map { (identity, count) ->
                BrandSpotCount(brandId = identity.first, name = identity.second, spotCount = count)
            }
            .sortedWith(
                compareByDescending<BrandSpotCount>(BrandSpotCount::spotCount)
                    .thenBy { it.name.lowercase(Locale.ROOT) }
                    .thenBy(BrandSpotCount::brandId),
            )
            .take(TOP_BRANDS_LIMIT)

        val rareModels = spots.asSequence()
            .filter { it.modelRarity == CarRarity.RARE || it.modelRarity == CarRarity.LEGENDARY }
            .map { it.spot.carModelId }
            .distinct()
            .count()

        val categories = CarCategory.entries.map { category ->
            CategorySpotCount(
                category = category,
                spotCount = spots.count { it.modelCategory == category },
            )
        }

        return StatisticsSnapshot(
            month = currentMonth,
            totalSpots = spots.size,
            uniqueModels = spots.map { it.spot.carModelId }.distinct().size,
            brandsDiscovered = spots.map(SpotWithModel::brandId).distinct().size,
            rareFinds = rareModels,
            spotsThisMonth = monthCount,
            topBrands = brandCounts,
            categoryDistribution = categories,
        )
    }

    private const val TOP_BRANDS_LIMIT = 5
}
