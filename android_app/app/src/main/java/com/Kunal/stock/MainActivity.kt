package com.Kunal.stock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.Kunal.stock.data.UiState
import com.Kunal.stock.ui.BacktestScreen
import com.Kunal.stock.ui.ChartSignalScreen
import com.Kunal.stock.ui.LoginScreen
import com.Kunal.stock.ui.OptionSentimentScreen
import com.Kunal.stock.ui.SimpleSetupScreen
import com.Kunal.stock.ui.CalmAndGhostRadarScreen
import com.Kunal.stock.ui.OiTrendAndSetupsScreen
import com.Kunal.stock.ui.CapitalLadderAndRiskScreen
import com.Kunal.stock.ui.OptionSentimentViewModel
import com.Kunal.stock.ui.theme.StockAnalysisTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.installations.FirebaseInstallations

// ─── Professional Color Tokens ─────────────────────────────────────────────────
private val BgDeep     = Color(0xFF0D0A1E)
private val BgCard     = Color(0xFF12103A)
private val BgCard2    = Color(0xFF1A1650)
private val NeonViolet = Color(0xFF7B2FBE)
private val NeonCyan   = Color(0xFF00DFD8)
private val NeonGold   = Color(0xFFFFD600)
private val GreenUp    = Color(0xFF00E676)
private val RedDown    = Color(0xFFFF5252)
private val TextHigh   = Color(0xFFF5F3FF)
private val TextMid    = Color(0xFFA79BC4)
private val BorderClr  = Color(0xFF352654)

data class NavTab(
    val label: String,
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val activeColor: Color
)

data class NavigationItem(
    val title: String,
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StockAnalysisTheme(darkTheme = true) {
                AuthWrapper()
            }
        }
    }
}

@Composable
fun AuthWrapper() {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    var user by remember { mutableStateOf(auth.currentUser) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(user?.uid) {
        val userId = user?.uid ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        FirebaseInstallations.getInstance().id
            .addOnSuccessListener { installationId ->
                NotificationTokenStore.register(userId, installationId)
            }
            .addOnFailureListener { error ->
                Log.e("TradeAlertNotifications", "Failed to retrieve the Firebase installation ID.", error)
            }
    }

    if (user == null) {
        LoginScreen(onLoginSuccess = { user = auth.currentUser })
    } else {
        MainScreen(
            userEmail = user?.email ?: "Trader",
            onLogout = {
                val userId = user?.uid
                if (userId == null) {
                    auth.signOut()
                    user = null
                } else {
                    FirebaseInstallations.getInstance().id
                        .addOnSuccessListener { installationId ->
                            NotificationTokenStore.unregister(userId, installationId) { error ->
                                if (error != null) {
                                    Log.e("TradeAlertNotifications", "Failed to unregister this device for trade alerts.", error)
                                }
                                auth.signOut()
                                user = null
                            }
                        }
                        .addOnFailureListener { error ->
                            Log.e("TradeAlertNotifications", "Could not retrieve the Firebase installation ID during sign-out.", error)
                            auth.signOut()
                            user = null
                        }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(userEmail: String, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val optionViewModel: OptionSentimentViewModel = viewModel()
    var selectedItem by remember { mutableIntStateOf(0) }
    var showLogout by remember { mutableStateOf(false) }

    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pa"
    )

    val tabs = listOf(
        NavTab("Options",       "options",       Icons.Default.ShowChart,      GreenUp),
        NavTab("Calm & Radar",  "calm_radar",    Icons.Default.Shield,         NeonCyan),
        NavTab("OI & Setups",   "oi_setups",     Icons.Default.Analytics,      NeonViolet),
        NavTab("Capital Ladder","capital",       Icons.Default.AccountBalance, NeonGold)
    )

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Sign Out", color = TextHigh, fontWeight = FontWeight.Bold) },
            text  = { Text("Exit terminal?\n$userEmail", color = TextMid, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showLogout = false; onLogout() }) {
                    Text("Sign Out", color = RedDown, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Cancel", color = TextMid) }
            }
        )
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo circle with Delta
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Brush.radialGradient(listOf(NeonViolet, BgDeep)), CircleShape
                            )
                            .border(1.5.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Δ", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Text("Kunal Option Quant", color = TextHigh, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.5.sp)
                        Text("Option Quant Pro  •  NSE / BSE", color = TextMid, fontSize = 10.sp, letterSpacing = 0.3.sp)
                    }

                    Spacer(Modifier.weight(1f))

                    // Live dot badge
                    Row(
                        modifier = Modifier
                            .background(BgCard2, RoundedCornerShape(20.dp))
                            .border(1.dp, BorderClr, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).background(GreenUp.copy(alpha = pulse), CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text("LIVE", color = GreenUp, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { showLogout = true },
                        modifier = Modifier.size(36.dp)
                            .background(BgCard2, CircleShape)
                            .border(1.dp, BorderClr, CircleShape)
                    ) {
                        Icon(Icons.Default.ExitToApp, null, tint = TextMid, modifier = Modifier.size(18.dp))
                    }
                }

                // Rainbow gradient bottom line
                Box(
                    Modifier.fillMaxWidth().height(2.dp)
                        .background(Brush.horizontalGradient(listOf(NeonViolet, NeonCyan, NeonGold, NeonViolet)))
                        .align(Alignment.BottomCenter)
                )
            }
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(BgCard)) {
                // Top accent line
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(Brush.horizontalGradient(listOf(NeonViolet, NeonCyan, NeonGold, NeonViolet)))
                        .align(Alignment.TopCenter)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedItem == index
                        val color = if (isSelected) tab.activeColor else TextMid
                        val interactionSrc = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) tab.activeColor.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    if (isSelected) 1.dp else 0.dp,
                                    if (isSelected) tab.activeColor.copy(0.35f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(interactionSrc, null) {
                                    if (selectedItem != index) {
                                        selectedItem = index
                                        navController.navigate(tab.route) {
                                            popUpTo("options") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isSelected) {
                                Box(Modifier.width(22.dp).height(3.dp).background(tab.activeColor, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.height(3.dp))
                            } else {
                                Spacer(Modifier.height(6.dp))
                            }
                            Icon(tab.icon, null, tint = color, modifier = Modifier.size(if (isSelected) 24.dp else 22.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(tab.label, color = color, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal, letterSpacing = 0.3.sp)
                        }
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = "options",
            modifier = Modifier.padding(inner).background(BgDeep)
        ) {
            composable("options")     { OptionSentimentScreen(optionViewModel) }
            composable("calm_radar")  { CalmAndGhostRadarScreen() }
            composable("oi_setups")   { OiTrendAndSetupsScreen() }
            composable("capital")     { CapitalLadderAndRiskScreen() }
        }
    }
}
