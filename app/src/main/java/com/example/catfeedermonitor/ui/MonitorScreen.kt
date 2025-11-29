package com.example.catfeedermonitor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.catfeedermonitor.logic.DetectionResult
import com.example.catfeedermonitor.logic.FeedingSessionManager
import com.example.catfeedermonitor.logic.FeedingState
import com.example.catfeedermonitor.logic.ObjectDetectorHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class CaptureController {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        context: Context,
        previewView: PreviewView,
        analysisExecutor: Executor,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // 确保清理旧的绑定
            cameraProvider?.unbindAll()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor, analyzer)

            try {
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture!!,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CaptureController", "Binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture(context: Context, executor: Executor, onImageSaved: (File) -> Unit, onError: (Exception) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            Log.e("CaptureController", "ImageCapture is null, cannot take picture!")
            onError(IllegalStateException("Camera not ready"))
            return
        }

        // 使用 getExternalFilesDir 确保不需要额外权限，且卸载后自动清理
        // 路径: /Android/data/com.example.catfeedermonitor/files/
        val photoFile = File(
            context.getExternalFilesDir(null),
            "cat_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CaptureController", "Photo capture failed: ${exc.message}", exc)
                    onError(exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CaptureController", "Photo saved to: ${photoFile.absolutePath}")
                    onImageSaved(photoFile)
                }
            }
        )
    }
}

@Composable
fun MonitorScreen(
    sessionManager: FeedingSessionManager,
    detectorHelper: ObjectDetectorHelper,
    captureController: CaptureController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val currentFeedingState by sessionManager.currentState.collectAsState()
    val statusMessage by sessionManager.statusMessage.collectAsState()

    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var sourceResolution by remember { mutableStateOf(android.util.Size(1, 1)) }

    var isPowerSaveModeEnabled by remember { mutableStateOf(true) }
    var isCameraActive by remember { mutableStateOf(true) }

    // 脉冲逻辑
    LaunchedEffect(isPowerSaveModeEnabled, currentFeedingState) {
        while (isActive) {
            if (!isPowerSaveModeEnabled) {
                isCameraActive = true
                delay(1000)
                continue
            }

            if (currentFeedingState != FeedingState.IDLE) {
                isCameraActive = true
                delay(1000)
                continue
            }

            // 巡逻
            isCameraActive = true
            delay(4000)

            if (sessionManager.currentState.value == FeedingState.IDLE) {
                // 休眠
                isCameraActive = false
                detections = emptyList()
                delay(15000)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("省电模式", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isPowerSaveModeEnabled,
                    onCheckedChange = { isPowerSaveModeEnabled = it },
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isCameraActive) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    // 修复点：将绑定逻辑移到了 factory 块中，只执行一次
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        // 在这里绑定相机，只会在 View 创建时执行一次
                        captureController.bindCamera(
                            lifecycleOwner = lifecycleOwner,
                            context = context,
                            previewView = previewView,
                            analysisExecutor = cameraExecutor,
                            analyzer = { imageProxy ->
                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                val bitmap = imageProxy.toBitmap()

                                val matrix = Matrix()
                                matrix.postRotate(rotationDegrees.toFloat())
                                val rotatedBitmap = Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                )
                                val currentSize = android.util.Size(rotatedBitmap.width, rotatedBitmap.height)

                                val results = detectorHelper.detect(rotatedBitmap)
                                sessionManager.processDetections(results)

                                previewView.post {
                                    sourceResolution = currentSize
                                    detections = results
                                }
                                imageProxy.close()
                            }
                        )
                        previewView
                    },
                    // update 块留空，防止每次刷新 UI 都重置相机
                    update = {
                        // No-op
                    }
                )

                DetectionOverlay(
                    detections = detections,
                    sourceWidth = sourceResolution.width,
                    sourceHeight = sourceResolution.height,
                    modifier = Modifier.fillMaxSize()
                )

                if (currentFeedingState == FeedingState.IDLE && isPowerSaveModeEnabled) {
                    Text(
                        "巡逻检测中...",
                        color = Color.Green,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    )
                }

            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("... Zzz ...", color = Color.Gray, style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("设备休眠中，降低发热", color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
fun DetectionOverlay(
    detections: List<DetectionResult>,
    sourceWidth: Int,
    sourceHeight: Int,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        val viewWidth = size.width
        val viewHeight = size.height

        val scaleX = viewWidth / sourceWidth
        val scaleY = viewHeight / sourceHeight
        val scale = kotlin.math.max(scaleX, scaleY)

        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val offsetX = (viewWidth - scaledWidth) / 2
        val offsetY = (viewHeight - scaledHeight) / 2

        for (result in detections) {
            val box = result.boundingBox

            val left = box.left * scale + offsetX
            val top = box.top * scale + offsetY
            val right = box.right * scale + offsetX
            val bottom = box.bottom * scale + offsetY

            val color = if (result.label == "sunny") Color.Yellow else Color.Cyan

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = 40f
                    isAntiAlias = true
                }
                drawText(
                    "${result.label} (${(result.score * 100).toInt()}%)",
                    left,
                    top - 10,
                    paint
                )
            }
        }
    }
}