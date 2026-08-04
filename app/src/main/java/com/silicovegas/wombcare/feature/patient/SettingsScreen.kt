package com.silicovegas.wombcare.feature.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.core.ui.components.DangerButton
import com.silicovegas.wombcare.core.ui.components.FormError
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val demoMode by vm.demoMode.collectAsStateWithLifecycle()
    val deviceForgotten by vm.deviceForgotten.collectAsStateWithLifecycle()
    val deleteState by vm.deleteState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your data?") },
            text = {
                Text(
                    "This permanently deletes your account, all your monitoring history, and " +
                        "anything shared with your doctors. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; vm.deleteMyData() },
                ) {
                    Text("Delete everything", color = LocalStatusColors.current.pathologic)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard(title = "Device") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Demo mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Play a simulated monitoring session without a device. Turn off " +
                                "to connect to a real WombCare device over Bluetooth.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Switch(checked = demoMode, onCheckedChange = vm::setDemoMode)
                }

                Spacer(Modifier.height(Spacing.lg))
                Text(
                    if (deviceForgotten) {
                        "Device forgotten — reconnect from the dashboard and you'll be asked " +
                            "for the PIN again."
                    } else {
                        "Forget the paired device to connect a different one, or to re-enter " +
                            "the PIN. You'll pick and pair again next time you start."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DangerButton(
                    if (deviceForgotten) "Device forgotten" else "Forget device",
                    onClick = vm::forgetDevice,
                    enabled = !deviceForgotten,
                )
            }

            SectionCard(title = "Account") {
                DangerButton("Sign out", onClick = onSignOut)
            }

            SectionCard(title = "Your data") {
                Text(
                    "We store only what's needed: your monitoring summaries and, if you " +
                        "share, what your doctors can see. Raw signals never leave your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (deleteState as? DeleteState.Error)?.let { FormError(it.message) }
                DangerButton(
                    if (deleteState is DeleteState.Deleting) "Deleting…" else "Delete my data",
                    onClick = { confirmDelete = true },
                    enabled = deleteState !is DeleteState.Deleting,
                )
            }
        }
    }
}
