package io.github.allisson.bitcoinalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.allisson.bitcoinalert.domain.model.AlertState
import io.github.allisson.bitcoinalert.ui.alerts.AlertsScreen
import io.github.allisson.bitcoinalert.ui.history.HistoryScreen
import io.github.allisson.bitcoinalert.ui.home.HomeScreen
import io.github.allisson.bitcoinalert.ui.theme.BitcoinAlertTheme
import kotlinx.coroutines.flow.map
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
                val alerts = (application as BitcoinAlertApp).alertRepository.alerts
                val activeAlerts by remember(alerts) { alerts.map { list -> list.count { it.state == AlertState.Active } } }
                    .collectAsStateWithLifecycle(0)
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
                                    icon = {
                                        BadgedBox(badge = { if (item == TopLevel.AlertList && activeAlerts > 0) ActiveBadge(activeAlerts) }) {
                                            Icon(painterResource(item.icon), contentDescription = null)
                                        }
                                    },
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
                        composable<Alerts> { AlertsScreen() }
                        composable<History> { HistoryScreen() }
                    }
                }
            }
        }
    }
}

/** The number of Active alerts on the Alerts item, read as "2 active alerts". */
@Composable
private fun ActiveBadge(count: Int) {
    val description = pluralStringResource(R.plurals.active_alerts, count, count)
    Badge(
        Modifier.semantics { contentDescription = description },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) { Text(count.toString()) }
}
