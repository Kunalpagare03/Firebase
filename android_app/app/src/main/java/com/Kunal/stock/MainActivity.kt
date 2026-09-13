package com.Kunal.stock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.Kunal.stock.ui.LoginScreen
import com.Kunal.stock.ui.OptionSentimentScreen
import com.Kunal.stock.ui.StockScanScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AuthWrapper()
            }
        }
    }
}

@Composable
fun AuthWrapper() {
    val auth = FirebaseAuth.getInstance()
    var user by remember { mutableStateOf(auth.currentUser) }

    if (user == null) {
        LoginScreen(onLoginSuccess = { user = auth.currentUser })
    } else {
        MainScreen(onLogout = { 
            auth.signOut()
            user = null
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onLogout: () -> Unit) {
    val navController = rememberNavController()
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Stock Scans", "Option Sentiment", "Logout")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Stock Analysis") })
        },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
                            if (item == "Logout") {
                                onLogout()
                            } else {
                                selectedItem = index
                                navController.navigate(if (index == 0) "stocks" else "options")
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "stocks",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("stocks") { StockScanScreen() }
            composable("options") { OptionSentimentScreen() }
        }
    }
}
