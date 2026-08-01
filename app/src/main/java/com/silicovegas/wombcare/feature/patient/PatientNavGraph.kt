package com.silicovegas.wombcare.feature.patient

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.silicovegas.wombcare.core.navigation.Routes

/**
 * The signed-in patient subtree. Sign-out is handled one level up by the reactive host
 * ([com.silicovegas.wombcare.core.navigation.WombCareNavHost]) — this graph just calls the
 * passed-in [onSignOut], which flips auth state and unmounts the whole subtree.
 */
@Composable
fun PatientNavGraph(
    onSignOut: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    NavHost(navController = nav, startDestination = Routes.PATIENT_DASHBOARD) {
        composable(Routes.PATIENT_DASHBOARD) {
            PatientDashboardScreen(
                onOpenSettings = { nav.navigate(Routes.PATIENT_SETTINGS) },
                onOpenShare = { nav.navigate(Routes.PATIENT_SHARE) },
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
