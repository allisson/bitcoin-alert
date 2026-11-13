package io.github.allisson.bitcoinalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.allisson.bitcoinalert.ui.PlaceholderScreen
import io.github.allisson.bitcoinalert.ui.home.HomeScreen
import io.github.allisson.bitcoinalert.ui.theme.BitcoinAlertTheme
import kotlinx.serialization.Serializable

@Serializable
private data object Home

@Serializable
private data object Alerts

@Serializable
private data object History

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitcoinAlertTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = Home) {
                    composable<Home> {
                        HomeScreen(
                            onOpenAlerts = { navController.navigate(Alerts) { launchSingleTop = true } },
                            onOpenHistory = { navController.navigate(History) { launchSingleTop = true } },
                        )
                    }
                    composable<Alerts> {
                        PlaceholderScreen(
                            stringResource(R.string.alerts),
                            stringResource(R.string.alerts_placeholder),
                            navController::backToHome,
                        )
                    }
                    composable<History> {
                        PlaceholderScreen(
                            stringResource(R.string.history),
                            stringResource(R.string.history_placeholder),
                            navController::backToHome,
                        )
                    }
                }
            }
        }
    }
}

// A double tap on Back must not pop Home itself and leave an empty screen.
private fun NavController.backToHome() {
    popBackStack(Home, inclusive = false)
}
