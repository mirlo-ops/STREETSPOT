package com.streetspot.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streetspot.app.data.model.AchievementState
import com.streetspot.app.data.model.Brand
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.RecordSource
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.XpReason
import com.streetspot.app.data.model.XpTransaction
import com.streetspot.app.data.repository.DefaultCatalog
import com.streetspot.app.data.repository.RoomCarCatalogRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreetSpotDatabaseTest {
    private lateinit var database: StreetSpotDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            StreetSpotDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun defaultCatalogSeedIsIdempotent() = runBlocking {
        val repository = RoomCarCatalogRepository(database)

        repository.ensureDefaultCatalog()
        val initialBrandCount = database.brandDao().count()
        val initialModelCount = database.carModelDao().count()
        repository.ensureDefaultCatalog()

        assertEquals(DefaultCatalog.brands.size, initialBrandCount)
        assertEquals(DefaultCatalog.models.size, initialModelCount)
        assertEquals(initialBrandCount, database.brandDao().count())
        assertEquals(initialModelCount, database.carModelDao().count())
    }

    @Test
    fun userModelsNormalizeIdentityAndAlwaysUseCommonRarity() = runBlocking {
        val repository = RoomCarCatalogRepository(database)
        val first = repository.createUserModel(
            brandName = "  Volvo ",
            modelName = " V90   Cross Country ",
            generation = null,
            countryCode = "se",
            category = CarCategory.OTHER,
        )
        val duplicate = repository.createUserModel(
            brandName = "VOLVO",
            modelName = "v90 cross country",
            generation = "   ",
            countryCode = null,
            category = CarCategory.EURO,
        )
        val otherGeneration = repository.createUserModel(
            brandName = "Volvo",
            modelName = "V90 Cross Country",
            generation = "II",
            countryCode = "SE",
            category = CarCategory.OTHER,
        )

        assertEquals("Volvo", first.brand.name)
        assertEquals("V90 Cross Country", first.model.name)
        assertEquals("volvo", first.brand.normalizedName)
        assertEquals(CarRarity.COMMON, first.model.rarity)
        assertEquals(first.model.id, duplicate.model.id)
        assertNotEquals(first.model.id, otherGeneration.model.id)
        assertEquals("SE", first.model.countryCode)
    }

    @Test
    fun uniqueIndexesRejectCaseAndWhitespaceVariantsInsertedDirectly() = runBlocking {
        val brand = Brand(
            id = "brand-one",
            name = "Toyota",
            normalizedName = "toyota",
            source = RecordSource.DEFAULT,
            createdAt = 1L,
        )
        database.brandDao().insert(brand)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.brandDao().insert(brand.copy(id = "brand-two", name = " TOYOTA "))
            }
        }

        val model = CarModel(
            id = "model-one",
            brandId = brand.id,
            name = "Supra",
            normalizedName = "supra",
            generation = null,
            normalizedGeneration = "",
            countryCode = "JP",
            category = CarCategory.JDM,
            rarity = CarRarity.SPORT,
            source = RecordSource.DEFAULT,
            createdAt = 1L,
            updatedAt = 1L,
        )
        database.carModelDao().insert(model)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.carModelDao().insert(model.copy(id = "model-two", name = "SUPRA"))
            }
        }

        database.carModelDao().insert(model.copy(id = "model-three", generation = "A90", normalizedGeneration = "a90"))
        assertEquals(2, database.carModelDao().count())
    }

    @Test
    fun foreignKeysRestrictBrandAndModelDeletionAndCascadeSpotTransactions() = runBlocking {
        val repository = RoomCarCatalogRepository(database)
        val catalogModel = repository.createUserModel(
            brandName = "Honda",
            modelName = "Civic Type R",
            generation = "FL5",
            countryCode = "JP",
            category = CarCategory.JDM,
        )
        val model = catalogModel.model
        val brand = catalogModel.brand

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.brandDao().delete(brand) }
        }

        val spot = Spot(
            id = "spot-one",
            carModelId = model.id,
            photoPath = "spots/spot-one.jpg",
            photoSha256 = "a".repeat(64),
            observedAt = 100L,
            observedLocalDate = "2026-10-29",
            observedLocalTime = "22:10",
            createdAt = 100L,
            color = null,
            notes = null,
            placeLabel = null,
        )
        database.spotDao().insert(spot)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.carModelDao().delete(model) }
        }

        database.xpTransactionDao().insertAll(
            listOf(
                XpTransaction("xp-base", spot.id, 10, XpReason.BASE_RARITY, 100L),
                XpTransaction("xp-model", spot.id, 40, XpReason.NEW_MODEL, 100L),
            ),
        )
        database.achievementStateDao().putAll(listOf(AchievementState("FIRST_SPOT", 100L)))
        assertNotNull(database.achievementStateDao().find("FIRST_SPOT"))

        database.spotDao().delete(spot)

        assertTrue(database.xpTransactionDao().findForSpot(spot.id).isEmpty())
    }

    @Test
    fun exactDuplicateMatchesModelDateAndPreparedPhotoHash() = runBlocking {
        val model = RoomCarCatalogRepository(database).createUserModel(
            brandName = "Mazda",
            modelName = "RX-7",
            generation = "FD",
            countryCode = "JP",
            category = CarCategory.JDM,
        ).model
        val spot = Spot(
            id = "spot-duplicate",
            carModelId = model.id,
            photoPath = "photos/spot-duplicate.jpg",
            photoSha256 = "b".repeat(64),
            observedAt = 1_798_675_200_000L,
            observedLocalDate = "2026-12-01",
            observedLocalTime = "12:00",
            createdAt = 1_798_675_200_000L,
            color = null,
            notes = null,
            placeLabel = null,
        )
        database.spotDao().insert(spot)

        assertEquals(
            spot.id,
            database.spotDao().findExactDuplicate(model.id, "2026-12-01", "b".repeat(64))?.id,
        )
        assertEquals(null, database.spotDao().findExactDuplicate(model.id, "2026-12-02", "b".repeat(64)))
        assertEquals(null, database.spotDao().findExactDuplicate(model.id, "2026-12-01", "c".repeat(64)))
        assertEquals(null, database.spotDao().findExactDuplicate("another-model", "2026-12-01", "b".repeat(64)))
    }

    @Test
    fun spotdexGroupsObservationsAndUsesMostRecentPhoto() = runBlocking {
        val catalog = RoomCarCatalogRepository(database)
        val model = catalog.createUserModel(
            brandName = "Subaru",
            modelName = "Impreza WRX STI",
            generation = "GD",
            countryCode = "JP",
            category = CarCategory.JDM,
        ).model
        val first = Spot(
            id = "spot-first",
            carModelId = model.id,
            photoPath = "photos/first.jpg",
            photoSha256 = "1".repeat(64),
            observedAt = 1_700_000_000_000L,
            observedLocalDate = "2023-11-14",
            observedLocalTime = "22:13",
            createdAt = 1_700_000_000_000L,
            color = null,
            notes = null,
            placeLabel = null,
        )
        val latest = first.copy(
            id = "spot-latest",
            photoPath = "photos/latest.jpg",
            photoSha256 = "2".repeat(64),
            observedAt = first.observedAt + 86_400_000L,
            observedLocalDate = "2023-11-15",
            createdAt = first.createdAt + 86_400_000L,
        )
        database.spotDao().insert(first)
        database.spotDao().insert(latest)

        val entries = database.carModelDao().observeSpotdex().first()
        assertEquals(1, entries.size)
        assertEquals(model.id, entries.single().model.id)
        assertEquals(2, entries.single().spotCount)
        assertEquals(latest.observedAt, entries.single().latestObservedAt)
        assertEquals(latest.photoPath, entries.single().coverPhotoPath)

        database.spotDao().update(latest.copy(observedAt = first.observedAt - 1L))
        val reordered = database.carModelDao().observeSpotdex().first().single()
        assertEquals(first.photoPath, reordered.coverPhotoPath)

        database.spotDao().delete(first)
        assertEquals(1, database.carModelDao().observeSpotdex().first().single().spotCount)
    }
}
