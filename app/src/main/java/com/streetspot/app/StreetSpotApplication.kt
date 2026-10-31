package com.streetspot.app

import android.app.Application
import android.util.Log
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.backup.BackupRepository
import com.streetspot.app.data.repository.CarCatalogRepository
import com.streetspot.app.data.repository.RoomSpotRepository
import com.streetspot.app.data.repository.RoomCarCatalogRepository
import com.streetspot.app.data.repository.VehiclesDbCatalogSeeder
import com.streetspot.app.data.repository.ProgressRepository
import com.streetspot.app.data.repository.RoomProgressRepository
import com.streetspot.app.data.repository.SpotRepository
import com.streetspot.app.data.photo.PhotoStorage
import com.streetspot.app.data.settings.UserPreferencesRepository
import com.streetspot.app.widget.StreetSpotWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreetSpotApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: StreetSpotDatabase by lazy { StreetSpotDatabase.getInstance(this) }

    val carCatalogRepository: CarCatalogRepository by lazy {
        RoomCarCatalogRepository(database)
    }

    val photoStorage: PhotoStorage by lazy {
        PhotoStorage(contentResolver, filesDir, cacheDir)
    }

    val progressRepository: ProgressRepository by lazy {
        RoomProgressRepository(database)
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(this, database, progressRepository, photoStorage)
    }

    val spotRepository: SpotRepository by lazy {
        RoomSpotRepository(database, photoStorage, progressRepository) {
            refreshWidgetsSafely()
        }
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                ensureLocalCatalog()
                progressRepository.rebuild()
                photoStorage.cleanupOrphans(spotRepository.allPhotoPaths())
                refreshWidgetsSafely()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось подготовить локальный каталог", error)
            }
        }
    }

    internal suspend fun ensureLocalCatalog() {
        carCatalogRepository.ensureDefaultCatalog()
        VehiclesDbCatalogSeeder(database, assets).seed()
    }

    internal suspend fun refreshWidgetsSafely() {
        try {
            StreetSpotWidgets.updateAll(this)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Не удалось обновить виджеты", error)
        }
    }

    private companion object {
        const val TAG = "StreetSpotApplication"
    }
}
