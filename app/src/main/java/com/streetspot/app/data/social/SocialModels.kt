package com.streetspot.app.data.social

data class SocialProfileSettings(
    val displayName: String = "",
    val shareLevelXp: Boolean = false,
    val shareAchievements: Boolean = false,
    val shareModels: Boolean = false,
    val sharePhotos: Boolean = false,
    val acceptFriendRequests: Boolean = false,
)

data class SocialProfile(
    val uid: String,
    val displayName: String,
    val shareLevelXp: Boolean,
    val level: Int?,
    val totalXp: Int?,
    val shareAchievements: Boolean,
    val achievementIds: List<String>,
    val shareModels: Boolean,
    val modelCount: Int?,
    val modelPreview: List<String>,
    val sharePhotos: Boolean,
)

data class SocialFriend(
    val profile: SocialProfile,
    val sharedSpots: List<SharedSpot>,
)

data class SharedSpot(
    val id: String,
    val brandName: String,
    val modelName: String,
    val generation: String?,
    val rarity: String,
    val vehicleYear: Int?,
)

data class FriendRequest(
    val id: String,
    val otherUid: String,
    val displayName: String,
    val incoming: Boolean,
    val status: String,
)

data class CommunitySnapshot(
    val settings: SocialProfileSettings,
    val friendCode: String?,
    val friends: List<SocialFriend>,
    val incomingRequests: List<FriendRequest>,
    val outgoingRequests: List<FriendRequest>,
    val ownSharedSpotIds: Set<String>,
    val isFromCache: Boolean = false,
    val checkedAtEpochMillis: Long = 0L,
)
