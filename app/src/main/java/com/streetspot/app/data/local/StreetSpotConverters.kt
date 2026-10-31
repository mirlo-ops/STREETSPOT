package com.streetspot.app.data.local

import androidx.room.TypeConverter
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarBodyStyle
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.RecordSource
import com.streetspot.app.data.model.XpReason

class StreetSpotConverters {
    @TypeConverter
    fun carBodyStyleToString(value: CarBodyStyle?): String? = value?.name

    @TypeConverter
    fun stringToCarBodyStyle(value: String?): CarBodyStyle? = value?.let { CarBodyStyle.valueOf(it) }

    @TypeConverter
    fun carCategoryToString(value: CarCategory): String = value.name

    @TypeConverter
    fun stringToCarCategory(value: String): CarCategory = enumValueOf(value)

    @TypeConverter
    fun carRarityToString(value: CarRarity): String = value.name

    @TypeConverter
    fun stringToCarRarity(value: String): CarRarity = enumValueOf(value)

    @TypeConverter
    fun recordSourceToString(value: RecordSource): String = value.name

    @TypeConverter
    fun stringToRecordSource(value: String): RecordSource = enumValueOf(value)

    @TypeConverter
    fun xpReasonToString(value: XpReason): String = value.name

    @TypeConverter
    fun stringToXpReason(value: String): XpReason = enumValueOf(value)
}
