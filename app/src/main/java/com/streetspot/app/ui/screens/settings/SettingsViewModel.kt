package com.streetspot.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetspot.app.StreetSpotApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val dynamicColorsEnabled: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

private data class BackupOperationState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class SettingsViewModel(
    private val application: StreetSpotApplication,
) : ViewModel() {
    private val operation = MutableStateFlow(BackupOperationState())

    val uiState = combine(
        application.userPreferencesRepository.dynamicColorsEnabled,
        operation,
    ) { dynamicColors, currentOperation ->
        SettingsUiState(
            dynamicColorsEnabled = dynamicColors,
            isWorking = currentOperation.isWorking,
            message = currentOperation.message,
            messageIsError = currentOperation.messageIsError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDynamicColorsEnabled(enabled: Boolean) {
        if (operation.value.isWorking) return
        viewModelScope.launch {
            try {
                application.userPreferencesRepository.setDynamicColorsEnabled(enabled)
                application.refreshWidgetsSafely()
                operation.value = BackupOperationState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operation.value = BackupOperationState(
                    message = application.getString(com.streetspot.app.R.string.settings_theme_error),
                    messageIsError = true,
                )
            }
        }
    }

    fun exportBackup(destination: Uri) = runBackupOperation(
        errorMessage = application.getString(com.streetspot.app.R.string.settings_export_error),
    ) {
        val result = withContext(Dispatchers.IO) { application.backupRepository.export(destination) }
        if (result.missingPhotos == 0) {
            application.getString(com.streetspot.app.R.string.settings_export_success, result.spots)
        } else {
            application.getString(
                com.streetspot.app.R.string.settings_export_missing_photos,
                result.spots,
                result.missingPhotos,
            )
        }
    }

    fun restoreBackup(source: Uri) = runBackupOperation(
        errorMessage = application.getString(com.streetspot.app.R.string.settings_restore_error),
    ) {
        val result = withContext(Dispatchers.IO) { application.backupRepository.restore(source) }
        withContext(Dispatchers.IO) { application.ensureLocalCatalog() }
        application.refreshWidgetsSafely()
        if (result.missingPhotos == 0) {
            application.getString(com.streetspot.app.R.string.settings_restore_success, result.spots)
        } else {
            application.getString(
                com.streetspot.app.R.string.settings_restore_missing_photos,
                result.spots,
                result.missingPhotos,
            )
        }
    }

    private fun runBackupOperation(
        errorMessage: String,
        operationBlock: suspend () -> String,
    ) {
        if (operation.value.isWorking) return
        viewModelScope.launch {
            operation.value = BackupOperationState(isWorking = true)
            try {
                operation.value = BackupOperationState(message = operationBlock())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operation.value = BackupOperationState(
                    message = errorMessage,
                    messageIsError = true,
                )
            }
        }
    }
}
