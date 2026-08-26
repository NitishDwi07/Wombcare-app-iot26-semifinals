package com.silicovegas.wombcare.feature.patient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.ble.MotionDisplay
import com.silicovegas.wombcare.core.device.statsOf
import com.silicovegas.wombcare.core.ui.components.ConnectionChip
import com.silicovegas.wombcare.core.ui.components.DisclaimerFootnote
import com.silicovegas.wombcare.core.ui.components.FormError
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
    onOpenScan: () -> Unit,
    chosenDeviceId: String?,
    onDeviceConsumed: () -> Unit,
    vm: MonitoringViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A device came back from the scan screen → connect to that specific one, then clear it
    // so a config change doesn't re-trigger.
    LaunchedEffect(chosenDeviceId) {
        if (chosenDeviceId != null) {
            vm.start(chosenDeviceId)
            onDeviceConsumed()
        }
    }

    fun isGranted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    // The Bluetooth permissions a REAL-device session needs. Android 12+ split BLUETOOTH
    // into runtime SCAN/CONNECT; ≤11 uses location for BLE scanning. Demo mode needs none.
    fun blePermissions(): List<String> = when {
        !vm.usesRealBle() -> emptyList()
        Build.VERSION.SDK_INT >= 31 -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Notifications (13+) are nice-to-have; the BLE permissions are start-critical.
    fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        addAll(blePermissions())
    }

    fun bleReady(): Boolean = blePermissions().all { isGranted(it) }

    // Real device → reconnect straight to the remembered device if we have one (no re-scan,
    // and the bond persists so no PIN); otherwise open the scan/picker. Demo → start directly.
    fun proceedToMonitoring() {
        if (!vm.usesRealBle()) { vm.start(); return }
        val remembered = vm.rememberedDeviceId()
        if (remembered != null) vm.start(remembered) else onOpenScan()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Proceed only once the BLE permissions are actually granted (notifications optional).
        // Without this guard, a real-device scan would crash needing BLUETOOTH_SCAN.
        if (bleReady()) proceedToMonitoring()
    }

    fun startMonitoring() {
        val missing = requiredPermissions().filter { !isGranted(it) }
        if (missing.isEmpty()) proceedToMonitoring() else permLauncher.launch(missing.toTypedArray())
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
            val readings = ui.session?.readings?.filter { !it.isPlaceholder }.orEmpty()
            val stats = statsOf(readings)

            // Live session clock: real elapsed seconds since the session began, ticking every
            // second, formatted sec → min → hr (not the per-window count).
            val startedAt = ui.session?.startedAtEpochMillis
            var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(startedAt) {
                if (startedAt != null) {
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        kotlinx.coroutines.delay(1000)
                    }
                }
            }
            val elapsedSec = startedAt?.let { ((nowMs - it) / 1000).coerceAtLeast(0) } ?: 0L

            StatusHeroCard(
                status = latest?.status ?: com.silicovegas.wombcare.core.ble.WellnessStatus.NORMAL,
                waiting = ui.waitingForFirstReading,
                subtitle = when {
                    ui.waitingForFirstReading -> stringResource(R.string.note_first_window)
                    ui.session != null -> formatElapsed(elapsedSec) + " · worst " +
                        statusWord(ui.session!!.worstStatus)
                    else -> null
                },
            )

            // Real-time inference status — share of the session in each class (real numbers).
            if (readings.isNotEmpty()) {
                InferenceStatusCard(stats)
            }

            // Gentle, actionable guidance when the device is live but the signal is poor —
            // so a blank FHR reads as "fix the sensor", not "something is wrong with baby".
            if (ui.connection.isLive && !ui.waitingForFirstReading && latest != null) {
                when {
                    latest.fhrBpm == null -> SensorHint(
                        "No fetal heartbeat detected. Reposition the sensor on your belly and " +
                            "hold still for a few seconds.",
                    )
                    latest.signalLow -> SensorHint(
                        "Weak signal. Adjust the sensor so it sits snugly, and stay still for " +
                            "a clearer reading.",
                    )
                }
            }

            // Summary KPIs — the mother's "daily overview" tiles. Baby's and mother's heart
            // rates sit side by side so the two pulses read as one glance.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "Baby's heart rate",
                    value = latest?.fhrBpm?.toString(),
                    unit = stringResource(R.string.unit_bpm),
                    icon = Icons.Rounded.MonitorHeart,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Mother's heart rate",
                    value = latest?.maternalHrBpm?.toString(),
                    unit = stringResource(R.string.unit_bpm),
                    icon = Icons.Rounded.Favorite,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = stringResource(R.string.label_kicks),
                    value = ui.session?.totalKicks?.toString() ?: "0",
                    unit = stringResource(R.string.unit_kicks),
                    icon = Icons.Rounded.SportsSoccer,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "AI confidence",
                    value = latest?.confidencePercent?.toString(),
                    unit = stringResource(R.string.unit_percent),
                    icon = Icons.Rounded.Speed,
                    note = if (latest?.signalLow == true) {
                        stringResource(R.string.note_signal_low)
                    } else null,
                    dimmed = latest?.signalLow == true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = stringResource(R.string.label_motion),
                    value = latest?.let { motionWord(it.motionDisplay) },
                    icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Device battery",
                    value = latest?.batteryPercent?.toString(),
                    unit = stringResource(R.string.unit_percent),
                    icon = Icons.Rounded.BatteryFull,
                    modifier = Modifier.weight(1f),
                )
            }

            // Numbers first, then charts — only once there's something real to show.
            if (readings.isNotEmpty()) {
                SessionSummaryCard(readings)
                SectionCard(title = "Heart rate") {
                    FhrTrendChart(readings)
                    Spacer(Modifier.height(Spacing.sm))
                    NspTimelineStrip(readings)
                }
                KickSummaryCard(readings)
            }

            // If the link dropped unexpectedly, show WHY (with the GATT code) so a real
            // dropout is never mistaken for "never connected".
            (ui.connection as? ConnectionState.Failed)?.let { FormError(it.reason) }

            Spacer(Modifier.height(Spacing.sm))
            val c = ui.connection
            val busy = c == ConnectionState.Scanning || c == ConnectionState.Connecting ||
                c == ConnectionState.Pairing || c.isLive || c == ConnectionState.SignalLost
            if (busy) {
                SecondaryButton("Stop", onClick = vm::stop)
            } else {
                PrimaryButton("Start monitoring", onClick = ::startMonitoring)
            }

            DisclaimerFootnote()
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Real elapsed time as a clock, rolling up as it fills: seconds until a minute, then
 * "M min S sec", and once past 59 min 59 sec, "H hr M min". So the session shows live
 * seconds early on and never displays a fake minute count.
 */
private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "$h hr $m min"
        m > 0 -> "$m min $s sec"
        else -> "$s sec"
    }
}

/** A soft, non-alarming guidance note (amber) for "fix the sensor" situations. */
@Composable
private fun SensorHint(message: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            com.silicovegas.wombcare.core.ui.theme.Radii.tile,
        ),
        color = com.silicovegas.wombcare.core.ui.theme.LocalStatusColors.current.suspectContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Rounded.Sensors,
                contentDescription = null,
                tint = com.silicovegas.wombcare.core.ui.theme.LocalStatusColors.current.suspect,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
