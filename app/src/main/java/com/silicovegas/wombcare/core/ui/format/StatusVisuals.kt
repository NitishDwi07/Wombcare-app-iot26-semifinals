package com.silicovegas.wombcare.core.ui.format

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.ble.MotionDisplay
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors

/**
 * The single place that turns a domain value into something visible.
 *
 * Colour, icon **and** word travel together in [StatusVisual] by construction, so no call
 * site can accidentally render status as colour alone — which would be unreadable for a
 * colour-blind user and in greyscale print.
 */
@Immutable
data class StatusVisual(
    val accent: Color,
    val container: Color,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun WellnessStatus.visual(): StatusVisual {
    val c = LocalStatusColors.current
    return when (this) {
        WellnessStatus.NORMAL -> StatusVisual(
            accent = c.normal,
            container = c.normalContainer,
            icon = Icons.Rounded.CheckCircle,
            label = stringResource(R.string.status_normal),
        )
        WellnessStatus.SUSPECT -> StatusVisual(
            accent = c.suspect,
            container = c.suspectContainer,
            icon = Icons.Rounded.WarningAmber,
            label = stringResource(R.string.status_suspect),
        )
        WellnessStatus.PATHOLOGIC -> StatusVisual(
            accent = c.pathologic,
            container = c.pathologicContainer,
            icon = Icons.Rounded.ErrorOutline,
            label = stringResource(R.string.status_pathologic),
        )
        WellnessStatus.UNKNOWN -> StatusVisual(
            accent = c.unknown,
            container = c.unknownContainer,
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            label = stringResource(R.string.status_unknown),
        )
    }
}

@Composable
fun MotionDisplay.label(): String = stringResource(
    when (this) {
        MotionDisplay.RESTING -> R.string.motion_resting
        MotionDisplay.SITTING -> R.string.motion_sitting
        MotionDisplay.WALKING -> R.string.motion_walking
        MotionDisplay.ACTIVE -> R.string.motion_active
        MotionDisplay.UNKNOWN -> R.string.motion_unknown
    },
)

@Composable
fun ConnectionState.label(): String = when (this) {
    ConnectionState.Idle -> stringResource(R.string.conn_idle)
    ConnectionState.Scanning -> stringResource(R.string.conn_scanning)
    ConnectionState.Connecting -> stringResource(R.string.conn_connecting)
    ConnectionState.Pairing -> stringResource(R.string.conn_pairing)
    ConnectionState.Connected -> stringResource(R.string.conn_connected)
    ConnectionState.Monitoring -> stringResource(R.string.conn_monitoring)
    ConnectionState.SignalLost -> stringResource(R.string.conn_signal_lost)
    ConnectionState.BluetoothOff -> stringResource(R.string.conn_bluetooth_off)
    ConnectionState.PermissionRequired -> stringResource(R.string.conn_permission_required)
    is ConnectionState.Failed -> stringResource(R.string.conn_failed)
}

/** Chip accent per link state. Only a live, data-flowing link earns the "normal" colour. */
@Composable
fun ConnectionState.accent(): Color {
    val c = LocalStatusColors.current
    return when (this) {
        ConnectionState.Monitoring -> c.normal
        ConnectionState.Connected, ConnectionState.Scanning, ConnectionState.Connecting,
        ConnectionState.Pairing -> c.suspect
        ConnectionState.SignalLost,
        ConnectionState.BluetoothOff,
        ConnectionState.PermissionRequired,
        is ConnectionState.Failed,
        -> c.pathologic
        ConnectionState.Idle -> c.unknown
    }
}

/** Whether the state's dot should pulse — i.e. something is actively in progress. */
val ConnectionState.isPulsing: Boolean
    get() = this is ConnectionState.Scanning ||
        this is ConnectionState.Connecting ||
        this is ConnectionState.Pairing ||
        this is ConnectionState.Monitoring
