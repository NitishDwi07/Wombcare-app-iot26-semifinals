package com.silicovegas.wombcare.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.core.ui.components.EmptyState
import com.silicovegas.wombcare.core.ui.components.FormError
import com.silicovegas.wombcare.core.ui.components.PrimaryButton
import com.silicovegas.wombcare.core.ui.components.SecondaryButton
import com.silicovegas.wombcare.core.ui.components.WombCareTextField
import com.silicovegas.wombcare.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reset password") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(Spacing.xl)) {
            if (form.success) {
                // A sent-reset confirmation deliberately doesn't reveal whether the email
                // was registered — same message either way avoids leaking who has an account.
                EmptyState(
                    icon = Icons.Rounded.MarkEmailRead,
                    title = "Check your email",
                    message = "If an account exists for that address, we've sent a link to " +
                        "reset your password.",
                    action = { SecondaryButton("Back to login", onBack) },
                )
            } else {
                Text(
                    "Enter the email you signed up with and we'll send a reset link.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xl))
                WombCareTextField(
                    value = form.email,
                    onValueChange = vm::onEmail,
                    label = "Email",
                    error = form.emailError,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    enabled = !form.submitting,
                )
                FormError(form.formError)
                Spacer(Modifier.height(Spacing.lg))
                PrimaryButton(
                    text = "Send reset link",
                    onClick = vm::sendReset,
                    loading = form.submitting,
                )
            }
        }
    }
}
