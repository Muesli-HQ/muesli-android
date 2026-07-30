package com.phequals7.muesli

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.ui.onboarding.OnboardingScreen
import com.phequals7.muesli.ui.dashboard.DashboardScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val store = SharedStore(context)

  val startDestination = if (store.isOnboardingCompleted) Dashboard else Onboarding
  val backStack = rememberNavBackStack(startDestination)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Onboarding> {
          OnboardingScreen(
            onFinished = {
              // Navigate to dashboard and remove onboarding from backstack
              backStack.add(Dashboard)
            },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<Dashboard> {
          DashboardScreen(
            modifier = Modifier.safeDrawingPadding()
          )
        }
      },
  )
}
