package com.silicovegas.wombcare.feature.doctor

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.silicovegas.wombcare.core.navigation.Routes

/**
 * The signed-in doctor subtree: patient list → add-by-code / patient detail. Sign-out is
 * handled by the reactive host one level up.
 */
@Composable
fun DoctorNavGraph(
    onSignOut: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    NavHost(navController = nav, startDestination = Routes.DOCTOR_LIST) {
        composable(Routes.DOCTOR_LIST) {
            DoctorPatientListScreen(
                onAddPatient = { nav.navigate(Routes.DOCTOR_ADD) },
                onOpenPatient = { uid -> nav.navigate(Routes.doctorDetail(uid)) },
                onOpenAlerts = { nav.navigate(Routes.DOCTOR_ALERTS) },
                onSignOut = onSignOut,
            )
        }
        composable(Routes.DOCTOR_ADD) {
            AddPatientScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DOCTOR_ALERTS) {
            AlertsFeedScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.DOCTOR_DETAIL,
            arguments = listOf(navArgument("patientUid") { type = NavType.StringType }),
        ) {
            DoctorPatientDetailScreen(onBack = { nav.popBackStack() })
        }
    }
}
