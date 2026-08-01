package com.silicovegas.wombcare.core.ui.gallery

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
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.ui.components.ConnectionChip
import com.silicovegas.wombcare.core.ui.components.ConsentCheckbox
import com.silicovegas.wombcare.core.ui.components.DangerButton
import com.silicovegas.wombcare.core.ui.components.DisclaimerCard
import com.silicovegas.wombcare.core.ui.components.DisclaimerFootnote
import com.silicovegas.wombcare.core.ui.components.EmptyState
import com.silicovegas.wombcare.core.ui.components.PillSize
import com.silicovegas.wombcare.core.ui.components.PrimaryButton
import com.silicovegas.wombcare.core.ui.components.SecondaryButton
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.components.StatTile
import com.silicovegas.wombcare.core.ui.components.StatTileSkeleton
import com.silicovegas.wombcare.core.ui.components.StatusHeroCard
import com.silicovegas.wombcare.core.ui.components.StatusPill
import com.silicovegas.wombcare.core.ui.theme.AppRole
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme

/**
 * The design system on one screen.
 *
 * It owns its own [WombCareTheme] so the role and light/dark toggles are live — reviewing
 * rose against sky, and both against dark mode, is the point. Delete once the real
 * navigation graph lands in Phase 3; until then it is the fastest way to check that a
 * component change didn't break the other three surfaces that use it.
 */
@Composable
fun ComponentGallery() {
    var role by remember { mutableStateOf(AppRole.PATIENT) }
    var dark by remember { mutableStateOf(false) }

    WombCareTheme(role = role, darkTheme = dark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Design system", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Phase 1 — component gallery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = role == AppRole.PATIENT,
                        onClick = { role = AppRole.PATIENT },
                        label = { Text("Patient") },
                    )
                    FilterChip(
                        selected = role == AppRole.DOCTOR,
                        onClick = { role = AppRole.DOCTOR },
                        label = { Text("Doctor") },
                    )
                    FilterChip(
                        selected = dark,
                        onClick = { dark = !dark },
                        label = { Text("Dark") },
                    )
                }

                Spacer(Modifier.height(Spacing.xs))

                // ---- Status hero -------------------------------------------------
                SectionCard(title = "Status hero") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatusHeroCard(
                            status = WellnessStatus.NORMAL,
                            subtitle = "Updated 1 min ago",
                            trailing = { StatusPill(WellnessStatus.NORMAL) },
                        )
                        StatusHeroCard(WellnessStatus.SUSPECT, subtitle = "2 windows in a row")
                        StatusHeroCard(
                            WellnessStatus.PATHOLOGIC,
                            subtitle = "Please contact your doctor",
                        )
                        StatusHeroCard(WellnessStatus.NORMAL, waiting = true)
                    }
                }

                // ---- Pills ------------------------------------------------------
                SectionCard(title = "Status pills") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            WellnessStatus.entries.forEach { StatusPill(it) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            StatusPill(WellnessStatus.NORMAL, size = PillSize.LARGE)
                            StatusPill(WellnessStatus.PATHOLOGIC, size = PillSize.LARGE)
                        }
                    }
                }

                // ---- The six dashboard tiles ------------------------------------
                SectionCard(title = "Dashboard tiles") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            StatTile(
                                label = "Fetal heart rate",
                                value = "142",
                                unit = "BPM",
                                icon = Icons.Rounded.MonitorHeart,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Kicks",
                                value = "8",
                                unit = "this session",
                                icon = Icons.Rounded.SportsSoccer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            StatTile(
                                label = "Confidence",
                                value = "41",
                                unit = "%",
                                icon = Icons.Rounded.Speed,
                                note = "Signal quality low — please sit still",
                                dimmed = true,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Motion",
                                value = "Resting",
                                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            // Exactly how a missing measurement must look: "--", never 0.
                            StatTile(
                                label = "Fetal heart rate",
                                value = null,
                                unit = "BPM",
                                icon = Icons.Rounded.Favorite,
                                note = "No lock this minute",
                                modifier = Modifier.weight(1f),
                            )
                            // Battery is not in payload v1 at all.
                            StatTile(
                                label = "Battery",
                                value = null,
                                icon = Icons.Rounded.BatteryFull,
                                note = "Not sent by this device",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ---- Connection -------------------------------------------------
                SectionCard(title = "Connection states") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(
                            ConnectionState.Idle,
                            ConnectionState.Scanning,
                            ConnectionState.Connecting,
                            ConnectionState.Connected,
                            ConnectionState.Monitoring,
                            ConnectionState.SignalLost,
                            ConnectionState.BluetoothOff,
                            ConnectionState.PermissionRequired,
                        ).forEach { ConnectionChip(it) }
                    }
                }

                // ---- Buttons ----------------------------------------------------
                SectionCard(title = "Actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PrimaryButton("Start monitoring", {})
                        PrimaryButton("Approving…", {}, loading = true)
                        PrimaryButton("Disabled", {}, enabled = false)
                        SecondaryButton("Connect a device", {})
                        Row { DangerButton("Revoke access", {}) }
                    }
                }

                // ---- Consent ----------------------------------------------------
                SectionCard(title = "Consent gate") {
                    Column {
                        ConsentCheckbox(
                            checked = true,
                            onCheckedChange = {},
                            text = "I have read and accept the Terms of Service",
                            readLabel = "Read Terms",
                            onRead = {},
                        )
                        ConsentCheckbox(
                            checked = true,
                            onCheckedChange = {},
                            text = "I have read and accept the Privacy Policy",
                            readLabel = "Read Privacy Policy",
                            onRead = {},
                        )
                        ConsentCheckbox(
                            checked = false,
                            onCheckedChange = {},
                            text = "I understand WombCare is not a medical device and does " +
                                "not replace clinical care",
                        )
                    }
                }

                // ---- Empty + loading --------------------------------------------
                SectionCard(title = "Empty and loading") {
                    Column {
                        EmptyState(
                            title = "No sessions yet",
                            message = "Press the button on your WombCare device to record " +
                                "your first reading.",
                            action = { SecondaryButton("Connect a device", {}) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            StatTileSkeleton(Modifier.weight(1f))
                            StatTileSkeleton(Modifier.weight(1f))
                        }
                    }
                }

                SectionCard(title = "Disclaimer") {
                    DisclaimerCard()
                }

                DisclaimerFootnote(Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(name = "Gallery", heightDp = 2400, widthDp = 400)
@Composable
private fun GalleryPreview() {
    ComponentGallery()
}
