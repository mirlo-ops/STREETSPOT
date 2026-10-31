package com.streetspot.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.AchievementState
import com.streetspot.app.data.model.Brand
import com.streetspot.app.data.model.CarBodyStyle
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.CatalogMetadataCodec
import com.streetspot.app.data.model.RecordSource
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.data.repository.ProgressRepository
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class BackupResult(
    val spots: Int,
    val missingPhotos: Int,
)

class BackupFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)

private data class BackupSpot(
    val spot: Spot,
    val photoEntry: String?,
)

private data class BackupSnapshot(
    val brands: List<Brand>,
    val models: List<CarModel>,
    val spots: List<Spot>,
    val achievements: List<AchievementState>,
)

private data class ParsedBackup(
    val brands: List<Brand>,
    val models: List<CarModel>,
    val spots: List<BackupSpot>,
    val achievements: List<AchievementState>,
    val stagedPhotos: Map<String, File>,
    val missingPhotos: Int,
)

class BackupRepository(
    context: Context,
    private val database: StreetSpotDatabase,
    private val progressRepository: ProgressRepository,
    private val photoStorage: PhotoStorage,
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    suspend fun export(destination: Uri): BackupResult {
        val snapshot = database.withTransaction {
            BackupSnapshot(
                brands = database.brandDao().getAll(),
                models = database.carModelDao().getAll(),
                spots = database.spotDao().getAll(),
                achievements = database.achievementStateDao().getAll(),
            )
        }
        val temporaryArchive = File(appContext.cacheDir, "streetspot-export-${UUID.randomUUID()}.zip")
        try {
            var missingPhotos = 0
            val availablePhotos = linkedMapOf<String, File>()
            snapshot.spots.forEach { spot ->
                val image = photoStorage.findStoredPhoto(spot.photoPath)
                if (image == null) {
                    missingPhotos++
                } else {
                    if (sha256(image) != spot.photoSha256) {
                        throw IOException("A stored photo does not match its record")
                    }
                    availablePhotos[spot.id] = image
                }
            }

            val photoEntries = availablePhotos.keys.associateWith(::photoEntry)
            val dataBytes = encodeData(snapshot, photoEntries).toByteArray(Charsets.UTF_8)
            if (dataBytes.size > MAX_DATA_BYTES) {
                throw IOException("The local collection is too large to export")
            }
            val manifestBytes = encodeManifest(
                appVersion = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty(),
                dataSha256 = sha256(dataBytes),
                snapshot = snapshot,
                missingPhotos = missingPhotos,
            ).toByteArray(Charsets.UTF_8)

            ZipOutputStream(
                BufferedOutputStream(LimitedOutputStream(FileOutputStream(temporaryArchive), MAX_ARCHIVE_BYTES)),
            ).use { zip ->
                zip.writeEntry("manifest.json", manifestBytes)
                zip.writeEntry("data.json", dataBytes)
                availablePhotos.forEach { (spotId, image) ->
                    if (image.length() > MAX_PHOTO_BYTES) {
                        throw IOException("A stored photo is larger than the backup limit")
                    }
                    image.inputStream().buffered().use { input ->
                        zip.putNextEntry(ZipEntry(photoEntry(spotId)))
                        copyLimited(input, zip, MAX_PHOTO_BYTES)
                        zip.closeEntry()
                    }
                }
            }

            val output = contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("The selected backup file could not be opened")
            output.buffered().use { target -> temporaryArchive.inputStream().buffered().use { it.copyTo(target) } }
            return BackupResult(snapshot.spots.size, missingPhotos)
        } finally {
            temporaryArchive.delete()
        }
    }

    suspend fun restore(source: Uri): BackupResult {
        val token = UUID.randomUUID().toString()
        val temporaryArchive = File(appContext.cacheDir, "streetspot-import-$token.zip")
        val stagingDirectory = File(appContext.cacheDir, "streetspot-import-$token")
        val importedPhotoPaths = mutableListOf<String>()
        var databaseCommitted = false
        try {
            val input = contentResolver.openInputStream(source)
                ?: throw BackupFormatException("The selected backup could not be opened")
            input.buffered().use { inputStream ->
                FileOutputStream(temporaryArchive).buffered().use { output ->
                    copyLimited(inputStream, output, MAX_ARCHIVE_BYTES)
                }
            }
            if (!stagingDirectory.mkdirs() && !stagingDirectory.isDirectory) {
                throw IOException("Temporary restore storage is unavailable")
            }
            val backup = parseArchive(temporaryArchive, stagingDirectory)
            val restoreToken = token.replace("-", "")
            val restoredSpots = backup.spots.map { backupSpot ->
                val image = backup.stagedPhotos[backupSpot.spot.id]
                if (image == null) {
                    backupSpot.spot.copy(
                        photoPath = "photos/missing-$restoreToken-${backupSpot.spot.id}.jpg",
                    )
                } else {
                    val stored = photoStorage.importBackupPhoto(
                        source = image,
                        spotId = backupSpot.spot.id,
                        restoreToken = restoreToken,
                    )
                    importedPhotoPaths += stored.relativePath
                    if (stored.sha256 != backupSpot.spot.photoSha256) {
                        throw BackupFormatException("A restored photo does not match its saved record")
                    }
                    backupSpot.spot.copy(photoPath = stored.relativePath)
                }
            }

            val oldPaths = database.withTransaction {
                val previousPaths = database.spotDao().getAllPhotoPaths().toSet()
                database.xpTransactionDao().deleteAll()
                database.spotDao().deleteAll()
                database.carModelDao().deleteAll()
                database.brandDao().deleteAll()
                database.achievementStateDao().deleteAll()

                database.brandDao().insertAll(backup.brands)
                database.carModelDao().insertAll(backup.models)
                database.spotDao().insertAll(restoredSpots)
                database.achievementStateDao().putAll(backup.achievements)
                progressRepository.recalculateWithinTransaction()
                previousPaths
            }
            databaseCommitted = true

            val newPaths = restoredSpots.map(Spot::photoPath).toSet()
            (oldPaths - newPaths).forEach { path ->
                try {
                    photoStorage.deleteStoredPhoto(path)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A stale image must not invalidate a successful database restore.
                }
            }
            return BackupResult(restoredSpots.size, backup.missingPhotos)
        } catch (error: Exception) {
            if (!databaseCommitted) {
                importedPhotoPaths.forEach { path ->
                    try {
                        photoStorage.deleteStoredPhoto(path)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Orphan cleanup can retry removing a staged import image later.
                    }
                }
            }
            throw error
        } finally {
            temporaryArchive.delete()
            stagingDirectory.deleteRecursively()
        }
    }

    private suspend fun parseArchive(archive: File, stagingDirectory: File): ParsedBackup {
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size !in 2..MAX_SPOTS + 2) {
                    throw BackupFormatException("The archive has an invalid number of files")
                }
                val names = entries.map(ZipEntry::getName)
                if (names.size != names.toSet().size) {
                    throw BackupFormatException("The archive contains duplicate files")
                }
                entries.forEach { entry ->
                    if (entry.isDirectory ||
                        (entry.name != MANIFEST_ENTRY && entry.name != DATA_ENTRY && !PHOTO_ENTRY_PATTERN.matches(entry.name))
                    ) {
                        throw BackupFormatException("The archive contains an unsupported file")
                    }
                }

                val manifestEntry = zip.getEntry(MANIFEST_ENTRY)
                    ?: throw BackupFormatException("The backup manifest is missing")
                val manifest = JSONObject(readEntry(zip, manifestEntry, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8))
                val backupVersion = manifest.getInt("formatVersion")
                if (manifest.getString("format") != FORMAT_NAME || backupVersion !in SUPPORTED_FORMAT_VERSIONS) {
                    throw BackupFormatException("This backup format is not supported")
                }
                val dataEntry = zip.getEntry(DATA_ENTRY)
                    ?: throw BackupFormatException("The backup data is missing")
                val dataBytes = readEntry(zip, dataEntry, MAX_DATA_BYTES)
                if (sha256(dataBytes) != manifest.getString("dataSha256")) {
                    throw BackupFormatException("The backup data is incomplete or damaged")
                }
                val data = JSONObject(dataBytes.toString(Charsets.UTF_8))
                if (data.getInt("schemaVersion") != backupVersion) {
                    throw BackupFormatException("This backup data version is not supported")
                }
                val brands = data.getJSONArray("brands").parseBrands(backupVersion)
                val models = data.getJSONArray("models").parseModels(backupVersion)
                val spots = data.getJSONArray("spots").parseSpots()
                val achievements = data.getJSONArray("achievements").parseAchievements()
                validateData(brands, models, spots, achievements)

                val expectedPhotoEntries = spots.mapNotNull(BackupSpot::photoEntry).toSet()
                val actualPhotoEntries = names.filter { it.startsWith("photos/") }.toSet()
                if (actualPhotoEntries != expectedPhotoEntries) {
                    throw BackupFormatException("The archive photo list does not match its records")
                }
                if (manifest.getInt("brandCount") != brands.size ||
                    manifest.getInt("modelCount") != models.size ||
                    manifest.getInt("spotCount") != spots.size
                ) {
                    throw BackupFormatException("The backup manifest counts do not match its data")
                }

                var extractedBytes = 0L
                val stagedPhotos = linkedMapOf<String, File>()
                spots.forEach { backupSpot ->
                    val name = backupSpot.photoEntry ?: return@forEach
                    val entry = zip.getEntry(name)
                        ?: throw BackupFormatException("A photo referenced by the backup is missing")
                    if (entry.size > MAX_PHOTO_BYTES) {
                        throw BackupFormatException("A backup photo exceeds the supported size")
                    }
                    val destination = File(stagingDirectory, "${backupSpot.spot.id}.jpg")
                    val copied = zip.getInputStream(entry).buffered().use { input ->
                        FileOutputStream(destination).buffered().use { output ->
                            copyLimited(
                                input,
                                output,
                                minOf(MAX_PHOTO_BYTES, (MAX_ARCHIVE_BYTES - extractedBytes).coerceAtLeast(0L)),
                            )
                        }
                    }
                    if (copied > MAX_PHOTO_BYTES) {
                        throw BackupFormatException("A backup photo exceeds the supported size")
                    }
                    extractedBytes += copied
                    if (sha256(destination) != backupSpot.spot.photoSha256) {
                        throw BackupFormatException("A backup photo is damaged or does not match its record")
                    }
                    stagedPhotos[backupSpot.spot.id] = destination
                }
                val missingPhotos = spots.count { it.photoEntry == null }
                if (manifest.getInt("photoCount") != stagedPhotos.size ||
                    manifest.getInt("missingPhotoCount") != missingPhotos
                ) {
                    throw BackupFormatException("The backup photo counts do not match its data")
                }
                return ParsedBackup(brands, models, spots, achievements, stagedPhotos, missingPhotos)
            }
        } catch (error: BackupFormatException) {
            throw error
        } catch (error: Exception) {
            throw BackupFormatException("The selected archive is damaged or unsupported", error)
        }
    }

    private fun encodeManifest(
        appVersion: String,
        dataSha256: String,
        snapshot: BackupSnapshot,
        missingPhotos: Int,
    ): String = JSONObject()
        .put("format", FORMAT_NAME)
        .put("formatVersion", FORMAT_VERSION)
        .put("appVersion", appVersion)
        .put("createdAt", System.currentTimeMillis())
        .put("dataSha256", dataSha256)
        .put("brandCount", snapshot.brands.size)
        .put("modelCount", snapshot.models.size)
        .put("spotCount", snapshot.spots.size)
        .put("photoCount", snapshot.spots.size - missingPhotos)
        .put("missingPhotoCount", missingPhotos)
        .toString()

    private fun encodeData(
        snapshot: BackupSnapshot,
        photoEntries: Map<String, String>,
    ): String = JSONObject()
        .put("schemaVersion", FORMAT_VERSION)
        .put("brands", JSONArray().apply {
            snapshot.brands.forEach { brand ->
                put(JSONObject()
                    .put("id", brand.id)
                    .put("name", brand.name)
                    .put("normalizedName", brand.normalizedName)
                    .put("source", brand.source.name)
                    .put("createdAt", brand.createdAt)
                    .putNullable("vehiclesDbId", brand.vehiclesDbId)
                    .put("aliases", JSONArray(CatalogMetadataCodec.decode(brand.aliases))))
            }
        })
        .put("models", JSONArray().apply {
            snapshot.models.forEach { model ->
                put(JSONObject()
                    .put("id", model.id)
                    .put("brandId", model.brandId)
                    .put("name", model.name)
                    .put("normalizedName", model.normalizedName)
                    .putNullable("generation", model.generation)
                    .put("normalizedGeneration", model.normalizedGeneration)
                    .putNullable("countryCode", model.countryCode)
                    .put("category", model.category.name)
                    .put("rarity", model.rarity.name)
                    .put("source", model.source.name)
                    .put("createdAt", model.createdAt)
                    .put("updatedAt", model.updatedAt)
                    .putNullable("vehiclesDbId", model.vehiclesDbId)
                    .put("aliases", JSONArray(CatalogMetadataCodec.decode(model.aliases)))
                    .put("bodyTypes", JSONArray(CatalogMetadataCodec.decode(model.bodyTypes)))
                    .putNullableInt("globalPopularityDecile", model.globalPopularityDecile)
                    .putNullableInt("massPopularityDecile", model.massPopularityDecile))
            }
        })
        .put("spots", JSONArray().apply {
            snapshot.spots.forEach { spot ->
                put(JSONObject()
                    .put("id", spot.id)
                    .put("carModelId", spot.carModelId)
                    .put("photoSha256", spot.photoSha256)
                    .put("observedAt", spot.observedAt)
                    .put("observedLocalDate", spot.observedLocalDate)
                    .put("observedLocalTime", spot.observedLocalTime)
                    .put("createdAt", spot.createdAt)
                    .putNullable("color", spot.color)
                    .putNullable("notes", spot.notes)
                    .putNullable("placeLabel", spot.placeLabel)
                    .putNullableInt("vehicleYear", spot.vehicleYear)
                    .putNullable("bodyStyle", spot.bodyStyle?.name)
                    .putNullable("photoEntry", photoEntries[spot.id]))
            }
        })
        .put("achievements", JSONArray().apply {
            snapshot.achievements.forEach { achievement ->
                put(JSONObject()
                    .put("achievementId", achievement.achievementId)
                    .put("unlockedAt", achievement.unlockedAt ?: JSONObject.NULL))
            }
        })
        .toString()

    private fun JSONArray.parseBrands(backupVersion: Int): List<Brand> {
        if (length() > MAX_BRANDS) throw BackupFormatException("The archive contains too many brands")
        return List(length()) { index ->
            val value = getJSONObject(index)
            Brand(
                id = value.getString("id"),
                name = value.getString("name"),
                normalizedName = value.getString("normalizedName"),
                source = RecordSource.valueOf(value.getString("source")),
                createdAt = value.getLong("createdAt"),
                vehiclesDbId = if (backupVersion >= 3) value.nullableString("vehiclesDbId") else null,
                aliases = if (backupVersion >= 3) value.catalogStringList("aliases") else null,
            )
        }
    }

    private fun JSONArray.parseModels(backupVersion: Int): List<CarModel> {
        if (length() > MAX_MODELS) throw BackupFormatException("The archive contains too many models")
        return List(length()) { index ->
            val value = getJSONObject(index)
            CarModel(
                id = value.getString("id"),
                brandId = value.getString("brandId"),
                name = value.getString("name"),
                normalizedName = value.getString("normalizedName"),
                generation = value.nullableString("generation"),
                normalizedGeneration = value.getString("normalizedGeneration"),
                countryCode = value.nullableString("countryCode"),
                category = CarCategory.valueOf(value.getString("category")),
                rarity = CarRarity.valueOf(value.getString("rarity")),
                source = RecordSource.valueOf(value.getString("source")),
                createdAt = value.getLong("createdAt"),
                updatedAt = value.getLong("updatedAt"),
                vehiclesDbId = if (backupVersion >= 3) value.nullableString("vehiclesDbId") else null,
                aliases = if (backupVersion >= 3) value.catalogStringList("aliases") else null,
                bodyTypes = if (backupVersion >= 3) value.catalogStringList("bodyTypes") else null,
                globalPopularityDecile = if (backupVersion >= 3) value.nullableInt("globalPopularityDecile") else null,
                massPopularityDecile = if (backupVersion >= 3) value.nullableInt("massPopularityDecile") else null,
            )
        }
    }

    private fun JSONArray.parseSpots(): List<BackupSpot> {
        if (length() > MAX_SPOTS) throw BackupFormatException("The archive contains too many spots")
        return List(length()) { index ->
            val value = getJSONObject(index)
            val spot = Spot(
                id = value.getString("id"),
                carModelId = value.getString("carModelId"),
                photoPath = "photos/backup-placeholder.jpg",
                photoSha256 = value.getString("photoSha256"),
                observedAt = value.getLong("observedAt"),
                observedLocalDate = value.getString("observedLocalDate"),
                observedLocalTime = value.getString("observedLocalTime"),
                createdAt = value.getLong("createdAt"),
                color = value.nullableString("color"),
                notes = value.nullableString("notes"),
                placeLabel = value.nullableString("placeLabel"),
                vehicleYear = value.nullableInt("vehicleYear"),
                bodyStyle = value.nullableString("bodyStyle")?.let(CarBodyStyle::valueOf),
            )
            BackupSpot(spot = spot, photoEntry = value.nullableString("photoEntry"))
        }
    }

    private fun JSONArray.parseAchievements(): List<AchievementState> {
        if (length() > MAX_ACHIEVEMENTS) throw BackupFormatException("The archive contains too many achievement records")
        return List(length()) { index ->
            val value = getJSONObject(index)
            AchievementState(
                achievementId = value.getString("achievementId"),
                unlockedAt = if (value.isNull("unlockedAt")) null else value.getLong("unlockedAt"),
            )
        }
    }

    private fun validateData(
        brands: List<Brand>,
        models: List<CarModel>,
        spots: List<BackupSpot>,
        achievements: List<AchievementState>,
    ) {
        if (brands.any {
                it.id.isBlank() || it.id.length > MAX_TEXT_ID_LENGTH ||
                    it.name.isBlank() || it.name.length > MAX_NAME_LENGTH ||
                    it.normalizedName.isBlank() || it.normalizedName.length > MAX_NAME_LENGTH
            }
        ) {
            throw BackupFormatException("The archive contains an invalid brand")
        }
        if (brands.map(Brand::id).hasDuplicates() || brands.map(Brand::normalizedName).hasDuplicates()) {
            throw BackupFormatException("The archive contains duplicate brands")
        }
        if (brands.mapNotNull(Brand::vehiclesDbId).hasDuplicates()) {
            throw BackupFormatException("The archive contains duplicate VehiclesDB makes")
        }
        val brandIds = brands.mapTo(hashSetOf(), Brand::id)
        if (models.any {
                it.brandId !in brandIds ||
                    it.id.isBlank() || it.id.length > MAX_TEXT_ID_LENGTH ||
                    it.name.isBlank() || it.name.length > MAX_NAME_LENGTH ||
                    it.normalizedName.isBlank() || it.normalizedName.length > MAX_NAME_LENGTH ||
                    it.normalizedGeneration.length > MAX_NAME_LENGTH ||
                    it.generation?.length?.let { length -> length > MAX_NAME_LENGTH } == true ||
                    it.countryCode?.length?.let { length -> length > 8 } == true ||
                    it.vehiclesDbId?.length?.let { length -> length > MAX_TEXT_ID_LENGTH } == true ||
                    it.globalPopularityDecile?.let { decile -> decile !in 1..10 } == true ||
                    it.massPopularityDecile?.let { decile -> decile !in 1..10 } == true
            }
        ) {
            throw BackupFormatException("A model in the archive references a missing brand")
        }
        if (models.map(CarModel::id).hasDuplicates() || models.map {
                "${it.brandId}\u0000${it.normalizedName}\u0000${it.normalizedGeneration}"
            }.hasDuplicates()
        ) {
            throw BackupFormatException("The archive contains duplicate models")
        }
        if (models.mapNotNull(CarModel::vehiclesDbId).hasDuplicates()) {
            throw BackupFormatException("The archive contains duplicate VehiclesDB models")
        }
        val modelIds = models.mapTo(hashSetOf(), CarModel::id)
        if (spots.any { it.spot.carModelId !in modelIds }) {
            throw BackupFormatException("A spot in the archive references a missing model")
        }
        if (spots.map { it.spot.id }.hasDuplicates()) {
            throw BackupFormatException("The archive contains duplicate spots")
        }
        spots.forEach { backupSpot ->
            val spot = backupSpot.spot
            if (!SAFE_ID_PATTERN.matches(spot.id) ||
                !SHA256_PATTERN.matches(spot.photoSha256) ||
                spot.color?.length?.let { it > MAX_COLOR_LENGTH } == true ||
                spot.notes?.length?.let { it > MAX_NOTES_LENGTH } == true ||
                spot.placeLabel?.length?.let { it > MAX_PLACE_LENGTH } == true ||
                spot.vehicleYear?.let { it !in MIN_VEHICLE_YEAR..LocalDate.now().year + 1 } == true ||
                backupSpot.photoEntry?.let { it != photoEntry(spot.id) } == true
            ) {
                throw BackupFormatException("A spot in the archive contains invalid image information")
            }
            try {
                LocalDate.parse(spot.observedLocalDate)
                LocalTime.parse(spot.observedLocalTime)
            } catch (error: Exception) {
                throw BackupFormatException("A spot in the archive contains an invalid date or time", error)
            }
        }
        if (achievements.map(AchievementState::achievementId).hasDuplicates() ||
            achievements.any {
                it.achievementId.isBlank() || it.achievementId.length > MAX_TEXT_ID_LENGTH
            }
        ) {
            throw BackupFormatException("The archive contains invalid achievement records")
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.catalogStringList(key: String): String? =
        if (!has(key) || isNull(key)) null else {
            val array = getJSONArray(key)
            if (array.length() > MAX_CATALOG_METADATA_ITEMS) {
                throw BackupFormatException("A catalog metadata list is too large")
            }
            val values = List(array.length()) { index ->
                array.getString(index).takeIf { it.length <= MAX_NAME_LENGTH }
                    ?: throw BackupFormatException("A catalog metadata value is too long")
            }
            CatalogMetadataCodec.encode(values)
        }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.putNullableInt(key: String, value: Int?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun readEntry(zip: ZipFile, entry: ZipEntry, limit: Long): ByteArray {
        if (entry.size > limit) throw BackupFormatException("A backup metadata file is too large")
        val output = ByteArrayOutputStream()
        zip.getInputStream(entry).buffered().use { input -> copyLimited(input, output, limit) }
        return output.toByteArray()
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        var copied = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return copied
            if (copied + read > limit) throw BackupFormatException("The selected backup exceeds the supported size")
            output.write(buffer, 0, read)
            copied += read
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size

    private companion object {
        const val FORMAT_NAME = "streetspot-backup"
        const val FORMAT_VERSION = 3
        val SUPPORTED_FORMAT_VERSIONS = setOf(1, 2, FORMAT_VERSION)
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATA_ENTRY = "data.json"
        const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 1024L * 1024
        const val MAX_DATA_BYTES = 64L * 1024 * 1024
        const val MAX_BRANDS = 10_000
        const val MAX_MODELS = 20_000
        const val MAX_SPOTS = 50_000
        const val MAX_ACHIEVEMENTS = 1_000
        const val MAX_CATALOG_METADATA_ITEMS = 100
        const val MAX_TEXT_ID_LENGTH = 100
        const val MAX_NAME_LENGTH = 200
        const val MAX_COLOR_LENGTH = 100
        const val MAX_NOTES_LENGTH = 20_000
        const val MAX_PLACE_LENGTH = 500
        const val MIN_VEHICLE_YEAR = 1886
        val SAFE_ID_PATTERN = Regex("[A-Za-z0-9-]{1,100}")
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
        val PHOTO_ENTRY_PATTERN = Regex("photos/[A-Za-z0-9-]{1,100}\\.jpg")

        fun photoEntry(spotId: String): String = "photos/$spotId.jpg"
    }
}

private class LimitedOutputStream(
    output: OutputStream,
    private val limit: Long,
) : FilterOutputStream(output) {
    private var written = 0L

    override fun write(value: Int) {
        ensureCapacity(1)
        out.write(value)
        written++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensureCapacity(length)
        out.write(buffer, offset, length)
        written += length
    }

    private fun ensureCapacity(additional: Int) {
        if (written + additional > limit) throw IOException("The backup archive exceeds the supported size")
    }
}
