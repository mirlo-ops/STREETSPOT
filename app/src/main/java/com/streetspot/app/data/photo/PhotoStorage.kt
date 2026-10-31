package com.streetspot.app.data.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class PreparedPhoto(
    val file: File,
    val sha256: String,
    val width: Int,
    val height: Int,
)

data class StoredPhoto(
    val relativePath: String,
    val sha256: String,
)

class PhotoPreparationException(message: String, cause: Throwable? = null) : IOException(message, cause)

class PhotoStorage(
    private val contentResolver: ContentResolver,
    private val filesDirectory: File,
    private val cacheDirectory: File,
) {
    suspend fun createCameraCaptureFile(): File = withContext(Dispatchers.IO) {
        stagingDirectory().ensureDirectory()
        File(stagingDirectory(), "capture-${UUID.randomUUID()}.jpg")
    }

    suspend fun prepare(uri: Uri): PreparedPhoto = withContext(Dispatchers.IO) {
        val source = createStagingFile("source", ".image")
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw PhotoPreparationException("Selected image could not be opened")
            input.use { inputStream -> source.outputStream().use(inputStream::copyTo) }
            normalize(source)
        } catch (error: PhotoPreparationException) {
            throw error
        } catch (error: Exception) {
            throw PhotoPreparationException("Selected image could not be read", error)
        } finally {
            source.delete()
        }
    }

    suspend fun prepare(cameraFile: File): PreparedPhoto = withContext(Dispatchers.IO) {
        try {
            normalize(cameraFile)
        } catch (error: PhotoPreparationException) {
            throw error
        } catch (error: Exception) {
            throw PhotoPreparationException("Captured image could not be processed", error)
        } finally {
            cameraFile.delete()
        }
    }

    suspend fun persist(preparedPhoto: PreparedPhoto, spotId: String): StoredPhoto =
        withContext(Dispatchers.IO) {
            require(spotId.matches(Regex("[A-Za-z0-9-]+"))) { "Invalid spot identifier" }
            if (!preparedPhoto.file.isFile) {
                throw PhotoPreparationException("Prepared image is no longer available")
            }

            val directory = photoDirectory().also { it.ensureDirectory() }
            val destination = File(directory, "$spotId.jpg")
            try {
                preparedPhoto.file.copyTo(destination, overwrite = false)
            } catch (error: Exception) {
                destination.delete()
                throw PhotoPreparationException("Prepared image could not be stored", error)
            }
            StoredPhoto(relativePath = "photos/${destination.name}", sha256 = preparedPhoto.sha256)
        }

    suspend fun persistReplacement(preparedPhoto: PreparedPhoto, spotId: String): StoredPhoto =
        withContext(Dispatchers.IO) {
            require(spotId.matches(Regex("[A-Za-z0-9-]+"))) { "Invalid spot identifier" }
            if (!preparedPhoto.file.isFile) {
                throw PhotoPreparationException("Prepared image is no longer available")
            }

            val directory = photoDirectory().also { it.ensureDirectory() }
            val destination = File(directory, "$spotId-${UUID.randomUUID()}.jpg")
            try {
                preparedPhoto.file.copyTo(destination, overwrite = false)
            } catch (error: Exception) {
                destination.delete()
                throw PhotoPreparationException("Replacement image could not be stored", error)
            }
            StoredPhoto(relativePath = "photos/${destination.name}", sha256 = preparedPhoto.sha256)
        }

    suspend fun discard(preparedPhoto: PreparedPhoto?) = withContext(Dispatchers.IO) {
        preparedPhoto?.file?.delete()
        Unit
    }

    suspend fun deleteStoredPhoto(relativePath: String) = withContext(Dispatchers.IO) {
        resolveStoredPhoto(relativePath).delete()
        Unit
    }

    suspend fun findStoredPhoto(relativePath: String): File? = withContext(Dispatchers.IO) {
        resolveStoredPhoto(relativePath).takeIf(File::isFile)
    }

    suspend fun importBackupPhoto(
        source: File,
        spotId: String,
        restoreToken: String,
    ): StoredPhoto = withContext(Dispatchers.IO) {
        require(spotId.matches(Regex("[A-Za-z0-9-]+"))) { "Invalid spot identifier" }
        require(restoreToken.matches(Regex("[A-Za-z0-9-]+"))) { "Invalid restore token" }
        if (!source.isFile) throw PhotoPreparationException("Backup image is missing")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PhotoPreparationException("Backup image is not a supported image")
        }
        val hasGpsMetadata = runCatching {
            val exif = ExifInterface(source.absolutePath)
            GPS_TAGS.any { tag -> exif.getAttribute(tag) != null }
        }.getOrElse { error ->
            throw PhotoPreparationException("Backup image metadata could not be validated", error)
        }
        if (hasGpsMetadata) {
            throw PhotoPreparationException("Backup image contains location metadata")
        }

        val directory = photoDirectory().also { it.ensureDirectory() }
        val destination = File(directory, "$spotId-restore-$restoreToken.jpg")
        try {
            source.copyTo(destination, overwrite = false)
            StoredPhoto(
                relativePath = "photos/${destination.name}",
                sha256 = sha256(destination),
            )
        } catch (error: Exception) {
            destination.delete()
            throw PhotoPreparationException("Backup image could not be restored", error)
        }
    }

    suspend fun cleanupOrphans(referencedPaths: Set<String>) = withContext(Dispatchers.IO) {
        val staleBefore = System.currentTimeMillis() - ORPHAN_FILE_MAX_AGE_MILLIS
        photoDirectory().listFiles().orEmpty().forEach { file ->
            val relativePath = "photos/${file.name}"
            if (file.isFile && relativePath !in referencedPaths && file.lastModified() < staleBefore) {
                file.delete()
            }
        }
        stagingDirectory().listFiles().orEmpty().forEach { file ->
            if (file.lastModified() < staleBefore) file.delete()
        }
        Unit
    }

    private fun normalize(source: File): PreparedPhoto {
        if (!source.isFile || source.length() == 0L) {
            throw PhotoPreparationException("Image file is empty")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PhotoPreparationException("File is not a supported image")
        }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: throw PhotoPreparationException("Image decoding failed")

        try {
            val orientation = runCatching {
                ExifInterface(source.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val oriented = applyOrientation(decoded, orientation)
            try {
                val scale = min(1f, MAX_IMAGE_SIDE.toFloat() / max(oriented.width, oriented.height))
                val resized = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        oriented,
                        (oriented.width * scale).toInt().coerceAtLeast(1),
                        (oriented.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    oriented
                }
                try {
                    val destination = createStagingFile("prepared", ".jpg")
                    try {
                        FileOutputStream(destination).use { stream ->
                            if (!resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                                throw PhotoPreparationException("Image encoding failed")
                            }
                            stream.flush()
                            stream.fd.sync()
                        }
                        stripGpsMetadata(destination)
                        return PreparedPhoto(
                            file = destination,
                            sha256 = sha256(destination),
                            width = resized.width,
                            height = resized.height,
                        )
                    } catch (error: Exception) {
                        destination.delete()
                        throw error
                    }
                } finally {
                    if (resized !== oriented) resized.recycle()
                }
            } finally {
                if (oriented !== decoded) oriented.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun stripGpsMetadata(file: File) {
        val exif = ExifInterface(file.absolutePath)
        GPS_TAGS.forEach { exif.setAttribute(it, null) }
        exif.setAttribute(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL.toString(),
        )
        exif.saveAttributes()
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val largestSide = max(width, height)
        while (largestSide / sampleSize > MAX_IMAGE_SIDE * 2) sampleSize *= 2
        return sampleSize
    }

    private fun createStagingFile(prefix: String, suffix: String): File {
        stagingDirectory().ensureDirectory()
        return File(stagingDirectory(), "$prefix-${UUID.randomUUID()}$suffix")
    }

    private fun photoDirectory(): File = File(filesDirectory, PHOTO_DIRECTORY)

    private fun stagingDirectory(): File = File(cacheDirectory, STAGING_DIRECTORY)

    private fun resolveStoredPhoto(relativePath: String): File {
        require(relativePath.startsWith("$PHOTO_DIRECTORY/")) { "Invalid stored photo path" }
        val directory = photoDirectory().canonicalFile
        val file = File(filesDirectory, relativePath).canonicalFile
        require(file.parentFile == directory && file.extension == "jpg") { "Invalid stored photo path" }
        return file
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun File.ensureDirectory() {
        if (!exists() && !mkdirs()) throw IOException("Unable to create image directory")
        if (!isDirectory) throw IOException("Image path is not a directory")
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 2048
        const val JPEG_QUALITY = 88
        const val ORPHAN_FILE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
        const val PHOTO_DIRECTORY = "photos"
        const val STAGING_DIRECTORY = "spot-staging"

        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        )
    }
}
