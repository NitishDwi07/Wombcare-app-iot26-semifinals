package com.silicovegas.wombcare.feature.patient

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsSoccer
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.ble.MotionDisplay
import com.silicovegas.wombcare.core.ui.components.ConnectionChip
import com.silicovegas.wombcare.core.ui.components.DisclaimerFootnote
import com.silicovegas.wombcare.core.ui.components.PrimaryButton
import com.silicovegas.wombcare.core.ui.components.SecondaryButton
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.components.StatTile
import com.silicovegas.wombcare.core.ui.components.StatusHeroCard
import com.silicovegas.wombcare.core.ui.format.label
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.feature.patient.charts.FhrTrendChart
import com.silicovegas.wombcare.feature.patient.charts.NspTimelineStrip

/**
 * The patient's live monitoring screen (P1 + P3 combined). Renders the [MonitoringUiState]
 * from [MonitoringViewModel] and offers a single Start/Stop. Everything reactive: as the
 * device (real or simulated) pushes windows, the hero, tiles and charts update in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenShare: () -> Unit,
    vm: MonitoringViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    // Ask for notification permission (13+) so a Pathologic alert can reach her with the
    // app backgrounded. Requested lazily on first Start rather than at launch.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result irrelevant here; notifier degrades gracefully if denied */ }

    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WombCare") },
                actions = {
                    IconButton(onClick = onOpenShare) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share & care team")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
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
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ConnectionChip(ui.connection)
                if (ui.sourceLabel.isNotEmpty()) {
                    Text(
                        ui.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val latest = ui.session?.latest?.takeIf { !it.isPlaceholder }
            StatusHeroCard(
                status = latest?.status ?: com.silicovegas.wombcare.core.ble.WellnessStatus.NORMAL,
                waiting = ui.waitingForFirstReading,
                subtitle = when {
                    ui.waitingForFirstReading -> stringResource(R.string.note_first_window)
                    ui.session != null -> "${ui.session!!.windowCount} min · worst " +
                        statusWord(ui.session!!.worstStatus)
                    else -> null
                },
            )

            // Six tiles.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = stringResource(R.string.label_fhr),
                    value = latest?.fhrBpm?.toString(),
                    unit = stringResource(R.string.unit_bpm),
                    icon = Icons.Rounded.MonitorHeart,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.label_kicks),
                    value = ui.session?.totalKicks?.toString() ?: "0",
                    unit = stringResource(R.string.unit_kicks),
                    icon = Icons.Rounded.SportsSoccer,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = stringResource(R.string.label_confidence),
                    value = latest?.confidencePercent?.toString(),
                    unit = stringResource(R.string.unit_percent),
                    icon = Icons.Rounded.Speed,
                    note = if (latest?.signalLow == true) {
                        stringResource(R.string.note_signal_low)
                    } else null,
                    dimmed = latest?.signalLow == true,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.label_motion),
                    value = latest?.let { motionWord(it.motionDisplay) },
                    icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            BatteryCard(percent = latest?.batteryPercent)

            // Numbers first, then charts — only once there's something real to show.
            val readings = ui.session?.readings?.filter { !it.isPlaceholder }.orEmpty()
            if (readings.isNotEmpty()) {
                SessionSummaryCard(readings)
                SectionCard(title = "Heart rate") {
                    FhrTrendChart(readings)
                    Spacer(Modifier.height(Spacing.sm))
                    NspTimelineStrip(readings)
                }
                KickSummaryCard(readings)
            }

            Spacer(Modifier.height(Spacing.sm))
            if (ui.connection == ConnectionState.Idle) {
                PrimaryButton("Start monitoring", onClick = ::startMonitoring)
            } else {
                SecondaryButton("Stop", onClick = vm::stop)
            }

            DisclaimerFootnote()
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun statusWord(s: com.silicovegas.wombcare.core.ble.WellnessStatus): String =
    stringResource(
        when (s) {
            com.silicovegas.wombcare.core.ble.WellnessStatus.NORMAL -> R.string.status_normal
            com.silicovegas.wombcare.core.ble.WellnessStatus.SUSPECT -> R.string.status_suspect
            com.silicovegas.wombcare.core.ble.WellnessStatus.PATHOLOGIC -> R.string.status_pathologic
            com.silicovegas.wombcare.core.ble.WellnessStatus.UNKNOWN -> R.string.status_unknown
        },
    )

@Composable
private fun motionWord(m: MotionDisplay): String = m.label()
