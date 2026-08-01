package com.silicovegas.wombcare.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.silicovegas.wombcare.core.data.model.UserRole
import com.silicovegas.wombcare.core.ui.theme.AppRole
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme
import com.silicovegas.wombcare.feature.auth.ForgotPasswordScreen
import com.silicovegas.wombcare.feature.auth.LegalScreen
import com.silicovegas.wombcare.feature.auth.LoginScreen
import com.silicovegas.wombcare.feature.auth.OnboardingScreen
import com.silicovegas.wombcare.feature.auth.RoleSelectScreen
import com.silicovegas.wombcare.feature.auth.SessionState
import com.silicovegas.wombcare.feature.auth.SessionViewModel
import com.silicovegas.wombcare.feature.auth.SignUpScreen
import com.silicovegas.wombcare.feature.doctor.DoctorNavGraph
import com.silicovegas.wombcare.feature.patient.PatientNavGraph

/**
 * The top-level graph. Two concerns are kept separate on purpose:
 *
 *  - **Authentication routing** is reactive, not imperative. Rather than each screen
 *    navigating on success, [SessionViewModel] observes Firebase auth state and this host
 *    swaps the whole auth/main subtree when it flips. So sign-out from anywhere, or a token
 *    expiring, lands the user back at login with no per-screen wiring.
 *  - **The theme follows the role**: the signed-in subtree is wrapped in the patient (rose)
 *    or doctor (sky) theme, so the two apps are visually distinct the moment you're in.
 */
@Composable
fun WombCareNavHost(session: SessionViewModel = hiltViewModel()) {
    val state by session.state.collectAsStateWithLifecycle()

    when (val s = state) {
        SessionState.Loading -> LoadingScreen()

        SessionState.SignedOut ->
            WombCareTheme(role = AppRole.PATIENT) {
                AuthNavGraph()
            }

        is SessionState.SignedIn ->
            WombCareTheme(
                role = if (s.role == UserRole.DOCTOR) AppRole.DOCTOR else AppRole.PATIENT,
            ) {
                when (s.role) {
                    UserRole.DOCTOR -> DoctorNavGraph(onSignOut = session::signOut)
                    else -> PatientNavGraph(onSignOut = session::signOut)
                }
            }
    }
}

@Composable
private fun LoadingScreen() {
    WombCareTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AnimatedVisibility(visible = true) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * The signed-out subtree: onboarding → role select → signup/login → legal. Success is NOT
 * handled here (no navigate-to-home) — a successful auth flips [SessionViewModel], which
 * re-composes [WombCareNavHost] into the signed-in subtree. These screens only navigate
 * *within* the auth flow.
 */
@Composable
private fun AuthNavGraph(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Routes.ONBOARDING) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { nav.navigate(Routes.ROLE_SELECT) })
        }

        composable(Routes.ROLE_SELECT) {
            RoleSelectScreen(
                onRoleChosen = { role -> nav.navigate(Routes.signup(role.wire)) },
                onHaveAccount = { nav.navigate(Routes.LOGIN) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.SIGNUP,
            arguments = listOf(navArgument("role") { type = NavType.StringType }),
        ) { entry ->
            val role = UserRole.from(entry.arguments?.getString("role"))
            SignUpScreen(
                role = role,
                onSignedUp = { /* SessionViewModel routes on auth-state change */ },
                onOpenLegal = { doc -> nav.navigate(Routes.legal(doc)) },
                onBack = { nav.popBackStack() },
                onHaveAccount = {
                    nav.navigate(Routes.LOGIN) { popUpTo(Routes.ROLE_SELECT) }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { /* SessionViewModel routes on auth-state change */ },
                onForgotPassword = { nav.navigate(Routes.FORGOT_PASSWORD) },
                onNoAccount = { nav.navigate(Routes.ROLE_SELECT) { popUpTo(Routes.ONBOARDING) } },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { nav.popBackStack() })
        }

        composable(
            route = Routes.LEGAL,
            arguments = listOf(navArgument("doc") { type = NavType.StringType }),
        ) { entry ->
            LegalScreen(
                docKey = entry.arguments?.getString("doc").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
