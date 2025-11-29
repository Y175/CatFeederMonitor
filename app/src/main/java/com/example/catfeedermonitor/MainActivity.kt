package com.example.catfeedermonitor

import android.Manifest
import android.app.Activity
import android.os.Bundle
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
    private var currentTempImagePath: String? = null // NEW: 用来暂存刚才拍的照片路径


    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NEW: 保持屏幕常亮，防止系统自动休眠断开相机
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        database = AppDatabase.getDatabase(this)

        try {
            detectorHelper = ObjectDetectorHelper(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing detector", e)
            Toast.makeText(this, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
        }

        sessionManager = FeedingSessionManager(
            onCaptureTriggered = { catName ->
                // 1. 触发抓拍，但不立即存库，只保存图片文件
                captureController.takePicture(
                    context = this,
                    executor = ioExecutor,
                    onImageSaved = { file ->
                        currentTempImagePath = file.absolutePath // 暂存路径
                        runOnUiThread {
                            Toast.makeText(this, "抓拍成功，正在计时...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onError = { exc ->
                        Log.e("MainActivity", "Capture failed", exc)
                    }
                )
            },
            onSessionEnded = { catName, duration ->
                // 2. 进食结束，写入数据库 (包含时长)
                val imagePath = currentTempImagePath ?: "" // 取出刚才拍的照片

                val record = FeedingRecord(
                    catName = catName,
                    timestamp = System.currentTimeMillis(), // 记录结束时间作为入库时间
                    imagePath = imagePath,
                    duration = duration // NEW: 存入时长
                )

                CoroutineScope(Dispatchers.IO).launch {
                    database.feedingDao().insert(record)
                }

                // 清空暂存
                currentTempImagePath = null

                runOnUiThread {
                    val sec = duration / 1000
                    Toast.makeText(this, "$catName 进食结束: ${sec}秒", Toast.LENGTH_LONG).show()
                }
            }
        )

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

        // NEW: 控制伪息屏模式的状态
        var isBlackScreenMode by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val window = (context as? Activity)?.window

        // NEW: 监听模式变化，自动调节屏幕亮度
        LaunchedEffect(isBlackScreenMode) {
            window?.let { win ->
                val params = win.attributes
                // 0.01f 是最暗，-1f 是跟随系统
                params.screenBrightness = if (isBlackScreenMode) 0.01f else -1f
                win.attributes = params
            }
        }

        // 使用 Box 包裹整个界面，以便放置顶层的遮罩
        Box(modifier = Modifier.fillMaxSize()) {

            Scaffold(
                // NEW: 在顶部添加一个按钮来进入省电模式
                floatingActionButton = {
                    if (!isBlackScreenMode) { // 只有不在黑屏模式时才显示按钮
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

            // NEW: 全屏黑色遮罩层
            if (isBlackScreenMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(99f) // 确保在最上层
                        .pointerInput(Unit) {
                            // 监听双击事件来退出黑屏模式
                            detectTapGestures(
                                onDoubleTap = { isBlackScreenMode = false }
                            )
                        }
                ) {
                    // 添加一个微弱的提示，防止用户以为死机了
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
        ioExecutor.shutdown()
    }
}