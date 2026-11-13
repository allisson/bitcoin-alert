package io.github.allisson.bitcoinalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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

private enum class TopLevel(val route: Any, @StringRes val label: Int, @DrawableRes val icon: Int) {
    Market(Home, R.string.market, R.drawable.ic_market),
    AlertList(Alerts, R.string.alerts, R.drawable.ic_alerts),
    AlertHistory(History, R.string.history, R.drawable.ic_history),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitcoinAlertTheme {
                val navController = rememberNavController()
                val entry by navController.currentBackStackEntryAsState()
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TopLevel.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = entry?.destination?.hasRoute(item.route::class) == true,
                                    onClick = {
                                        // Home stays at the bottom of the stack, so Back from Alerts or History returns to it.
                                        navController.navigate(item.route) {
                                            popUpTo(Home)
                                            launchSingleTop = true
                                        }
                                    },
                                    // The label names the item, so the icon is decorative.
                                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                    label = { Text(stringResource(item.label)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    NavHost(navController, startDestination = Home, modifier = Modifier.padding(innerPadding)) {
                        composable<Home> { HomeScreen() }
                        composable<Alerts> {
                            PlaceholderScreen(stringResource(R.string.alerts), stringResource(R.string.alerts_placeholder))
                        }
                        composable<History> {
                            PlaceholderScreen(stringResource(R.string.history), stringResource(R.string.history_placeholder))
                        }
                    }
                }
            }
        }
    }
}
