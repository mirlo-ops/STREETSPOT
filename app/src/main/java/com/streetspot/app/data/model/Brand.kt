package com.streetspot.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "brands",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["vehiclesDbId"], unique = true),
    ],
)
data class Brand(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val source: RecordSource,
    val createdAt: Long,
    val vehiclesDbId: String? = null,
    val aliases: String? = null,
)
