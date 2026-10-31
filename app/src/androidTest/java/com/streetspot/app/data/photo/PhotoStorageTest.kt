package com.streetspot.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhotoStorageTest {
    @Test
    fun cameraPhotoIsResizedAndGpsMetadataIsRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "photo-storage-test-input.jpg")
        val bitmap = Bitmap.createBitmap(2800, 1400, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }
        } finally {
            bitmap.recycle()
        }

        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "55/1,45/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "82/1,55/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "100/1")
            saveAttributes()
        }

        val storage = PhotoStorage(context.contentResolver, context.filesDir, context.cacheDir)
        val prepared = storage.prepare(source)
        try {
            assertTrue(prepared.width <= 2048)
            assertTrue(prepared.height <= 2048)
            assertEquals(64, prepared.sha256.length)
            assertTrue(!source.exists())

            val exif = ExifInterface(prepared.file.absolutePath)
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE))
        } finally {
            storage.discard(prepared)
        }
    }
}
