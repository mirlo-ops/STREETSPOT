package com.streetspot.app.ui.screens.social

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import com.streetspot.app.data.model.SpotWithModel
import com.streetspot.app.data.repository.HomeSnapshot
import com.streetspot.app.data.social.CommunitySnapshot
import com.streetspot.app.data.social.FirebaseSocialRepository
import com.streetspot.app.data.social.SocialFriend
import com.streetspot.app.data.social.SocialProfile
import com.streetspot.app.data.social.SocialProfileSettings
import com.streetspot.app.domain.progression.AchievementProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun CommunityScreen(onOpenAuth: (SocialAuthMode) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as StreetSpotApplication
    val repository = remember(application) {
        FirebaseSocialRepository.createOrNull(
            application,
            application.database,
            application.photoStorage,
        )
    }
    var currentUid by remember(repository) { mutableStateOf(repository?.currentUid) }
    DisposableEffect(repository) {
        val auth = repository?.auth
        val listener = FirebaseAuth.AuthStateListener { currentUid = it.currentUser?.uid }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }
    val localSpots by application.database.spotDao().observeAllWithModel()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val home by application.progressRepository.observeHome()
        .collectAsStateWithLifecycle(initialValue = HomeSnapshot())
    val achievements by application.progressRepository.observeAchievements()
        .collectAsStateWithLifecycle(initialValue = emptyList<AchievementProgress>())

    if (repository == null) {
        FirebaseUnavailableContent(onOpenAuth)
    } else if (currentUid == null) {
        SignedOutContent(onOpenAuth)
    } else {
        SignedInCommunityContent(
            repository = repository,
            application = application,
            currentUid = currentUid!!,
            localSpots = localSpots,
            home = home,
            achievements = achievements,
            onOpenAuth = onOpenAuth,
        )
    }
}

@Composable
private fun FirebaseUnavailableContent(onOpenAuth: (SocialAuthMode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.community_firebase_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.social_firebase_not_configured), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.community_local_mode_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onOpenAuth(SocialAuthMode.SIGN_IN) }) { Text(stringResource(R.string.social_sign_in)) }
    }
}

@Composable
private fun SignedOutContent(onOpenAuth: (SocialAuthMode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.community_signed_out_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.community_local_mode_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onOpenAuth(SocialAuthMode.CREATE) }) { Text(stringResource(R.string.social_create_account)) }
        OutlinedButton(onClick = { onOpenAuth(SocialAuthMode.SIGN_IN) }) { Text(stringResource(R.string.social_sign_in)) }
    }
}

@Composable
private fun SignedInCommunityContent(
    repository: FirebaseSocialRepository,
    application: StreetSpotApplication,
    currentUid: String,
    localSpots: List<SpotWithModel>,
    home: HomeSnapshot,
    achievements: List<AchievementProgress>,
    onOpenAuth: (SocialAuthMode) -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var snapshot by remember(currentUid) { mutableStateOf<CommunitySnapshot?>(null) }
    var error by remember(currentUid) { mutableStateOf<String?>(null) }
    var message by remember(currentUid) { mutableStateOf<String?>(null) }
    var busy by remember(currentUid) { mutableStateOf(false) }
    var reloadCounter by remember(currentUid) { mutableStateOf(0) }
    var uploadProgress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var requestCode by rememberSaveable { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var deletePassword by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val user = repository.auth.currentUser

    LaunchedEffect(currentUid, reloadCounter) {
        busy = true
        error = null
        try {
            snapshot = repository.loadSnapshot()
        } catch (failure: Exception) {
            error = failure.localizedMessage ?: "Не удалось загрузить данные сообщества. Проверьте сеть и повторите попытку."
        } finally {
            busy = false
        }
    }

    if (pendingRemoval != null) {
        val (friendUid, block) = pendingRemoval!!
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(if (block) R.string.community_block_title else R.string.community_remove_title)) },
            text = { Text(stringResource(if (block) R.string.community_block_confirm else R.string.community_remove_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        busy = true
                        scope.launch {
                            try {
                                repository.removeFriend(friendUid, block)
                                message = if (block) "Пользователь заблокирован." else "Друг удалён."
                                reloadCounter++
                            } catch (failure: Exception) {
                                error = failure.localizedMessage ?: "Не удалось обновить список друзей."
                            } finally { busy = false }
                        }
                    },
                ) { Text(stringResource(if (block) R.string.community_block_action else R.string.community_remove_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.settings_cancel_action)) } },
        )
    }

    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false; deletePassword = "" },
            title = { Text(stringResource(R.string.community_delete_account_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.community_delete_account_message))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text(stringResource(R.string.social_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deletePassword.isNotEmpty() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                repository.deleteAccount(deletePassword)
                                showDeleteAccount = false
                                message = "Аккаунт и облачные данные удалены. Локальный дневник сохранён."
                            } catch (failure: Exception) {
                                error = failure.localizedMessage ?: "Не удалось удалить аккаунт. Облачные данные сохранены; можно повторить попытку."
                            } finally {
                                busy = false
                                deletePassword = ""
                            }
                        }
                    },
                ) { Text(stringResource(R.string.community_delete_account_action)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccount = false; deletePassword = "" }) { Text(stringResource(R.string.settings_cancel_action)) } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            listOf(R.string.community_profile_tab, R.string.community_friends_tab).forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(label)) },
                )
            }
        }
        snapshot?.takeIf { it.checkedAtEpochMillis > 0L }?.let { loaded ->
            val checkedTime = android.text.format.DateFormat.getTimeFormat(context)
                .format(Date(loaded.checkedAtEpochMillis))
            Text(
                stringResource(
                    if (loaded.isFromCache) R.string.community_checked_cache else R.string.community_checked_server,
                    checkedTime,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (busy && snapshot != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                if (snapshot == null) {
                    TextButton(enabled = !busy, onClick = { reloadCounter++ }) {
                        Text(stringResource(R.string.community_retry_load))
                    }
                }
            }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        if (busy && snapshot == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.social_working))
            }
        } else if (selectedTab == 0) {
            ProfileSection(
                repository = repository,
                application = application,
                snapshot = snapshot,
                localSpots = localSpots,
                home = home,
                achievements = achievements,
                verified = repository.isEmailVerified,
                busy = busy,
                uploadProgress = uploadProgress,
                onSave = { newSettings ->
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            repository.saveProfile(newSettings)
                            message = "Настройки профиля сохранены."
                            reloadCounter++
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Не удалось сохранить настройки профиля."
                        } finally { busy = false }
                    }
                },
                onPublish = { spot ->
                    busy = true
                    error = null
                    uploadProgress = uploadProgress + (spot.spot.id to 0f)
                    scope.launch {
                        try {
                            val localFile = application.photoStorage.findStoredPhoto(spot.spot.photoPath)
                                ?: error("Локальная фотография не найдена")
                            repository.publishPhoto(spot, localFile) { progress ->
                                uploadProgress = uploadProgress + (spot.spot.id to progress)
                            }
                            message = "Фото опубликовано для друзей."
                            reloadCounter++
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Не удалось загрузить фото. Локальная находка сохранена; повторите попытку."
                        } finally {
                            busy = false
                            uploadProgress = uploadProgress - spot.spot.id
                        }
                    }
                },
                onUnpublish = { spotId ->
                    busy = true
                    scope.launch {
                        try {
                            repository.unpublishPhoto(spotId)
                            message = "Фото удалено из профиля."
                            reloadCounter++
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Не удалось удалить облачное фото."
                        } finally { busy = false }
                    }
                },
                onVerifyEmail = {
                    scope.launch {
                        try {
                            repository.sendVerificationEmail()
                            message = "Письмо подтверждения отправлено."
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Не удалось отправить письмо."
                        }
                    }
                },
                onRefreshVerification = {
                    scope.launch {
                        try {
                            if (repository.refreshEmailVerification()) {
                                message = "Email подтверждён. Функции друзей доступны."
                                reloadCounter++
                            } else message = "Подтверждение пока не найдено. Откройте письмо и повторите проверку."
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Не удалось проверить состояние email."
                        }
                    }
                },
                onSignOut = {
                    repository.signOut()
                    message = null
                },
                onDeleteAccount = { showDeleteAccount = true },
            )
        } else {
            FriendsSection(
                repository = repository,
                snapshot = snapshot,
                verified = repository.isEmailVerified,
                busy = busy,
                requestCode = requestCode,
                onRequestCodeChange = { requestCode = it.uppercase().take(12) },
                onSendRequest = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            repository.sendFriendRequest(requestCode)
                            requestCode = ""
                            message = "Запрос в друзья отправлен."
                            reloadCounter++
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Не удалось отправить запрос."
                        } finally { busy = false }
                    }
                },
                onAccept = { requestId ->
                    busy = true
                    scope.launch {
                        try { repository.acceptRequest(requestId); message = "Запрос принят."; reloadCounter++ }
                        catch (failure: Exception) { error = failure.localizedMessage ?: "Не удалось принять запрос." }
                        finally { busy = false }
                    }
                },
                onDecline = { requestId ->
                    busy = true
                    scope.launch {
                        try { repository.declineRequest(requestId); reloadCounter++ }
                        catch (failure: Exception) { error = failure.localizedMessage ?: "Не удалось отклонить запрос." }
                        finally { busy = false }
                    }
                },
                onCancel = { requestId ->
                    busy = true
                    scope.launch {
                        try { repository.cancelRequest(requestId); reloadCounter++ }
                        catch (failure: Exception) { error = failure.localizedMessage ?: "Не удалось отменить запрос." }
                        finally { busy = false }
                    }
                },
                onRemove = { uid -> pendingRemoval = uid to false },
                onBlock = { uid -> pendingRemoval = uid to true },
            )
        }
    }
}

@Composable
private fun ProfileSection(
    repository: FirebaseSocialRepository,
    application: StreetSpotApplication,
    snapshot: CommunitySnapshot?,
    localSpots: List<SpotWithModel>,
    home: HomeSnapshot,
    achievements: List<AchievementProgress>,
    verified: Boolean,
    busy: Boolean,
    uploadProgress: Map<String, Float>,
    onSave: (SocialProfileSettings) -> Unit,
    onPublish: (SpotWithModel) -> Unit,
    onUnpublish: (String) -> Unit,
    onVerifyEmail: () -> Unit,
    onRefreshVerification: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val user = repository.auth.currentUser
    var settings by remember(snapshot?.settings) { mutableStateOf(snapshot?.settings ?: SocialProfileSettings()) }
    var showPrivacyHelp by remember { mutableStateOf(false) }

    if (showPrivacyHelp) {
        AlertDialog(
            onDismissRequest = { showPrivacyHelp = false },
            title = { Text(stringResource(R.string.community_privacy_title)) },
            text = { Text(stringResource(R.string.community_privacy_help)) },
            confirmButton = { TextButton(onClick = { showPrivacyHelp = false }) { Text(stringResource(R.string.settings_cancel_action)) } },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommunityCard {
            Text(stringResource(R.string.community_profile_title), style = MaterialTheme.typography.titleMedium)
            Text(user?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (verified) {
                Text(stringResource(R.string.community_email_verified), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(R.string.community_email_unverified), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = onVerifyEmail) { Text(stringResource(R.string.community_send_verification)) }
                    TextButton(enabled = !busy, onClick = onRefreshVerification) { Text(stringResource(R.string.community_check_verification)) }
                }
            }
            OutlinedTextField(
                value = settings.displayName,
                onValueChange = { settings = settings.copy(displayName = it.take(32)) },
                label = { Text(stringResource(R.string.social_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.community_local_profile_preview, home.totalXp, home.totalSpots, achievements.count { it.isUnlocked }),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { showPrivacyHelp = true }) { Text(stringResource(R.string.community_privacy_title)) }
        }

        CommunityCard {
            Text(stringResource(R.string.community_sharing_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.community_sharing_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingSwitch(stringResource(R.string.community_share_level), settings.shareLevelXp, !busy) {
                settings = settings.copy(shareLevelXp = it)
            }
            SettingSwitch(stringResource(R.string.community_share_achievements), settings.shareAchievements, !busy) {
                settings = settings.copy(shareAchievements = it)
            }
            SettingSwitch(stringResource(R.string.community_share_models), settings.shareModels, !busy) {
                settings = settings.copy(shareModels = it)
            }
            SettingSwitch(stringResource(R.string.community_share_photos), settings.sharePhotos, !busy) {
                settings = settings.copy(sharePhotos = it)
            }
            Text(stringResource(R.string.community_photo_explicit_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            SettingSwitch(
                stringResource(R.string.community_accept_requests),
                settings.acceptFriendRequests,
                enabled = !busy && verified,
            ) { settings = settings.copy(acceptFriendRequests = it) }
            if (!verified) Text(stringResource(R.string.community_verify_to_use_friends), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings.acceptFriendRequests && snapshot?.friendCode != null) {
                Text(stringResource(R.string.community_your_friend_code), style = MaterialTheme.typography.labelLarge)
                Text(snapshot.friendCode.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            } else if (settings.acceptFriendRequests) {
                Text(stringResource(R.string.community_code_after_save), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onSave(settings) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.community_save_profile))
            }
        }

        if (settings.sharePhotos) {
            CommunityCard {
                Text(stringResource(R.string.community_my_photos_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.community_my_photos_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!repository.isPhotoSharingConfigured) {
                    Text(stringResource(R.string.community_supabase_not_configured), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (localSpots.isEmpty()) {
                    Text(stringResource(R.string.community_no_local_spots), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                localSpots.take(30).forEach { spot ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${spot.brandName} ${spot.modelName}", style = MaterialTheme.typography.bodyMedium)
                            spot.modelGeneration?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val progress = uploadProgress[spot.spot.id]
                        when {
                            progress != null -> CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(32.dp))
                            snapshot?.ownSharedSpotIds?.contains(spot.spot.id) == true -> TextButton(
                                enabled = !busy && repository.isPhotoSharingConfigured,
                                onClick = { onUnpublish(spot.spot.id) },
                            ) {
                                Text(stringResource(R.string.community_unpublish_photo))
                            }
                            else -> TextButton(
                                enabled = !busy && verified && repository.isPhotoSharingConfigured,
                                onClick = { onPublish(spot) },
                            ) {
                                Text(stringResource(R.string.community_publish_photo))
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.social_sign_out))
            }
            OutlinedButton(onClick = onDeleteAccount, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.community_delete_account_action))
            }
        }
    }
}

@Composable
private fun FriendsSection(
    repository: FirebaseSocialRepository,
    snapshot: CommunitySnapshot?,
    verified: Boolean,
    busy: Boolean,
    requestCode: String,
    onRequestCodeChange: (String) -> Unit,
    onSendRequest: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommunityCard {
            Text(stringResource(R.string.community_add_friend_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.community_add_friend_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = requestCode,
                onValueChange = onRequestCodeChange,
                label = { Text(stringResource(R.string.community_friend_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSendRequest,
                enabled = verified && !busy && requestCode.length == 12,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.community_send_request)) }
            if (!verified) Text(stringResource(R.string.community_verify_to_use_friends), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        snapshot?.incomingRequests.orEmpty().takeIf { it.isNotEmpty() }?.let { incoming ->
            CommunityCard {
                Text(stringResource(R.string.community_incoming_requests), style = MaterialTheme.typography.titleMedium)
                incoming.forEach { request ->
                    HorizontalDivider()
                    Text(request.displayName, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = verified && !busy, onClick = { onAccept(request.id) }) { Text(stringResource(R.string.community_accept)) }
                        TextButton(enabled = !busy, onClick = { onDecline(request.id) }) { Text(stringResource(R.string.community_decline)) }
                    }
                }
            }
        }
        snapshot?.outgoingRequests.orEmpty().takeIf { it.isNotEmpty() }?.let { outgoing ->
            CommunityCard {
                Text(stringResource(R.string.community_outgoing_requests), style = MaterialTheme.typography.titleMedium)
                outgoing.forEach { request ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(request.displayName, modifier = Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = { onCancel(request.id) }) { Text(stringResource(R.string.community_cancel_request)) }
                    }
                }
            }
        }
        CommunityCard {
            Text(stringResource(R.string.community_friends_title), style = MaterialTheme.typography.titleMedium)
            if (!repository.isPhotoSharingConfigured && snapshot?.friends?.any { it.profile.sharePhotos && it.sharedSpots.isNotEmpty() } == true) {
                Text(stringResource(R.string.community_supabase_not_configured), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            val friends = snapshot?.friends.orEmpty()
            if (friends.isEmpty()) {
                Text(stringResource(R.string.community_friends_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.community_offline_empty_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            friends.forEach { friend ->
                HorizontalDivider()
                FriendProfileCard(
                    repository = repository,
                    friend = friend,
                    enabled = !busy,
                    onRemove = { onRemove(friend.profile.uid) },
                    onBlock = { onBlock(friend.profile.uid) },
                )
            }
        }
    }
}

@Composable
private fun FriendProfileCard(
    repository: FirebaseSocialRepository,
    friend: SocialFriend,
    enabled: Boolean,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
) {
    val profile = friend.profile
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
        if (profile.shareLevelXp) {
            Text(stringResource(R.string.community_level_value, profile.level ?: 1, profile.totalXp ?: 0))
        }
        if (profile.shareAchievements) {
            Text(stringResource(R.string.community_achievement_count, profile.achievementIds.size))
            profile.achievementIds.forEach { id -> Text("• ${achievementName(id)}", style = MaterialTheme.typography.bodySmall) }
        }
        if (profile.shareModels) {
            Text(stringResource(R.string.community_model_count, profile.modelCount ?: 0))
            profile.modelPreview.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        if (profile.sharePhotos) {
            Text(stringResource(R.string.community_shared_photos, friend.sharedSpots.size), style = MaterialTheme.typography.labelLarge)
            friend.sharedSpots.forEach { spot ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteCommunityPhoto(repository, profile.uid, spot.id)
                    Column {
                        Text("${spot.brandName} ${spot.modelName}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(spot.generation, spot.vehicleYear?.toString()).joinToString(" · ").ifBlank { spot.rarity },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            Text(stringResource(R.string.community_photos_hidden), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = enabled, onClick = onRemove) { Text(stringResource(R.string.community_remove_action)) }
            TextButton(enabled = enabled, onClick = onBlock) { Text(stringResource(R.string.community_block_action)) }
        }
    }
}

@Composable
private fun RemoteCommunityPhoto(repository: FirebaseSocialRepository, uid: String, spotId: String) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, repository, uid, spotId) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(repository.downloadSharedPhoto(uid, spotId).absolutePath)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!, contentDescription = stringResource(R.string.community_shared_photo_description), modifier = Modifier.size(88.dp))
    } else {
        Card(modifier = Modifier.size(88.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.community_photo_loading), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CommunityCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun achievementName(id: String): String = when (id) {
    "FIRST_SPOT" -> stringResource(R.string.achievement_first_spot)
    "JDM_HUNTER" -> stringResource(R.string.achievement_jdm_hunter)
    "NIGHT_RIDER" -> stringResource(R.string.achievement_night_rider)
    "RARE_COLLECTOR" -> stringResource(R.string.achievement_rare_collector)
    "BRAND_HUNTER" -> stringResource(R.string.achievement_brand_hunter)
    "100_SPOTS" -> stringResource(R.string.achievement_spots_100)
    else -> stringResource(R.string.community_unknown_achievement)
}
