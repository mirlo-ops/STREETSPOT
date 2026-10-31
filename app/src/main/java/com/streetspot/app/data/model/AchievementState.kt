package com.streetspot.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievement_states")
data class AchievementState(
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long?,
)
