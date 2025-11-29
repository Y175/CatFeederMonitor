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

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) return emptyList()

        // 1. Preprocess
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            // Add normalization if needed (e.g., / 255.0). 
            // YOLOv8 usually expects [0, 1] float input.
            // If the model is int8 quantized, it might expect uint8 [0, 255] or int8 [-128, 127].
            // Assuming standard float input for simplicity or that TensorImage handles basic conversion.
            // If the model is strictly int8 input, we might need specific normalization.
            // Given "model.tflite", it likely takes quantized inputs or dequantizes internally.
            // We'll assume float input [0,1] is handled or we pass normalized data.
            // For safety with TFLite Support, we often use NormalizeOp if we know mean/std.
            // YOLOv8 default: 0-255 -> 0.0-1.0.
            .add(org.tensorflow.lite.support.common.ops.NormalizeOp(0f, 255f)) 
            .build()

        var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Run Inference
        // Output shape: [1, 6, 8400] -> [1, 4+num_classes, anchors]
        val outputShape = intArrayOf(1, 6, 8400)
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)
        
        interpreter?.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // 3. Post-process
        val outputArray = outputBuffer.floatArray
        val results = ArrayList<DetectionResult>()

        // The output is flattened. Shape [1, 6, 8400].
        // Stride = 8400.
        // We want to iterate over anchors (columns).
        // Row 0: cx, Row 1: cy, Row 2: w, Row 3: h, Row 4: score0, Row 5: score1
        
        val numAnchors = 8400
        val numChannels = 6
        
        // Accessing outputArray[channel * numAnchors + anchorIndex]
        
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

                val left = (cx - w / 2) * bitmap.width / inputSize // Scale back to original image
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

        return applyNMS(results)
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
