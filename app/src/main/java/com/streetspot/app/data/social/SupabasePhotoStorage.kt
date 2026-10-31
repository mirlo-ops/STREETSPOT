package com.streetspot.app.data.social

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sends private photo operations through the Supabase Edge Function, never to a public bucket. */
internal class SupabasePhotoStorage(
    baseUrl: String,
    private val publishableKey: String,
    private val cacheDirectory: File,
) {
    private val functionUrl = baseUrl.trim().trimEnd('/')
        .takeIf { it.isNotEmpty() }
        ?.let { "$it/functions/v1/shared-photos" }

    val isConfigured: Boolean
        get() = !functionUrl.isNullOrBlank() && publishableKey.isNotBlank()

    suspend fun upload(
        ownerUid: String,
        spotId: String,
        file: File,
        firebaseIdToken: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        requireConfigured()
        require(file.isFile && file.length() in 1..MAX_PHOTO_BYTES) {
            "Фото должно занимать не больше 5 МБ"
        }
        val connection = openConnection("POST", ownerUid, spotId, firebaseIdToken)
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setFixedLengthStreamingMode(file.length())
            file.inputStream().buffered().use { input ->
                connection.outputStream.buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var transferred = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        transferred += count
                        onProgress((transferred.toFloat() / file.length()).coerceIn(0f, 1f))
                    }
                }
            }
            requireSuccess(connection)
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(ownerUid: String, spotId: String, firebaseIdToken: String): File =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val destinationDirectory = File(cacheDirectory, "community-photos").also { it.mkdirs() }
            val destination = File(destinationDirectory, "${ownerUid}_$spotId.jpg")
            val temporary = File(destinationDirectory, "${ownerUid}_$spotId.download")
            val connection = openConnection("GET", ownerUid, spotId, firebaseIdToken)
            try {
                connection.setRequestProperty("Accept", "image/jpeg")
                requireSuccess(connection)
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_PHOTO_BYTES) throw IOException("Облачное фото превышает 5 МБ")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (destination.exists() && !destination.delete()) throw IOException("Не удалось обновить кэш фото")
                if (!temporary.renameTo(destination)) throw IOException("Не удалось сохранить фото в кэш")
                destination
            } catch (error: Exception) {
                temporary.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }

    suspend fun delete(ownerUid: String, spotId: String?, firebaseIdToken: String) =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val connection = openConnection("DELETE", ownerUid, spotId, firebaseIdToken)
            try {
                requireSuccess(connection)
            } finally {
                connection.disconnect()
            }
        }

    private fun openConnection(
        method: String,
        ownerUid: String,
        spotId: String?,
        firebaseIdToken: String,
    ): HttpURLConnection {
        val url = Uri.parse(functionUrl ?: error("Supabase ещё не настроен"))
            .buildUpon()
            .appendQueryParameter("ownerUid", ownerUid)
            .apply { spotId?.let { appendQueryParameter("spotId", it) } }
            .build()
            .toString()
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $firebaseIdToken")
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status !in 200..299) {
            val details = connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?.replace(Regex("\\s+"), " ")
                ?.take(MAX_ERROR_LENGTH)
            throw IOException(buildString {
                append("Supabase вернул HTTP ").append(status)
                if (!details.isNullOrBlank()) append(": ").append(details)
            })
        }
    }

    private fun requireConfigured() {
        check(isConfigured) {
            "Для публикации фото задайте streetspot.supabase.url и streetspot.supabase.publishableKey в local.properties"
        }
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 5L * 1024L * 1024L
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_ERROR_LENGTH = 600
    }
}
