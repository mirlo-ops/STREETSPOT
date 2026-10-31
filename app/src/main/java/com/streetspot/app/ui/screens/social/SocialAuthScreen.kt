package com.streetspot.app.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.streetspot.app.R
import com.streetspot.app.data.social.FirebaseSocialRepository
import kotlinx.coroutines.launch

enum class SocialAuthMode { CREATE, SIGN_IN, RESET }

@Composable
fun SocialAuthScreen(
    repository: FirebaseSocialRepository?,
    initialMode: SocialAuthMode,
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(
                when (mode) {
                    SocialAuthMode.CREATE -> R.string.social_create_account
                    SocialAuthMode.SIGN_IN -> R.string.social_sign_in
                    SocialAuthMode.RESET -> R.string.social_reset_password
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (repository == null) {
            Text(
                stringResource(R.string.social_firebase_not_configured),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            if (mode == SocialAuthMode.CREATE) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(32) },
                    label = { Text(stringResource(R.string.social_display_name)) },
                    supportingText = { Text(stringResource(R.string.social_display_name_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.social_email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode != SocialAuthMode.RESET) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.social_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (mode == SocialAuthMode.CREATE) {
                Text(stringResource(R.string.social_verify_email_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (repository == null || busy) return@Button
                busy = true
                message = null
                scope.launch {
                    try {
                        when (mode) {
                            SocialAuthMode.CREATE -> repository.createAccount(email, password, displayName)
                            SocialAuthMode.SIGN_IN -> repository.signIn(email, password)
                            SocialAuthMode.RESET -> repository.sendPasswordReset(email)
                        }
                        if (mode == SocialAuthMode.RESET) {
                            message = "Письмо для сброса пароля отправлено, если аккаунт с таким адресом существует."
                        } else {
                            onAuthenticated()
                        }
                    } catch (error: Exception) {
                        message = error.localizedMessage ?: "Не удалось выполнить запрос. Проверьте сеть и повторите попытку."
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = repository != null && !busy && email.isNotBlank() &&
                (mode == SocialAuthMode.RESET || password.isNotEmpty()) &&
                (mode != SocialAuthMode.CREATE || displayName.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (busy) stringResource(R.string.social_working)
                else stringResource(
                    when (mode) {
                        SocialAuthMode.CREATE -> R.string.social_create_account
                        SocialAuthMode.SIGN_IN -> R.string.social_sign_in
                        SocialAuthMode.RESET -> R.string.social_reset_password
                    },
                ),
            )
        }
        if (mode == SocialAuthMode.SIGN_IN) {
            OutlinedButton(onClick = { mode = SocialAuthMode.RESET; message = null }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.social_reset_password))
            }
            OutlinedButton(onClick = { mode = SocialAuthMode.CREATE; message = null }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.social_need_account))
            }
        } else if (mode != SocialAuthMode.SIGN_IN) {
            OutlinedButton(onClick = { mode = SocialAuthMode.SIGN_IN; message = null }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.social_sign_in))
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}
