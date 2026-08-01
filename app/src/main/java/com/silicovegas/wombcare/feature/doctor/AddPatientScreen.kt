package com.silicovegas.wombcare.feature.doctor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
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
fun AddPatientScreen(
    onBack: () -> Unit,
    vm: DoctorViewModel = hiltViewModel(),
) {
    val result by vm.addResult.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a patient") },
                navigationIcon = {
                    IconButton(onClick = { vm.resetAdd(); onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(Spacing.screen)) {
            if (result is AddPatientResult.Requested) {
                EmptyState(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Request sent",
                    message = "The patient will see your request and can approve it. Once " +
                        "they do, they'll appear in your patient list.",
                    action = {
                        SecondaryButton("Back to patients", onClick = { vm.resetAdd(); onBack() })
                    },
                )
            } else {
                Text(
                    "Enter the code the patient shared with you. They must approve your " +
                        "request before you can see any readings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xl))
                WombCareTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = "Patient code (WMB-…)",
                    imeAction = ImeAction.Done,
                    enabled = result !is AddPatientResult.Submitting,
                )
                (result as? AddPatientResult.Error)?.let { FormError(it.message) }
                Spacer(Modifier.height(Spacing.lg))
                PrimaryButton(
                    text = "Send request",
                    onClick = { vm.requestLink(code) },
                    loading = result is AddPatientResult.Submitting,
                    enabled = code.isNotBlank(),
                )
            }
        }
    }
}
