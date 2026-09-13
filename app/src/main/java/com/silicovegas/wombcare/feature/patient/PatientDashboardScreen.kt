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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.silicovegas.wombcare.core.ui.components.StatusHeroCard
import com.silicovegas.wombcare.core.ui.format.label
import com.silicovegas.wombcare.core.ui.theme.Spacing

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
    onOpenHowTo: () -> Unit,
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

    // First launch → open the setup walkthrough once, then never auto-open again.
    LaunchedEffect(Unit) {
        val pref = com.silicovegas.wombcare.core.data.HowToPreference(context)
        if (!pref.hasSeen()) {
            pref.markSeen()
            onOpenHowTo()
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
                    IconButton(onClick = onOpenHowTo) {
                        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "How to use")
                    }
                    IconButton(onClick = onOpenShare) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share & care team")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)
                    .padding(top = Spacing.sm, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                (ui.connection as? ConnectionState.Failed)?.let { FormError(it.reason) }
                val cs = ui.connection
                val busy = cs == ConnectionState.Scanning || cs == ConnectionState.Connecting ||
                    cs == ConnectionState.Pairing || cs.isLive || cs == ConnectionState.SignalLost
                if (busy) {
                    SecondaryButton("Stop", onClick = vm::stop, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(
                        "Start monitoring",
                        onClick = ::startMonitoring,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DisclaimerFootnote()
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {

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

            // One ticker drives both the first-reading countdown and the session clock; it
            // runs the whole time the link is live.
            val startedAt = ui.session?.startedAtEpochMillis
            val isLive = ui.connection.isLive
            var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
            var liveSinceMs by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(isLive) {
                if (isLive) {
                    if (liveSinceMs == null) liveSinceMs = System.currentTimeMillis()
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        kotlinx.coroutines.delay(1000)
                    }
                } else {
                    liveSinceMs = null
                }
            }
            val elapsedSec = startedAt?.let { ((nowMs - it) / 1000).coerceAtLeast(0) } ?: 0L
            // The device fills a rolling 60-second buffer before its first reading — count it down.
            val countdownSec = if (ui.waitingForFirstReading && isLive)
                (60 - (nowMs - (liveSinceMs ?: nowMs)) / 1000).coerceIn(0, 60) else null

            StatusHeroCard(
                status = latest?.status ?: com.silicovegas.wombcare.core.ble.WellnessStatus.NORMAL,
                waiting = ui.waitingForFirstReading,
                subtitle = when {
                    countdownSec != null && countdownSec > 0L ->
                        "Collecting the first reading — ${countdownSec}s"
                    countdownSec != null -> "Almost there…"
                    ui.waitingForFirstReading -> stringResource(R.string.note_first_window)
                    ui.session != null -> formatElapsed(elapsedSec) + " · worst " +
                        statusWord(ui.session!!.worstStatus)
                    else -> null
                },
            )

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

            // Real-time inference status — the share of the session in each class.
            if (readings.isNotEmpty()) {
                InferenceStatusCard(stats)
            }

            // Compact vitals grid — 6 tiles in two rows so the whole screen fits, no scroll.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                CompactTile("Fetal HR", latest?.fhrBpm?.toString(), "bpm", Modifier.weight(1f))
                CompactTile("Mother HR", latest?.maternalHrBpm?.toString(), "bpm", Modifier.weight(1f))
                CompactTile("Kicks", ui.session?.totalKicks?.toString() ?: "0", "", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                CompactTile(
                    "AI accuracy", latest?.confidencePercent?.toString(), "%",
                    Modifier.weight(1f), dim = latest?.signalLow == true,
                )
                CompactTile("Motion", latest?.let { motionWord(it.motionDisplay) }, "", Modifier.weight(1f))
                CompactTile("Battery", latest?.batteryPercent?.toString(), "%", Modifier.weight(1f))
            }

            // Full session summary card (compact).
            if (readings.isNotEmpty()) {
                SessionSummaryCard(readings)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

/** A small, dense stat tile so the whole dashboard fits on one screen. */
@Composable
private fun CompactTile(
    label: String,
    value: String?,
    unit: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value ?: "--",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (value == null || dim) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (unit.isNotEmpty() && value != null) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
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
