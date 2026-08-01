package com.silicovegas.wombcare.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.core.data.model.UserRole
import com.silicovegas.wombcare.core.ui.components.ConsentCheckbox
import com.silicovegas.wombcare.core.ui.components.DisclaimerCard
import com.silicovegas.wombcare.core.ui.components.FormError
import com.silicovegas.wombcare.core.ui.components.PrimaryButton
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.components.WombCareTextField
import com.silicovegas.wombcare.core.ui.theme.Spacing

/**
 * One signup screen for both roles. The doctor variant adds registration + clinic fields;
 * everything else — including the three-checkbox consent gate — is shared. The "Create
 * account" button stays disabled until all three consents are ticked, so acceptance is an
 * explicit act rather than a default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    role: UserRole,
    onSignedUp: () -> Unit,
    onOpenLegal: (String) -> Unit,
    onBack: () -> Unit,
    onHaveAccount: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val isDoctor = role == UserRole.DOCTOR

    LaunchedEffect(form.success) { if (form.success) onSignedUp() }
    LaunchedEffect(Unit) { vm.reset() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isDoctor) "Doctor sign up" else "Create your account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        ) {
            WombCareTextField(
                value = form.fullName,
                onValueChange = vm::onName,
                label = "Full name",
                error = form.nameError,
                enabled = !form.submitting,
            )
            Spacer(Modifier.height(Spacing.md))
            WombCareTextField(
                value = form.email,
                onValueChange = vm::onEmail,
                label = "Email",
                error = form.emailError,
                keyboardType = KeyboardType.Email,
                enabled = !form.submitting,
            )

            if (isDoctor) {
                Spacer(Modifier.height(Spacing.md))
                WombCareTextField(
                    value = form.registrationNo,
                    onValueChange = vm::onRegistration,
                    label = "Medical registration number",
                    error = form.registrationError,
                    enabled = !form.submitting,
                )
                Spacer(Modifier.height(Spacing.md))
                WombCareTextField(
                    value = form.clinic,
                    onValueChange = vm::onClinic,
                    label = "Clinic / hospital (optional)",
                    enabled = !form.submitting,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            WombCareTextField(
                value = form.password,
                onValueChange = vm::onPassword,
                label = "Password",
                error = form.passwordError,
                isPassword = true,
                enabled = !form.submitting,
            )
            Spacer(Modifier.height(Spacing.md))
            WombCareTextField(
                value = form.confirmPassword,
                onValueChange = vm::onConfirm,
                label = "Confirm password",
                error = form.confirmError,
                isPassword = true,
                imeAction = ImeAction.Done,
                enabled = !form.submitting,
            )

            Spacer(Modifier.height(Spacing.lg))
            DisclaimerCard()

            Spacer(Modifier.height(Spacing.md))
            SectionCard(title = "Before you continue") {
                ConsentCheckbox(
                    checked = form.acceptedTerms,
                    onCheckedChange = vm::onTerms,
                    text = "I have read and accept the Terms of Service",
                    readLabel = "Read Terms",
                    onRead = { onOpenLegal("terms") },
                )
                ConsentCheckbox(
                    checked = form.acceptedPrivacy,
                    onCheckedChange = vm::onPrivacy,
                    text = "I have read and accept the Privacy Policy",
                    readLabel = "Read Privacy Policy",
                    onRead = { onOpenLegal("privacy") },
                )
                ConsentCheckbox(
                    checked = form.acceptedDisclaimer,
                    onCheckedChange = vm::onDisclaimer,
                    text = "I understand WombCare is not a medical device and does not " +
                        "replace clinical care",
                )
                if (form.consentError != null) {
                    Text(
                        form.consentError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            FormError(form.formError)

            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = "Create account",
                onClick = { vm.signUp(role, System.currentTimeMillis()) },
                enabled = form.allConsentsGiven,
                loading = form.submitting,
            )

            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Log in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onHaveAccount),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
