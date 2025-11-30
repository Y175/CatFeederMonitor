package com.example.catfeedermonitor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.catfeedermonitor.data.AppDatabase
import com.example.catfeedermonitor.data.FeedingRecord
import com.example.catfeedermonitor.logic.FeedingSessionManager
import com.example.catfeedermonitor.logic.LogManager
import com.example.catfeedermonitor.logic.ObjectDetectorHelper
import com.example.catfeedermonitor.logic.WebServer
import com.example.catfeedermonitor.ui.BitmapAnnotator
import com.example.catfeedermonitor.ui.CaptureController
import com.example.catfeedermonitor.ui.DebugScreen
import com.example.catfeedermonitor.ui.MonitorScreen
import com.example.catfeedermonitor.ui.StatsScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var detectorHelper: ObjectDetectorHelper
    private lateinit var sessionManager: FeedingSessionManager
    private lateinit var logManager: LogManager
    private lateinit var webServer: WebServer
    
    private val captureController = CaptureController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var currentTempImagePath: String? = null

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        database = AppDatabase.getDatabase(this)
        logManager = LogManager(database.debugLogDao())

        logManager.info("MainActivity", "App started")

        try {
            detectorHelper = ObjectDetectorHelper(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing detector", e)
            logManager.error("MainActivity", "Error initializing detector: ${e.message}")
            Toast.makeText(this, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
        }

        sessionManager = FeedingSessionManager(
            onCaptureTriggered = { catName ->
                captureController.takePicture(
                    context = this,
                    executor = ioExecutor,
                    onImageSaved = { file ->
                        try {
                            var bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            val exif = ExifInterface(file.absolutePath)
                            val orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            
                            val matrix = Matrix()
                            var needsRotation = false
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> {
                                    matrix.postRotate(90f)
                                    needsRotation = true
                                }
                                ExifInterface.ORIENTATION_ROTATE_180 -> {
                                    matrix.postRotate(180f)
                                    needsRotation = true
                                }
                                ExifInterface.ORIENTATION_ROTATE_270 -> {
                                    matrix.postRotate(270f)
                                    needsRotation = true
                                }
                            }

                            if (needsRotation) {
                                val rotatedBitmap = Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                )
                                if (rotatedBitmap != bitmap) {
                                    bitmap.recycle()
                                    bitmap = rotatedBitmap
                                }
                            }

                            val frameResult = detectorHelper.detect(bitmap)
                            val annotatedBitmap = BitmapAnnotator.drawDetections(bitmap, frameResult.detections)

                            FileOutputStream(file).use { out ->
                                annotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }

                            bitmap.recycle()
                            annotatedBitmap.recycle()

                            logManager.info("MainActivity", "Image annotated and saved: ${file.absolutePath}")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error annotating image", e)
                            logManager.error("MainActivity", "Error annotating image: ${e.message}")
                        }

                        currentTempImagePath = file.absolutePath
                        runOnUiThread {
                            val displayName = if (catName == "putong") "噗通" else catName
                            Toast.makeText(this, "抓拍成功，$displayName 正在进食...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onError = { exc ->
                        Log.e("MainActivity", "Capture failed", exc)
                        logManager.error("MainActivity", "Capture failed: ${exc.message}")
                    }
                )
            },
            onSessionEnded = { catName, duration ->
                val displayName = if (catName == "putong") "噗通" else catName
                val imagePath = currentTempImagePath ?: ""
                val record = FeedingRecord(
                    catName = displayName,
                    timestamp = System.currentTimeMillis(),
                    imagePath = imagePath,
                    duration = duration
                )

                CoroutineScope(Dispatchers.IO).launch {
                    database.feedingDao().insert(record)
                }

                currentTempImagePath = null

                runOnUiThread {
                    val sec = duration / 1000
                    Toast.makeText(this, "$displayName 进食结束: ${sec}秒", Toast.LENGTH_LONG).show()
                    logManager.info("FeedingSession", "$displayName finished feeding. Duration: ${sec}s")
                }
            },
            logManager = logManager
        )

        // Initialize WebServer
        webServer = WebServer(this, database.feedingDao())
        webServer.start()

        // Get IP
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = try {
            Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        } catch (e: Exception) {
            "Unavailable"
        }
        val webServerIp = "http://$ipAddress:8080"
        logManager.info("MainActivity", "Web server started at $webServerIp")

        setContent {
            MaterialTheme {
                val permissionState = rememberPermissionState(Manifest.permission.CAMERA)

                if (permissionState.status.isGranted) {
                    MainScreen(webServerIp)
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
    fun MainScreen(webServerIp: String) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        var isBlackScreenMode by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val window = (context as? Activity)?.window

        LaunchedEffect(isBlackScreenMode) {
            window?.let { win ->
                val params = win.attributes
                params.screenBrightness = if (isBlackScreenMode) 0.01f else -1f
                win.attributes = params
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                floatingActionButton = {
                    if (!isBlackScreenMode) {
                        FloatingActionButton(
                            onClick = { isBlackScreenMode = true },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = "省电模式")
                        }
                    }
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Filled.Videocam, contentDescription = "Monitor") },
                            label = { Text("实时监控") },
                            selected = currentRoute == "monitor",
                            onClick = {
                                navController.navigate("monitor") {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
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
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
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
                                captureController = captureController,
                                logManager = logManager,
                                onNavigateToDebug = {
                                    navController.navigate("debug")
                                }
                            )
                        } else {
                            Text("检测器未初始化")
                        }
                    }
                    composable("stats") {
                        StatsScreen(dao = database.feedingDao())
                    }
                    composable("debug") {
                        DebugScreen(dao = database.debugLogDao(), navController = navController, webServerIp = webServerIp)
                    }
                }
            }

            if (isBlackScreenMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(99f)
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { isBlackScreenMode = false })
                        }
                ) {
                    Text(
                        text = "监控运行中... (双击唤醒)",
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detectorHelper.isInitialized) {
            detectorHelper.close()
        }
        if (::webServer.isInitialized) {
            webServer.stop()
        }
        ioExecutor.shutdown()
    }
}