package com.streetspot.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.streetSpotPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "streetspot_preferences",
)

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.streetSpotPreferences

    val dynamicColorsEnabled: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> preferences[DynamicColorsKey] ?: false }

    suspend fun setDynamicColorsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DynamicColorsKey] = enabled
        }
    }

    private companion object {
        val DynamicColorsKey = booleanPreferencesKey("dynamic_colors_enabled")
    }
}
