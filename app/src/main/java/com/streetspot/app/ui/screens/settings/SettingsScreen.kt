package com.streetspot.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streetspot.app.R
import com.streetspot.app.StreetSpotApplication
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as StreetSpotApplication
    val factory = remember(application) {
        viewModelFactory {
            initializer { SettingsViewModel(application) }
        }
    }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedBackup by remember { mutableStateOf<android.net.Uri?>(null) }
    var showVehicleAttribution by remember { mutableStateOf(false) }
    val vehicleAttribution = remember(context) {
        runCatching {
            context.assets.open("vehiclesdb/ATTRIBUTION.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { context.getString(R.string.settings_vehicle_data_sources_unavailable) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> selectedBackup = uri }

    if (selectedBackup != null) {
        AlertDialog(
            onDismissRequest = { selectedBackup = null },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedBackup?.let(viewModel::restoreBackup)
                        selectedBackup = null
                    },
                ) { Text(stringResource(R.string.settings_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { selectedBackup = null }) {
                    Text(stringResource(R.string.settings_cancel_action))
                }
            },
        )
    }

    if (showVehicleAttribution) {
        AlertDialog(
            onDismissRequest = { showVehicleAttribution = false },
            title = { Text(stringResource(R.string.settings_vehicle_data_sources_title)) },
            text = {
                Text(
                    text = vehicleAttribution,
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showVehicleAttribution = false }) {
                    Text(stringResource(R.string.settings_vehicle_data_sources_close))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_dynamic_colors_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                R.string.settings_dynamic_colors_description
                            } else {
                                R.string.settings_dynamic_colors_unavailable
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = state.dynamicColorsEnabled,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !state.isWorking,
                    onCheckedChange = viewModel::setDynamicColorsEnabled,
                )
            }
        }

        SettingsCard {
            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_backup_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_system_backup_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.settings_backup_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = {
                    val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    exportLauncher.launch("streetspot-backup-$date.zip")
                },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_export_action))
            }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_restore_select_action))
            }
            if (state.isWorking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.settings_backup_working), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SettingsCard {
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                }.getOrDefault("")
            }
            Text(
                text = stringResource(R.string.settings_version, versionName),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SettingsCard {
            Text(
                text = stringResource(R.string.settings_vehicle_data_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_vehicle_data_credit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { showVehicleAttribution = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_vehicle_data_sources_action))
            }
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vehiclesdb.com")))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_vehicle_data_link))
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
