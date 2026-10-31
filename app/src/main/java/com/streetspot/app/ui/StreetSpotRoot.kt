package com.streetspot.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.social.FirebaseSocialRepository
import com.streetspot.app.data.social.SocialEntryPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import com.streetspot.app.ui.navigation.StreetSpotNavigation
import com.streetspot.app.ui.theme.StreetSpotTheme

@Composable
fun StreetSpotRoot(
    initialRoute: String?,
    externalNavigationRequests: Flow<String>,
) {
    val application = LocalContext.current.applicationContext as StreetSpotApplication
    val entryPreferences = remember(application) { SocialEntryPreferences(application) }
    val entryChoiceMade by entryPreferences.entryChoiceMade.collectAsStateWithLifecycle(initialValue = null)
    val useDynamicColor by application.userPreferencesRepository.dynamicColorsEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val hasSignedInUser = remember(application) {
        FirebaseApp.getApps(application).firstOrNull()?.let { app ->
            runCatching { FirebaseAuth.getInstance(app).currentUser != null }.getOrDefault(false)
        } ?: false
    }
    val startAtEntry = initialRoute == null && entryChoiceMade == false && !hasSignedInUser
    val socialRepository = remember(application) {
        FirebaseSocialRepository.createOrNull(application, application.database, application.photoStorage)
    }

    LaunchedEffect(socialRepository) {
        val repository = socialRepository ?: return@LaunchedEffect
        combine(
            application.spotRepository.observeAll().distinctUntilChanged(),
            repository.auth.authStateFlow().distinctUntilChanged(),
        ) { _, uid -> uid }
            .distinctUntilChanged()
            .collectLatest { uid ->
                if (uid == null) return@collectLatest
                try {
                    repository.syncLocalProjection()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The local diary remains usable when Firebase is offline or not yet configured.
                }
            }
    }

    StreetSpotTheme(useDynamicColor = useDynamicColor) {
        if (initialRoute == null && entryChoiceMade == null) {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { }
        } else {
            StreetSpotNavigation(
                initialRoute = initialRoute,
                externalNavigationRequests = externalNavigationRequests,
                startAtEntry = startAtEntry,
                entryPreferences = entryPreferences,
                socialRepository = socialRepository,
            )
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<String?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.uid) }
    addAuthStateListener(listener)
    trySend(currentUser?.uid)
    awaitClose { removeAuthStateListener(listener) }
}
