package com.streetspot.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "spots",
    foreignKeys = [
        ForeignKey(
            entity = CarModel::class,
            parentColumns = ["id"],
            childColumns = ["carModelId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["carModelId", "observedAt"]),
        Index(value = ["carModelId", "observedLocalDate", "photoSha256"]),
    ],
)
data class Spot(
    @PrimaryKey val id: String,
    val carModelId: String,
    val photoPath: String,
    val photoSha256: String,
    val observedAt: Long,
    val observedLocalDate: String,
    val observedLocalTime: String,
    val createdAt: Long,
    val color: String?,
    val notes: String?,
    val placeLabel: String?,
    val vehicleYear: Int? = null,
    val bodyStyle: CarBodyStyle? = null,
)
