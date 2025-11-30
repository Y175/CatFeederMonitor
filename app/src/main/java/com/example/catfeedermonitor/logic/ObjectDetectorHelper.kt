package com.example.catfeedermonitor.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

// INSTRUCTION: Place your 'model.tflite' file in the 'app/src/main/assets/' directory.

data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val score: Float
)

data class DetectionFrameResult(
    val detections: List<DetectionResult>,
    val inferenceTime: Long
)

class ObjectDetectorHelper(context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = listOf("putong", "sunny")
    private val inputSize = 640
    private val confidenceThreshold = 0.8f
    private val iouThreshold = 0.5f

    init {
        val modelFile = FileUtil.loadMappedFile(context, "model.tflite")
        val options = Interpreter.Options()
        interpreter = Interpreter(modelFile, options)
    }

    @Synchronized
    fun detect(bitmap: Bitmap): DetectionFrameResult {
        if (interpreter == null) return DetectionFrameResult(emptyList(), 0)

        val startTime = System.currentTimeMillis()

        // 1. Preprocess
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(org.tensorflow.lite.support.common.ops.NormalizeOp(0f, 255f)) 
            .build()

        var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Run Inference
        val outputShape = intArrayOf(1, 6, 8400)
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)
        
        interpreter?.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // 3. Post-process
        val outputArray = outputBuffer.floatArray
        val results = ArrayList<DetectionResult>()
        
        val numAnchors = 8400
        
        for (i in 0 until numAnchors) {
            val score0 = outputArray[4 * numAnchors + i]
            val score1 = outputArray[5 * numAnchors + i]
            
            var maxScore = score0
            var labelIndex = 0
            if (score1 > score0) {
                maxScore = score1
                labelIndex = 1
            }

            if (maxScore > confidenceThreshold) {
                val cx = outputArray[0 * numAnchors + i]
                val cy = outputArray[1 * numAnchors + i]
                val w = outputArray[2 * numAnchors + i]
                val h = outputArray[3 * numAnchors + i]

                val left = (cx - w / 2) * bitmap.width / inputSize
                val top = (cy - h / 2) * bitmap.height / inputSize
                val right = (cx + w / 2) * bitmap.width / inputSize
                val bottom = (cy + h / 2) * bitmap.height / inputSize

                results.add(
                    DetectionResult(
                        boundingBox = RectF(left, top, right, bottom),
                        label = labels[labelIndex],
                        score = maxScore
                    )
                )
            }
        }

        val finalResults = applyNMS(results)
        val endTime = System.currentTimeMillis()
        
        return DetectionFrameResult(finalResults, endTime - startTime)
    }

    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        val pq = PriorityQueue<DetectionResult> { o1, o2 -> o2.score.compareTo(o1.score) }
        pq.addAll(detections)

        val selected = ArrayList<DetectionResult>()
        while (pq.isNotEmpty()) {
            val current = pq.poll()
            selected.add(current)

            val iterator = pq.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(current.boundingBox, next.boundingBox) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selected
    }

    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val xA = max(boxA.left, boxB.left)
        val yA = max(boxA.top, boxB.top)
        val xB = min(boxA.right, boxB.right)
        val yB = min(boxA.bottom, boxB.bottom)

        val interArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
        val boxBArea = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)

        return interArea / (boxAArea + boxBArea - interArea)
    }
    
    fun close() {
        interpreter?.close()
    }
}
