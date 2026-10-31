package com.streetspot.app.domain.progression

import com.streetspot.app.data.model.CarModelWithBrand
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.XpReason
import com.streetspot.app.data.model.XpTransaction

data class XpCalculation(
    val transactions: List<XpTransaction>,
) {
    val totalXp: Int = transactions.sumOf(XpTransaction::amount)
}

object XpCalculator {
    fun calculate(
        spots: List<Spot>,
        models: List<CarModelWithBrand>,
    ): XpCalculation {
        val modelsById = models.associateBy { it.model.id }
        val discoveredModels = mutableSetOf<String>()
        val discoveredBrands = mutableSetOf<String>()
        val repeatXpDates = mutableSetOf<ModelDate>()
        val exactPhotoObservations = mutableSetOf<ExactObservation>()
        val transactions = mutableListOf<XpTransaction>()

        spots.sortedWith(compareBy(Spot::observedAt, Spot::createdAt, Spot::id)).forEach { spot ->
            val model = checkNotNull(modelsById[spot.carModelId]) {
                "Spot ${spot.id} references missing model ${spot.carModelId}"
            }
            val exactObservation = ExactObservation(
                carModelId = spot.carModelId,
                localDate = spot.observedLocalDate,
                photoSha256 = spot.photoSha256,
            )
            if (!exactPhotoObservations.add(exactObservation)) return@forEach

            val awards = mutableListOf<Pair<XpReason, Int>>()
            if (discoveredModels.add(model.model.id)) {
                awards += XpReason.BASE_RARITY to model.model.rarity.baseXp()
                awards += XpReason.NEW_MODEL to NEW_MODEL_XP
            } else {
                val modelDate = ModelDate(model.model.id, spot.observedLocalDate)
                if (repeatXpDates.add(modelDate)) {
                    awards += XpReason.REPEAT_SPOT to REPEAT_SPOT_XP
                }
            }

            if (discoveredBrands.add(model.brand.id)) {
                awards += XpReason.NEW_BRAND to NEW_BRAND_XP
            }

            awards.forEach { (reason, amount) ->
                transactions += XpTransaction(
                    id = "${spot.id}_${reason.name}",
                    spotId = spot.id,
                    amount = amount,
                    reason = reason,
                    createdAt = spot.createdAt,
                )
            }
        }

        return XpCalculation(transactions)
    }

    private fun CarRarity.baseXp(): Int = when (this) {
        CarRarity.COMMON -> 10
        CarRarity.UNCOMMON -> 30
        CarRarity.SPORT -> 60
        CarRarity.RARE -> 120
        CarRarity.LEGENDARY -> 250
    }

    private data class ModelDate(val carModelId: String, val localDate: String)

    private data class ExactObservation(
        val carModelId: String,
        val localDate: String,
        val photoSha256: String,
    )

    private const val NEW_MODEL_XP = 40
    private const val NEW_BRAND_XP = 20
    private const val REPEAT_SPOT_XP = 5
}
