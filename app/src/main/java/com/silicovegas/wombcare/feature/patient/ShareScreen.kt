package com.silicovegas.wombcare.feature.patient

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.core.data.model.CareLink
import com.silicovegas.wombcare.core.ui.components.DangerButton
import com.silicovegas.wombcare.core.ui.components.EmptyState
import com.silicovegas.wombcare.core.ui.components.PrimaryButton
import com.silicovegas.wombcare.core.ui.components.SecondaryButton
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Spacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    onBack: () -> Unit,
    vm: ShareViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val message = "Connect with me on WombCare to follow my baby's readings. " +
        "My code is ${ui.shareCode}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share & care team") },
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
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Master switch.
            SectionCard(title = "Sharing") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (ui.sharingEnabled) {
                            "On — approved doctors can see your readings"
                        } else {
                            "Off — nothing leaves your phone"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = ui.sharingEnabled, onCheckedChange = vm::setSharing)
                }
            }

            // The code.
            SectionCard(title = "Your code") {
                Surface(
                    shape = RoundedCornerShape(Radii.tile),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                        when {
                            ui.codeLoading -> CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
                            ui.codeError -> Text(
                                "Couldn't create your code",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            else -> Text(
                                text = ui.shareCode,
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))

                if (ui.codeError) {
                    PrimaryButton("Try again", onClick = vm::loadCode)
                } else {
                    val hasCode = ui.shareCode.isNotBlank() && !ui.codeLoading
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SecondaryButton(
                            "Copy",
                            onClick = { copyToClipboard(context, ui.shareCode) },
                            enabled = hasCode,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            "Share",
                            onClick = { shareText(context, message) },
                            enabled = hasCode,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(onClick = vm::rotateCode, enabled = hasCode) {
                        Text("Generate a new code")
                    }
                }
            }

            // Pending requests.
            if (ui.pending.isNotEmpty()) {
                SectionCard(title = "Requests") {
                    ui.pending.forEach { link ->
                        RequestRow(
                            link = link,
                            onApprove = { vm.approve(link.doctorUid) },
                            onDeny = { vm.deny(link.doctorUid) },
                        )
                    }
                }
            }

            // Linked doctors.
            SectionCard(title = "Your care team") {
                if (ui.activeDoctors.isEmpty()) {
                    EmptyState(
                        title = "No doctors linked",
                        message = "Share your code with a doctor. When they request access, " +
                            "you'll approve it here.",
                    )
                } else {
                    ui.activeDoctors.forEach { link ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    link.doctorName.ifEmpty { "Doctor" },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (link.clinic.isNotEmpty()) {
                                    Text(
                                        link.clinic,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            DangerButton("Revoke", onClick = { vm.revoke(link.doctorUid) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(link: CareLink, onApprove: () -> Unit, onDeny: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radii.tile),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                "${link.doctorName.ifEmpty { "A doctor" }} requested access",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton("Deny", modifier = Modifier.weight(1f), onClick = onDeny)
                PrimaryButton("Approve", modifier = Modifier.weight(1f), onClick = onApprove)
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("WombCare code", text))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share your code"))
}
