package com.streetspot.app.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.streetspot.app.R

@Composable
fun SocialEntryScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.social_entry_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.social_entry_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.social_create_account))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.social_sign_in))
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.social_continue_offline))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.social_entry_privacy_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
