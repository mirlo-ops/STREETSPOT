package com.streetspot.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "xp_transactions",
    foreignKeys = [
        ForeignKey(
            entity = Spot::class,
            parentColumns = ["id"],
            childColumns = ["spotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["spotId"]),
        Index(value = ["spotId", "reason"], unique = true),
    ],
)
data class XpTransaction(
    @PrimaryKey val id: String,
    val spotId: String,
    val amount: Int,
    val reason: XpReason,
    val createdAt: Long,
)
