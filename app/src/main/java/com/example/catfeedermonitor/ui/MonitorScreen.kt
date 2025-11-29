package com.example.catfeedermonitor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.catfeedermonitor.logic.DetectionResult
import com.example.catfeedermonitor.logic.FeedingSessionManager
import com.example.catfeedermonitor.logic.ObjectDetectorHelper
import java.util.concurrent.Executors

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.util.concurrent.Executor

class CaptureController {
    private var imageCapture: ImageCapture? = null

    fun setUseCase(useCase: ImageCapture) {
        imageCapture = useCase
    }

    fun takePicture(context: Context, executor: Executor, onImageSaved: (File) -> Unit, onError: (Exception) -> Unit) {
        val imageCapture = imageCapture ?: return
        
        val photoFile = File(
            context.getExternalFilesDir(null),
            "cat_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    onError(exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
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
    val statusMessage by sessionManager.statusMessage.collectAsState()
    
    // State for detections to draw
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var sourceResolution by remember { mutableStateOf(android.util.Size(1, 1)) }

    // Camera Executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    captureController.setUseCase(imageCapture)

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val bitmap = imageProxy.toBitmap()
                        
                        // Rotate bitmap if needed to match preview
                        val matrix = Matrix()
                        matrix.postRotate(rotationDegrees.toFloat())
                        val rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )

                        // Update source resolution for overlay scaling
                        val currentSize = android.util.Size(rotatedBitmap.width, rotatedBitmap.height)
                        
                        val results = detectorHelper.detect(rotatedBitmap)
                        
                        // Pass to session manager
                        sessionManager.processDetections(results)
                        
                        // Update UI state
                        previewView.post {
                            sourceResolution = currentSize
                            detections = results
                        }
                        
                        imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            imageAnalyzer
                        )
                    } catch (exc: Exception) {
                        Log.e("MonitorScreen", "Use case binding failed", exc)
                    }

                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            }
        )

        // Overlay
        DetectionOverlay(
            detections = detections,
            sourceWidth = sourceResolution.width,
            sourceHeight = sourceResolution.height,
            modifier = Modifier.fillMaxSize()
        )

        // Status Text
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .padding(top = 32.dp) // Status bar padding
        )
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
        // Calculate scale factors
        // Assuming FILL_CENTER (cropping) or FIT_CENTER (letterboxing).
        // PreviewView defaults to FILL_CENTER usually.
        // We need to know the view size.
        val viewWidth = size.width
        val viewHeight = size.height
        
        // Calculate scale to fit/fill
        val scaleX = viewWidth / sourceWidth
        val scaleY = viewHeight / sourceHeight
        
        // For FILL_CENTER, we take the max scale
        val scale = kotlin.math.max(scaleX, scaleY)
        
        // Calculate offsets to center
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val offsetX = (viewWidth - scaledWidth) / 2
        val offsetY = (viewHeight - scaledHeight) / 2

        for (result in detections) {
            val box = result.boundingBox
            
            // Transform coordinates
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
            
            // Draw Label
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
