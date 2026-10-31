package com.streetspot.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.data.photo.PreparedPhoto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: StreetSpotDatabase
    private lateinit var photoStorage: PhotoStorage
    private lateinit var spotRepository: RoomSpotRepository

    @Before
    fun createDependencies() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StreetSpotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        photoStorage = PhotoStorage(context.contentResolver, context.filesDir, context.cacheDir)
        spotRepository = RoomSpotRepository(
            database = database,
            photoStorage = photoStorage,
            progressRepository = RoomProgressRepository(database),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun saveEditAndDeleteRebuildXpAndReportOnlyAnUpwardLevelChange() = runBlocking {
        val catalog = RoomCarCatalogRepository(database)
        val civic = catalog.createUserModel("Honda", "Civic", "", "JP", CarCategory.JDM)
        val accord = catalog.createUserModel("Honda", "Accord", "", "JP", CarCategory.JDM)
        val date = LocalDate.of(2026, 10, 29)

        val first = spotRepository.save(
            draft = SpotDraft(civic.model.id, date, LocalTime.NOON, null, null, null),
            preparedPhoto = preparedPhoto(0xff336699.toInt()),
        ) as SpotSaveResult.Saved
        assertNull(first.reachedLevel)
        assertEquals(70, database.xpTransactionDao().getTotalXp())
        val firstAchievementUnlock = database.achievementStateDao().find("FIRST_SPOT")?.unlockedAt
        assertTrue(firstAchievementUnlock != null)
        assertTrue(database.achievementStateDao().getAll().any { it.achievementId == "100_SPOTS" })

        val second = spotRepository.save(
            draft = SpotDraft(accord.model.id, date, LocalTime.of(13, 0), null, null, null),
            preparedPhoto = preparedPhoto(0xff99aa33.toInt()),
        ) as SpotSaveResult.Saved
        assertEquals(2, second.reachedLevel)
        assertEquals(120, database.xpTransactionDao().getTotalXp())
        assertEquals(firstAchievementUnlock, database.achievementStateDao().find("FIRST_SPOT")?.unlockedAt)

        val edited = spotRepository.update(
            existing = second.spot,
            draft = SpotDraft(
                carModelId = civic.model.id,
                observedDate = date.plusDays(1),
                observedTime = LocalTime.of(13, 0),
                color = "Синий",
                notes = "Исправлена модель и дата",
                placeLabel = null,
            ),
        )
        assertEquals(75, database.xpTransactionDao().getTotalXp())
        assertEquals(5, database.xpTransactionDao().findForSpot(edited.id).single().amount)

        spotRepository.delete(edited)
        assertEquals(70, database.xpTransactionDao().getTotalXp())
        assertTrue(database.xpTransactionDao().findForSpot(edited.id).isEmpty())

        spotRepository.delete(first.spot)
        assertEquals(0, database.xpTransactionDao().getTotalXp())
        assertTrue(database.xpTransactionDao().findForSpot(first.spot.id).isEmpty())
        assertNull(database.achievementStateDao().find("FIRST_SPOT")?.unlockedAt)
        assertNull(database.achievementStateDao().find("FIRST_SPOT")?.unlockedAt)
        assertFalse(File(context.filesDir, first.spot.photoPath).exists())
    }

    private suspend fun preparedPhoto(color: Int): PreparedPhoto {
        val source = File(context.cacheDir, "progress-test-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            source.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }
        } finally {
            bitmap.recycle()
        }
        return photoStorage.prepare(source)
    }
}
