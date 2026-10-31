package com.streetspot.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streetspot.app.data.local.dao.AchievementStateDao
import com.streetspot.app.data.local.dao.BrandDao
import com.streetspot.app.data.local.dao.CarModelDao
import com.streetspot.app.data.local.dao.SpotDao
import com.streetspot.app.data.local.dao.XpTransactionDao
import com.streetspot.app.data.model.AchievementState
import com.streetspot.app.data.model.Brand
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.Spot
import com.streetspot.app.data.model.XpTransaction

@Database(
    entities = [
        Brand::class,
        CarModel::class,
        Spot::class,
        XpTransaction::class,
        AchievementState::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(StreetSpotConverters::class)
abstract class StreetSpotDatabase : RoomDatabase() {
    abstract fun brandDao(): BrandDao

    abstract fun carModelDao(): CarModelDao

    abstract fun spotDao(): SpotDao

    abstract fun xpTransactionDao(): XpTransactionDao

    abstract fun achievementStateDao(): AchievementStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spots ADD COLUMN vehicleYear INTEGER")
                db.execSQL("ALTER TABLE spots ADD COLUMN bodyStyle TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE brands ADD COLUMN vehiclesDbId TEXT")
                db.execSQL("ALTER TABLE brands ADD COLUMN aliases TEXT")
                db.execSQL("ALTER TABLE car_models ADD COLUMN vehiclesDbId TEXT")
                db.execSQL("ALTER TABLE car_models ADD COLUMN aliases TEXT")
                db.execSQL("ALTER TABLE car_models ADD COLUMN bodyTypes TEXT")
                db.execSQL("ALTER TABLE car_models ADD COLUMN globalPopularityDecile INTEGER")
                db.execSQL("ALTER TABLE car_models ADD COLUMN massPopularityDecile INTEGER")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_brands_vehiclesDbId ON brands(vehiclesDbId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_car_models_vehiclesDbId ON car_models(vehiclesDbId)")
            }
        }

        @Volatile
        private var instance: StreetSpotDatabase? = null

        fun getInstance(context: Context): StreetSpotDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StreetSpotDatabase::class.java,
                "streetspot.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
