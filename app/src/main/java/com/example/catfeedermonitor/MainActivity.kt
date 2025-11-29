package com.example.catfeedermonitor

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.catfeedermonitor.data.AppDatabase
import com.example.catfeedermonitor.data.FeedingRecord
import com.example.catfeedermonitor.logic.FeedingSessionManager
import com.example.catfeedermonitor.logic.ObjectDetectorHelper
import com.example.catfeedermonitor.ui.CaptureController
import com.example.catfeedermonitor.ui.MonitorScreen
import com.example.catfeedermonitor.ui.StatsScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var detectorHelper: ObjectDetectorHelper
    private lateinit var sessionManager: FeedingSessionManager
    private val captureController = CaptureController()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(this)
        
        try {
            detectorHelper = ObjectDetectorHelper(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing detector", e)
            Toast.makeText(this, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
        }

        sessionManager = FeedingSessionManager { catName ->
            // On Capture Triggered
            captureController.takePicture(
                context = this,
                executor = ioExecutor,
                onImageSaved = { file ->
                    // Save to DB
                    val record = FeedingRecord(
                        catName = catName,
                        timestamp = System.currentTimeMillis(),
                        imagePath = file.absolutePath
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        database.feedingDao().insert(record)
                    }
                    runOnUiThread {
                        Toast.makeText(this, "已抓拍: $catName", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { exc ->
                    Log.e("MainActivity", "Capture failed", exc)
                }
            )
        }

        setContent {
            MaterialTheme {
                val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
                
                if (permissionState.status.isGranted) {
                    MainScreen()
                } else {
                    LaunchedEffect(Unit) {
                        permissionState.launchPermissionRequest()
                    }
                    Text("需要相机权限才能运行")
                }
            }
        }
    }



    @Composable
    fun MainScreen() {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Videocam, contentDescription = "Monitor") },
                        label = { Text("实时监控") },
                        selected = currentRoute == "monitor",
                        onClick = { 
                            navController.navigate("monitor") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = "Stats") },
                        label = { Text("进食统计") },
                        selected = currentRoute == "stats",
                        onClick = { 
                            navController.navigate("stats") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "monitor",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("monitor") {
                    if (::detectorHelper.isInitialized) {
                        MonitorScreen(
                            sessionManager = sessionManager,
                            detectorHelper = detectorHelper,
                            captureController = captureController
                        )
                    } else {
                        Text("Detector not initialized")
                    }
                }
                composable("stats") {
                    StatsScreen(dao = database.feedingDao())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detectorHelper.isInitialized) {
            detectorHelper.close()
        }
        ioExecutor.shutdown()
    }
}
