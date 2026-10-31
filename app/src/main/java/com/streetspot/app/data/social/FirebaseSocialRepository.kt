package com.streetspot.app.data.social

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.exifinterface.media.ExifInterface
import com.streetspot.app.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.SpotWithModel
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.domain.progression.LevelCalculator
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Optional Firebase layer. It is created only when the app has a Firebase configuration. */
class FirebaseSocialRepository private constructor(
    private val database: StreetSpotDatabase,
    private val photoStorage: PhotoStorage,
    val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabasePhotoStorage: SupabasePhotoStorage,
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified == true
    val isPhotoSharingConfigured: Boolean get() = supabasePhotoStorage.isConfigured

    suspend fun createAccount(email: String, password: String, displayName: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult().user
            ?: error("Firebase не вернул созданную учётную запись")
        ensureProfile(displayName.trim())
        user.sendEmailVerification().awaitResult()
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
        ensureProfile()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).awaitResult()
    }

    suspend fun sendVerificationEmail() {
        (auth.currentUser ?: error("Сначала войдите в аккаунт")).sendEmailVerification().awaitResult()
    }

    suspend fun refreshEmailVerification(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().awaitResult()
        user.getIdToken(true).awaitResult()
        return auth.currentUser?.isEmailVerified == true
    }

    fun signOut() = auth.signOut()

    suspend fun loadSnapshot(): CommunitySnapshot {
        val uid = requireSignedIn()
        ensureProfile()
        val profileRef = profile(uid)
        val ownProfile = profileRef.get().awaitResult()
        var isFromCache = ownProfile.metadata.isFromCache
        val settings = ownProfile.toSettings()
        val friendCode = if (settings.acceptFriendRequests) {
            val identity = profile(uid).collection(PRIVATE).document(IDENTITY_DOCUMENT).get().awaitResult()
            isFromCache = isFromCache || identity.metadata.isFromCache
            identity.getString("friendCode")
        } else null

        val edgeSnapshot = profileRef.collection(FRIEND_ACCESS).get().awaitResult()
        isFromCache = isFromCache || edgeSnapshot.metadata.isFromCache
        val edgeDocuments = edgeSnapshot.documents
        val friends = edgeDocuments.mapNotNull { edge ->
            val friendUid = edge.getString("friendUid") ?: edge.id
            if (friendUid == uid) return@mapNotNull null
            val friendDocument = profile(friendUid).get().awaitResult()
            isFromCache = isFromCache || friendDocument.metadata.isFromCache
            if (!friendDocument.exists()) return@mapNotNull null
            val friendProfile = friendDocument.toSocialProfile(friendUid)
            val sharedSpots = if (friendProfile.sharePhotos) {
                val sharedSpotSnapshot = profile(friendUid).collection(SHARED_SPOTS).get().awaitResult()
                isFromCache = isFromCache || sharedSpotSnapshot.metadata.isFromCache
                sharedSpotSnapshot.documents.mapNotNull { doc ->
                    doc.toSharedSpot()
                }
            } else emptyList()
            SocialFriend(friendProfile, sharedSpots)
        }.distinctBy { it.profile.uid }.sortedBy { it.profile.displayName.lowercase(Locale.ROOT) }

        val incomingSnapshot = firestore.collection(FRIEND_REQUESTS)
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", STATUS_PENDING)
            .get().awaitResult()
        isFromCache = isFromCache || incomingSnapshot.metadata.isFromCache
        val incoming = incomingSnapshot.documents.mapNotNull { doc ->
                FriendRequest(
                    id = doc.id,
                    otherUid = doc.getString("fromUid") ?: return@mapNotNull null,
                    displayName = doc.getString("fromDisplayName") ?: "STREETSPOT",
                    incoming = true,
                    status = STATUS_PENDING,
                )
            }
        val outgoingSnapshot = firestore.collection(FRIEND_REQUESTS)
            .whereEqualTo("fromUid", uid)
            .whereEqualTo("status", STATUS_PENDING)
            .get().awaitResult()
        isFromCache = isFromCache || outgoingSnapshot.metadata.isFromCache
        val outgoing = outgoingSnapshot.documents.mapNotNull { doc ->
                FriendRequest(
                    id = doc.id,
                    otherUid = doc.getString("toUid") ?: return@mapNotNull null,
                    displayName = doc.getString("toDisplayName") ?: "STREETSPOT",
                    incoming = false,
                    status = STATUS_PENDING,
                )
            }
        val ownSharedSpotsSnapshot = profileRef.collection(SHARED_SPOTS).get().awaitResult()
        isFromCache = isFromCache || ownSharedSpotsSnapshot.metadata.isFromCache
        val ownSharedSpots = ownSharedSpotsSnapshot.documents
            .mapTo(mutableSetOf()) { it.id }

        return CommunitySnapshot(
            settings = settings,
            friendCode = friendCode,
            friends = friends,
            incomingRequests = incoming,
            outgoingRequests = outgoing,
            ownSharedSpotIds = ownSharedSpots,
            isFromCache = isFromCache,
            checkedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    suspend fun saveProfile(settings: SocialProfileSettings) {
        val uid = requireSignedIn()
        val normalizedName = settings.displayName.trim().take(32).ifBlank { "StreetSpotter" }
        val oldSharePhotos = profile(uid).get().awaitResult().getBoolean("sharePhotos") == true
        val profileFields = mutableMapOf<String, Any>(
            "displayName" to normalizedName,
            "shareLevelXp" to settings.shareLevelXp,
            "shareAchievements" to settings.shareAchievements,
            "shareModels" to settings.shareModels,
            "sharePhotos" to settings.sharePhotos,
            "acceptFriendRequests" to settings.acceptFriendRequests,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        putOrDelete(profileFields, "level", settings.shareLevelXp)
        putOrDelete(profileFields, "totalXp", settings.shareLevelXp)
        putOrDelete(profileFields, "achievementIds", settings.shareAchievements)
        putOrDelete(profileFields, "modelCount", settings.shareModels)
        putOrDelete(profileFields, "modelPreview", settings.shareModels)
        addLocalProjection(profileFields, settings)
        profile(uid).set(profileFields, SetOptions.merge()).awaitResult()
        if (oldSharePhotos && !settings.sharePhotos) clearSharedPhotos(uid)
        if (settings.acceptFriendRequests) {
            refreshFriendCodeMapping(uid, normalizedName)
        } else {
            deleteFriendCodeMapping(uid)
        }
        syncLocalProjection()
    }

    /** Publishes only the explicitly selected summary fields; no local spot history is copied. */
    suspend fun syncLocalProjection() {
        val uid = currentUid ?: return
        ensureProfile()
        val ref = profile(uid)
        val document = ref.get().awaitResult()
        val settings = document.toSettings()
        val fields = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        addLocalProjection(fields, settings)
        ref.update(fields).awaitResult()
    }

    private suspend fun addLocalProjection(fields: MutableMap<String, Any>, settings: SocialProfileSettings) {
        if (settings.shareLevelXp) {
            val totalXp = database.xpTransactionDao().getTotalXp()
            fields["level"] = LevelCalculator.calculate(totalXp).level
            fields["totalXp"] = totalXp
        } else {
            fields["level"] = FieldValue.delete()
            fields["totalXp"] = FieldValue.delete()
        }
        if (settings.shareAchievements) {
            fields["achievementIds"] = database.achievementStateDao().getAll()
                .filter { it.unlockedAt != null }
                .map { it.achievementId }
        } else {
            fields["achievementIds"] = FieldValue.delete()
        }
        if (settings.shareModels) {
            val modelIds = database.spotDao().getAll().mapTo(linkedSetOf()) { it.carModelId }
            val preview = buildList {
                for (modelId in modelIds) {
                    val model = database.carModelDao().findWithBrand(modelId) ?: continue
                    add("${model.brand.name} ${model.model.name}".take(80))
                    if (size == MAX_MODEL_PREVIEW) break
                }
            }
            fields["modelCount"] = modelIds.size
            fields["modelPreview"] = preview
        } else {
            fields["modelCount"] = FieldValue.delete()
            fields["modelPreview"] = FieldValue.delete()
        }
    }

    suspend fun sendFriendRequest(code: String) {
        requireVerifiedEmail()
        val uid = requireSignedIn()
        val normalizedCode = code.trim().uppercase(Locale.ROOT)
        require(normalizedCode.matches(Regex("[A-Z2-9]{12}"))) { "Проверьте код STREETSPOT" }
        val targetDocument = firestore.collection(FRIEND_CODES).document(normalizedCode).get().awaitResult()
        val targetUid = targetDocument.getString("uid") ?: throw IOException("Код не найден или получение запросов выключено")
        val targetName = targetDocument.getString("displayName") ?: "STREETSPOT"
        require(targetUid != uid) { "Нельзя добавить собственный аккаунт" }

        val existingFriend = profile(uid).collection(FRIEND_ACCESS).document(targetUid).get().awaitResult()
        require(!existingFriend.exists()) { "Этот человек уже в друзьях" }
        val duplicate = firestore.collection(FRIEND_REQUESTS)
            .whereEqualTo("fromUid", uid)
            .whereEqualTo("toUid", targetUid)
            .whereEqualTo("status", STATUS_PENDING)
            .limit(1).get().awaitResult()
        require(duplicate.isEmpty) { "Запрос уже отправлен" }
        val reverse = firestore.collection(FRIEND_REQUESTS)
            .whereEqualTo("fromUid", targetUid)
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", STATUS_PENDING)
            .limit(1).get().awaitResult()
        require(reverse.isEmpty) { "У вас уже есть входящий запрос от этого человека" }

        val request = firestore.collection(FRIEND_REQUESTS).document(UUID.randomUUID().toString())
        request.set(
            mapOf(
                "fromUid" to uid,
                "toUid" to targetUid,
                "fromDisplayName" to (profile(uid).get().awaitResult().getString("displayName") ?: "STREETSPOT"),
                "toDisplayName" to targetName,
                "status" to STATUS_PENDING,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitResult()
    }

    suspend fun acceptRequest(requestId: String) {
        requireVerifiedEmail()
        val uid = requireSignedIn()
        val requestRef = firestore.collection(FRIEND_REQUESTS).document(requestId)
        val request = requestRef.get().awaitResult()
        require(request.getString("toUid") == uid && request.getString("status") == STATUS_PENDING) {
            "Запрос больше недоступен"
        }
        val friendUid = request.getString("fromUid") ?: error("В запросе отсутствует отправитель")
        val batch = firestore.batch()
        batch.update(requestRef, "status", STATUS_ACCEPTED, "updatedAt", FieldValue.serverTimestamp())
        batch.set(friendAccess(uid, friendUid), friendAccessData(friendUid, requestId))
        batch.set(friendAccess(friendUid, uid), friendAccessData(uid, requestId))
        batch.commit().awaitResult()
    }

    suspend fun declineRequest(requestId: String) {
        val uid = requireSignedIn()
        val ref = firestore.collection(FRIEND_REQUESTS).document(requestId)
        val request = ref.get().awaitResult()
        require(request.getString("toUid") == uid && request.getString("status") == STATUS_PENDING)
        ref.update("status", STATUS_DECLINED, "updatedAt", FieldValue.serverTimestamp()).awaitResult()
    }

    suspend fun cancelRequest(requestId: String) {
        val uid = requireSignedIn()
        val ref = firestore.collection(FRIEND_REQUESTS).document(requestId)
        val request = ref.get().awaitResult()
        require(request.getString("fromUid") == uid && request.getString("status") == STATUS_PENDING)
        ref.delete().awaitResult()
    }

    suspend fun removeFriend(friendUid: String, block: Boolean) {
        val uid = requireSignedIn()
        val ownEdge = friendAccess(uid, friendUid).get().awaitResult()
        val requestId = ownEdge.getString("requestId")
        val batch = firestore.batch()
        batch.delete(friendAccess(uid, friendUid))
        batch.delete(friendAccess(friendUid, uid))
        if (requestId != null) {
            val requestRef = firestore.collection(FRIEND_REQUESTS).document(requestId)
            val request = requestRef.get().awaitResult()
            if (request.exists() && request.getString("status") == STATUS_ACCEPTED) {
                batch.update(requestRef, "status", STATUS_REVOKED, "updatedAt", FieldValue.serverTimestamp())
            }
        }
        if (block) batch.set(profile(uid).collection(BLOCKED).document(friendUid), mapOf("createdAt" to FieldValue.serverTimestamp()))
        batch.commit().awaitResult()
    }

    suspend fun publishPhoto(
        spot: SpotWithModel,
        localFile: File,
        onProgress: (Float) -> Unit,
    ) {
        requireVerifiedEmail()
        val uid = requireSignedIn()
        require(localFile.isFile && localFile.length() in 1..MAX_PHOTO_BYTES) {
            "Фото должно быть JPEG и занимать не больше 5 МБ"
        }
        requireGpsWasStripped(localFile)
        val userProfile = profile(uid).get().awaitResult()
        require(userProfile.getBoolean("sharePhotos") == true) { "Сначала включите публикацию фотографий в настройках профиля" }

        val idToken = firebaseIdToken(forceRefresh = true)
        try {
            supabasePhotoStorage.upload(uid, spot.spot.id, localFile, idToken, onProgress)
            profile(uid).collection(SHARED_SPOTS).document(spot.spot.id).set(
                mapOf(
                    "brandName" to spot.brandName,
                    "modelName" to spot.modelName,
                    "generation" to spot.modelGeneration,
                    "rarity" to spot.modelRarity.name,
                    "vehicleYear" to spot.spot.vehicleYear,
                    "publishedAt" to FieldValue.serverTimestamp(),
                ),
            ).awaitResult()
            onProgress(1f)
        } catch (error: Exception) {
            runCatching { supabasePhotoStorage.delete(uid, spot.spot.id, firebaseIdToken()) }
            throw error
        }
    }

    suspend fun unpublishPhoto(spotId: String) {
        val uid = requireSignedIn()
        supabasePhotoStorage.delete(uid, spotId, firebaseIdToken())
        profile(uid).collection(SHARED_SPOTS).document(spotId).delete().awaitResult()
    }

    suspend fun downloadSharedPhoto(uid: String, spotId: String): File {
        return supabasePhotoStorage.download(uid, spotId, firebaseIdToken())
    }

    suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: error("Сначала войдите в аккаунт")
        val email = user.email ?: error("Для удаления нужен аккаунт с адресом email")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).awaitResult()
        deleteCloudData(user.uid)
        user.delete().awaitResult()
    }

    private suspend fun deleteCloudData(uid: String) {
        val ownProfile = profile(uid)
        val edges = ownProfile.collection(FRIEND_ACCESS).get().awaitResult().documents
        edges.chunked(150).forEach { edgeChunk ->
            val batch = firestore.batch()
            edgeChunk.forEach { edge ->
                val friendUid = edge.getString("friendUid") ?: edge.id
                val requestId = edge.getString("requestId")
                batch.delete(friendAccess(uid, friendUid))
                batch.delete(friendAccess(friendUid, uid))
                if (requestId != null) batch.delete(firestore.collection(FRIEND_REQUESTS).document(requestId))
            }
            batch.commit().awaitResult()
        }

        deleteQuery(firestore.collection(FRIEND_REQUESTS).whereEqualTo("fromUid", uid))
        deleteQuery(firestore.collection(FRIEND_REQUESTS).whereEqualTo("toUid", uid))
        deleteSharedPhotos(uid)
        deleteSubcollection(ownProfile.collection(BLOCKED))
        deleteSubcollection(ownProfile.collection(FRIEND_ACCESS))

        val identityRef = ownProfile.collection(PRIVATE).document(IDENTITY_DOCUMENT)
        val friendCode = identityRef.get().awaitResult().getString("friendCode")
        if (friendCode != null) firestore.collection(FRIEND_CODES).document(friendCode).delete().awaitResult()
        identityRef.delete().awaitResult()

        ownProfile.delete().awaitResult()
    }

    private suspend fun deleteQuery(query: Query) {
        while (true) {
            val docs = query.limit(350).get().awaitResult().documents
            if (docs.isEmpty()) return
            val batch = firestore.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().awaitResult()
        }
    }

    private suspend fun deleteSubcollection(collection: com.google.firebase.firestore.CollectionReference) {
        while (true) {
            val docs = collection.limit(350).get().awaitResult().documents
            if (docs.isEmpty()) return
            val batch = firestore.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().awaitResult()
        }
    }

    private suspend fun ensureProfile(displayName: String? = null) {
        val uid = requireSignedIn()
        val ref = profile(uid)
        if (!ref.get().awaitResult().exists()) {
            val name = displayName?.trim()?.take(32)?.ifBlank { null } ?: "StreetSpotter"
            ref.set(
                mapOf(
                    "displayName" to name,
                    "shareLevelXp" to false,
                    "shareAchievements" to false,
                    "shareModels" to false,
                    "sharePhotos" to false,
                    "acceptFriendRequests" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).awaitResult()
        }
    }

    private suspend fun getOrCreateFriendCode(uid: String, displayName: String): String {
        val identityRef = profile(uid).collection(PRIVATE).document(IDENTITY_DOCUMENT)
        val existing = identityRef.get().awaitResult().getString("friendCode")
        if (existing != null) {
            refreshFriendCodeMapping(uid, displayName, existing)
            return existing
        }
        repeat(6) {
            val candidate = newFriendCode()
            val batch = firestore.batch()
            batch.set(firestore.collection(FRIEND_CODES).document(candidate), mapOf("uid" to uid, "displayName" to displayName))
            batch.set(identityRef, mapOf("friendCode" to candidate))
            try {
                batch.commit().awaitResult()
                return candidate
            } catch (error: Exception) {
                if (error !is com.google.firebase.firestore.FirebaseFirestoreException ||
                    error.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) throw error
            }
        }
        throw IOException("Не удалось создать код. Повторите попытку позже.")
    }

    private suspend fun refreshFriendCodeMapping(uid: String, displayName: String, code: String? = null) {
        val actualCode = code ?: profile(uid).collection(PRIVATE).document(IDENTITY_DOCUMENT)
            .get().awaitResult().getString("friendCode")
            ?: getOrCreateFriendCode(uid, displayName)
        firestore.collection(FRIEND_CODES).document(actualCode)
            .set(mapOf("uid" to uid, "displayName" to displayName), SetOptions.merge()).awaitResult()
    }

    private suspend fun deleteFriendCodeMapping(uid: String) {
        val identityRef = profile(uid).collection(PRIVATE).document(IDENTITY_DOCUMENT)
        val code = identityRef.get().awaitResult().getString("friendCode") ?: return
        firestore.collection(FRIEND_CODES).document(code).delete().awaitResult()
    }

    private fun friendAccess(ownerUid: String, friendUid: String) =
        profile(ownerUid).collection(FRIEND_ACCESS).document(friendUid)

    private fun friendAccessData(friendUid: String, requestId: String) = mapOf(
        "friendUid" to friendUid,
        "requestId" to requestId,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    private fun profile(uid: String) = firestore.collection(PROFILES).document(uid)

    private suspend fun firebaseIdToken(forceRefresh: Boolean = false): String =
        auth.currentUser?.getIdToken(forceRefresh)?.awaitResult()?.token
            ?: error("Не удалось получить токен Firebase")

    private fun requireSignedIn(): String = auth.currentUser?.uid ?: error("Войдите в аккаунт STREETSPOT")

    private fun requireVerifiedEmail() {
        check(auth.currentUser?.isEmailVerified == true) { "Подтвердите email, чтобы пользоваться функциями друзей и фото" }
    }

    private fun putOrDelete(fields: MutableMap<String, Any>, field: String, enabled: Boolean) {
        if (!enabled) fields[field] = FieldValue.delete()
    }

    private suspend fun clearSharedPhotos(uid: String) {
        val collection = profile(uid).collection(SHARED_SPOTS)
        if (supabasePhotoStorage.isConfigured || !collection.limit(1).get().awaitResult().isEmpty) {
            supabasePhotoStorage.delete(uid, null, firebaseIdToken())
        }
        deleteSubcollection(collection)
    }

    private suspend fun deleteSharedPhotos(uid: String) = clearSharedPhotos(uid)

    private fun com.google.firebase.firestore.DocumentSnapshot.toSettings() = SocialProfileSettings(
        displayName = getString("displayName") ?: "StreetSpotter",
        shareLevelXp = getBoolean("shareLevelXp") == true,
        shareAchievements = getBoolean("shareAchievements") == true,
        shareModels = getBoolean("shareModels") == true,
        sharePhotos = getBoolean("sharePhotos") == true,
        acceptFriendRequests = getBoolean("acceptFriendRequests") == true,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toSocialProfile(uid: String) = SocialProfile(
        uid = uid,
        displayName = getString("displayName") ?: "STREETSPOT",
        shareLevelXp = getBoolean("shareLevelXp") == true,
        level = getLong("level")?.toInt(),
        totalXp = getLong("totalXp")?.toInt(),
        shareAchievements = getBoolean("shareAchievements") == true,
        achievementIds = get("achievementIds") as? List<String> ?: emptyList(),
        shareModels = getBoolean("shareModels") == true,
        modelCount = getLong("modelCount")?.toInt(),
        modelPreview = get("modelPreview") as? List<String> ?: emptyList(),
        sharePhotos = getBoolean("sharePhotos") == true,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toSharedSpot(): SharedSpot? {
        val brand = getString("brandName") ?: return null
        val model = getString("modelName") ?: return null
        return SharedSpot(
            id = id,
            brandName = brand,
            modelName = model,
            generation = getString("generation"),
            rarity = getString("rarity") ?: "COMMON",
            vehicleYear = getLong("vehicleYear")?.toInt(),
        )
    }

    private fun requireGpsWasStripped(file: File) {
        val exif = ExifInterface(file.absolutePath)
        val gpsTags = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
        )
        check(gpsTags.none { exif.getAttribute(it) != null }) { "В фото остались GPS-метаданные; публикация отменена" }
    }

    private fun newFriendCode(): String = buildString(FRIEND_CODE_LENGTH) {
        repeat(FRIEND_CODE_LENGTH) { append(FRIEND_CODE_ALPHABET[random.nextInt(FRIEND_CODE_ALPHABET.length)]) }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IOException("Firebase-операция завершилась ошибкой"))
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }

    companion object {
        private const val PROFILES = "profiles"
        private const val FRIEND_CODES = "friendCodes"
        private const val FRIEND_REQUESTS = "friendRequests"
        private const val FRIEND_ACCESS = "friendAccess"
        private const val SHARED_SPOTS = "sharedSpots"
        private const val BLOCKED = "blocked"
        private const val PRIVATE = "private"
        private const val IDENTITY_DOCUMENT = "identity"
        private const val STATUS_PENDING = "PENDING"
        private const val STATUS_ACCEPTED = "ACCEPTED"
        private const val STATUS_DECLINED = "DECLINED"
        private const val STATUS_REVOKED = "REVOKED"
        private const val MAX_PHOTO_BYTES = 5L * 1024L * 1024L
        private const val MAX_MODEL_PREVIEW = 25
        private const val FRIEND_CODE_LENGTH = 12
        private const val FRIEND_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun createOrNull(
            context: Context,
            database: StreetSpotDatabase,
            photoStorage: PhotoStorage,
        ): FirebaseSocialRepository? {
            val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val app = FirebaseApp.getApps(context).firstOrNull() ?: run {
                if (!isDebuggable) return null
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId("1:123456789000:android:streetspot-emulator")
                        .setApiKey("demo-api-key-for-streetspot-emulator")
                        .setProjectId(DEMO_PROJECT_ID)
                        .build(),
                )
            } ?: return null
            val auth = FirebaseAuth.getInstance(app)
            val firestore = FirebaseFirestore.getInstance(app)
            val supabasePhotoStorage = SupabasePhotoStorage(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                context.cacheDir,
            )
            if (isDebuggable && app.options.projectId == DEMO_PROJECT_ID) {
                auth.useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT)
                firestore.useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
            }
            return FirebaseSocialRepository(
                database,
                photoStorage,
                auth,
                firestore,
                supabasePhotoStorage,
            )
        }

        private const val DEMO_PROJECT_ID = "demo-streetspot"
        private const val EMULATOR_HOST = "127.0.0.1"
        private const val AUTH_EMULATOR_PORT = 9099
        private const val FIRESTORE_EMULATOR_PORT = 8080
    }
}
