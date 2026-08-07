package com.silicovegas.wombcare.feature.doctor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.ble.WombCareGatt
import com.silicovegas.wombcare.core.data.model.PatientListEntry
import com.silicovegas.wombcare.core.ui.components.EmptyState
import com.silicovegas.wombcare.core.ui.components.StatusPill
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorPatientListScreen(
    onAddPatient: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onOpenAlerts: () -> Unit,
    onSignOut: () -> Unit,
    vm: DoctorViewModel = hiltViewModel(),
) {
    val patients by vm.patients.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patients") },
                actions = {
                    IconButton(onClick = onOpenAlerts) {
                        Icon(Icons.Rounded.NotificationsNone, contentDescription = "Alerts")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPatient,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add patient") },
            )
        },
    ) { pad ->
        if (patients.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No patients yet",
                    message = "Add a patient with the code they share with you. They'll " +
                        "approve your request before you can see any readings.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(patients, key = { it.patientUid }) { p ->
                    PatientRow(p, now) { onOpenPatient(p.patientUid) }
                }
            }
        }
    }
}

@Composable
private fun PatientRow(p: PatientListEntry, now: Long, onClick: () -> Unit) {
    val live = p.isLive(now, WombCareGatt.DROPOUT_MILLIS)
    val statusColors = LocalStatusColors.current

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier.size(Sizes.statusDot)
                    .background(if (live) statusColors.normal else statusColors.unknown, CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(p.fullName, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        p.pregnancyWeeks?.let { append("$it weeks · ") }
                        append(if (live) "Live now" else lastSeen(p, now))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            p.live?.let { StatusPill(statusOf(it.nsp)) }
        }
    }
}

private fun statusOf(nsp: Int) = when (nsp) {
    1 -> WellnessStatus.SUSPECT
    2 -> WellnessStatus.PATHOLOGIC
    3 -> WellnessStatus.UNKNOWN
    else -> WellnessStatus.NORMAL
}

private fun lastSeen(p: PatientListEntry, now: Long): String {
    val last = p.live?.lastReadingAt ?: return "No readings yet"
    if (last <= 0) return "No readings yet"
    val mins = ((now - last) / 60_000L).toInt()
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "Last seen $mins min ago"
        else -> "Last seen ${mins / 60} h ago"
    }
}
