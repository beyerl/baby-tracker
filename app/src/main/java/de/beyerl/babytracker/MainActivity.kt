package de.beyerl.babytracker

import android.os.Bundle
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
import de.beyerl.babytracker.ui.theme.BabyTrackerTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            )
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
