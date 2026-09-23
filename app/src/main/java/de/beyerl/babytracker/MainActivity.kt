package de.beyerl.babytracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.ui.analytics.AnalyticsScreen
import de.beyerl.babytracker.ui.day.DayScreen
import de.beyerl.babytracker.ui.month.MonthScreen
import de.beyerl.babytracker.sync.SyncService
import de.beyerl.babytracker.ui.sync.SyncScreen
import de.beyerl.babytracker.ui.theme.BabyTrackerTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status/navigation bar icons on the always-dark background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            BabyTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }

    // Sync runs while the app is visible; SyncService keeps it running in the background if enabled.
    override fun onStart() {
        super.onStart()
        (application as BabyTrackerApp).sync.acquire()
        SyncService.update(this)
    }

    override fun onStop() {
        (application as BabyTrackerApp).sync.release()
        super.onStop()
    }
}

@Composable
private fun rememberRepository(): EventRepository =
    (LocalContext.current.applicationContext as BabyTrackerApp).repository

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val repo = rememberRepository()

    NavHost(navController = navController, startDestination = "month") {
        composable("month") {
            MonthScreen(
                repository = repo,
                onDayClick = { date ->
                    navController.navigate("day/${date.toEpochDay()}")
                },
                onAnalyticsClick = { navController.navigate("analytics") },
                onSyncClick = { navController.navigate("sync") },
            )
        }
        composable("sync") {
            SyncScreen(onBack = { navController.popBackStack() })
        }
        composable("analytics") {
            AnalyticsScreen(
                repository = repo,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "day/{epochDay}",
            arguments = listOf(navArgument("epochDay") { type = NavType.LongType }),
        ) { backStackEntry ->
            val epochDay = backStackEntry.arguments?.getLong("epochDay") ?: LocalDate.now().toEpochDay()
            DayScreen(
                repository = repo,
                date = LocalDate.ofEpochDay(epochDay),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
