package com.streetspot.app.data.social

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

private val Context.streetSpotSocialPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "streetspot_social_preferences",
)

class SocialEntryPreferences(context: Context) {
    private val dataStore = context.applicationContext.streetSpotSocialPreferences

    val entryChoiceMade: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { it[ENTRY_CHOICE_MADE] ?: false }

    suspend fun markEntryChoiceMade() {
        dataStore.edit { it[ENTRY_CHOICE_MADE] = true }
    }

    private companion object {
        val ENTRY_CHOICE_MADE = booleanPreferencesKey("entry_choice_made")
    }
}
