package com.silicovegas.wombcare.feature.patient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.silicovegas.wombcare.core.navigation.Routes

/**
 * The signed-in patient subtree. Sign-out is handled one level up by the reactive host
 * ([com.silicovegas.wombcare.core.navigation.WombCareNavHost]) — this graph just calls the
 * passed-in [onSignOut], which flips auth state and unmounts the whole subtree.
 *
 * Device selection is a round-trip: the dashboard sends the user to the scan screen, which
 * hands the chosen Bluetooth address back via the dashboard entry's savedStateHandle. The
 * dashboard picks it up and starts monitoring against that specific device.
 */
@Composable
fun PatientNavGraph(
    onSignOut: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    NavHost(navController = nav, startDestination = Routes.PATIENT_DASHBOARD) {
        composable(Routes.PATIENT_DASHBOARD) { entry ->
            val chosenDevice by entry.savedStateHandle
                .getStateFlow<String?>("chosenDevice", null)
                .collectAsStateWithLifecycle()

            PatientDashboardScreen(
                onOpenSettings = { nav.navigate(Routes.PATIENT_SETTINGS) },
                onOpenShare = { nav.navigate(Routes.PATIENT_SHARE) },
                onOpenScan = { nav.navigate(Routes.PATIENT_SCAN) },
                chosenDeviceId = chosenDevice,
                onDeviceConsumed = { entry.savedStateHandle["chosenDevice"] = null },
            )
        }
        composable(Routes.PATIENT_SCAN) {
            DeviceScanScreen(
                onBack = { nav.popBackStack() },
                onDeviceChosen = { address ->
                    nav.previousBackStackEntry?.savedStateHandle?.set("chosenDevice", address)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.PATIENT_SHARE) {
            ShareScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PATIENT_SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onSignOut = onSignOut,
            )
        }
    }
}
