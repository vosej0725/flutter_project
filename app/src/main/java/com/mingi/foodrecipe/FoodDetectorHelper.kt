package com.mingi.foodrecipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val sourceWidth: Int = 640,
    val sourceHeight: Int = 640
)

class FoodDetectorHelper(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "best_float32.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val CONFIDENCE_THRESHOLD = 0.4f
        private const val IOU_THRESHOLD = 0.45f
        private const val NUM_COORDS = 4
    }

    private enum class InputLayout { NHWC, NCHW }

    private data class ModelSpec(
        val inputWidth: Int,
        val inputHeight: Int,
        val inputLayout: InputLayout,
        val outputChannels: Int,
        val outputAnchors: Int,
        val outputChannelsFirst: Boolean
    )

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var modelSpec: ModelSpec? = null

    var labels: List<String> = emptyList()
        private set

    fun setup() {
        labels = AssetUtils.loadLabels(context, LABELS_FILE)
        interpreter = buildInterpreter()
    }

    private fun buildInterpreter(): Interpreter {
        val model = AssetUtils.loadModelFile(context, MODEL_FILE)
        return try {
            gpuDelegate = GpuDelegate()
            val options = Interpreter.Options().addDelegate(gpuDelegate!!)
            Interpreter(model, options)
        } catch (e: Throwable) {
            gpuDelegate = null
            Interpreter(model, Interpreter.Options())
        }.also { interp ->
            val inputShape = interp.getInputTensor(0).shape().toList()
            val outputShape = interp.getOutputTensor(0).shape().toList()
            modelSpec = parseModelSpec(inputShape, outputShape)
            Log.d("FoodDetector", "input shape: $inputShape")
            Log.d("FoodDetector", "output shape: $outputShape")
            Log.d("FoodDetector", "model spec: $modelSpec")
        }
    }

    private fun parseModelSpec(inputShape: List<Int>, outputShape: List<Int>): ModelSpec {
        require(inputShape.size == 4 && inputShape[0] == 1) {
            "Unsupported input shape: $inputShape"
        }
        require(outputShape.size == 3 && outputShape[0] == 1) {
            "Unsupported output shape: $outputShape"
        }

        val inputSpec = when {
            inputShape[3] == 3 -> Triple(inputShape[2], inputShape[1], InputLayout.NHWC)
            inputShape[1] == 3 -> Triple(inputShape[3], inputShape[2], InputLayout.NCHW)
            else -> error("Unsupported input layout: $inputShape")
        }

        val expectedChannels = NUM_COORDS + labels.size
        val outputChannelsFirst = when {
            outputShape[1] == expectedChannels -> true
            outputShape[2] == expectedChannels -> false
            else -> error("Unsupported output channels: $outputShape, expected $expectedChannels")
        }

        return ModelSpec(
            inputWidth = inputSpec.first,
            inputHeight = inputSpec.second,
            inputLayout = inputSpec.third,
            outputChannels = if (outputChannelsFirst) outputShape[1] else outputShape[2],
            outputAnchors = if (outputChannelsFirst) outputShape[2] else outputShape[1],
            outputChannelsFirst = outputChannelsFirst
        )
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val interp = checkNotNull(interpreter) { "setup() must be called before detect()." }
        val spec = checkNotNull(modelSpec) { "Model spec is not initialized." }
        val resized = Bitmap.createScaledBitmap(bitmap, spec.inputWidth, spec.inputHeight, true)
        val inputBuffer = bitmapToFloatArray(resized, spec)
        val rawOutput = createOutputBuffer(spec)

        interp.run(inputBuffer, rawOutput)
        val output = normalizeOutput(rawOutput, spec)

        val maxConfPerClass = FloatArray(labels.size)
        var globalMaxConf = 0f
        var globalMaxAnchor = -1
        var globalMaxClass = -1
        for (i in 0 until spec.outputAnchors) {
            for (c in labels.indices) {
                val confidence = output[NUM_COORDS + c][i]
                if (confidence > maxConfPerClass[c]) maxConfPerClass[c] = confidence
                if (confidence > globalMaxConf) {
                    globalMaxConf = confidence
                    globalMaxAnchor = i
                    globalMaxClass = c
                }
            }
        }
        Log.d("FoodDetector", "max conf per class: ${labels.zip(maxConfPerClass.toList())}")
        Log.d("FoodDetector", "global max conf=$globalMaxConf class=$globalMaxClass anchor=$globalMaxAnchor")

        val results = postProcess(output, bitmap.width, bitmap.height, spec)
        Log.d("FoodDetector", "postProcess results: ${results.size}, $results")
        return results
    }

    private fun bitmapToFloatArray(bitmap: Bitmap, spec: ModelSpec): Any {
        val pixels = IntArray(spec.inputWidth * spec.inputHeight)
        bitmap.getPixels(pixels, 0, spec.inputWidth, 0, 0, spec.inputWidth, spec.inputHeight)

        return when (spec.inputLayout) {
            InputLayout.NHWC -> {
                val input = Array(1) { Array(spec.inputHeight) { Array(spec.inputWidth) { FloatArray(3) } } }
                for (y in 0 until spec.inputHeight) {
                    for (x in 0 until spec.inputWidth) {
                        val px = pixels[y * spec.inputWidth + x]
                        input[0][y][x][0] = ((px shr 16) and 0xFF) / 255f
                        input[0][y][x][1] = ((px shr 8) and 0xFF) / 255f
                        input[0][y][x][2] = (px and 0xFF) / 255f
                    }
                }
                input
            }
            InputLayout.NCHW -> {
                val input = Array(1) { Array(3) { Array(spec.inputHeight) { FloatArray(spec.inputWidth) } } }
                for (y in 0 until spec.inputHeight) {
                    for (x in 0 until spec.inputWidth) {
                        val px = pixels[y * spec.inputWidth + x]
                        input[0][0][y][x] = ((px shr 16) and 0xFF) / 255f
                        input[0][1][y][x] = ((px shr 8) and 0xFF) / 255f
                        input[0][2][y][x] = (px and 0xFF) / 255f
                    }
                }
                input
            }
        }
    }

    private fun createOutputBuffer(spec: ModelSpec): Any {
        return if (spec.outputChannelsFirst) {
            Array(1) { Array(spec.outputChannels) { FloatArray(spec.outputAnchors) } }
        } else {
            Array(1) { Array(spec.outputAnchors) { FloatArray(spec.outputChannels) } }
        }
    }

    private fun normalizeOutput(rawOutput: Any, spec: ModelSpec): Array<FloatArray> {
        @Suppress("UNCHECKED_CAST")
        return if (spec.outputChannelsFirst) {
            (rawOutput as Array<Array<FloatArray>>)[0]
        } else {
            val anchorsFirst = (rawOutput as Array<Array<FloatArray>>)[0]
            Array(spec.outputChannels) { channel ->
                FloatArray(spec.outputAnchors) { anchor -> anchorsFirst[anchor][channel] }
            }
        }
    }

    private fun postProcess(
        output: Array<FloatArray>,
        origWidth: Int,
        origHeight: Int,
        spec: ModelSpec
    ): List<DetectionResult> {
        val scaleX = origWidth.toFloat() / spec.inputWidth
        val scaleY = origHeight.toFloat() / spec.inputHeight
        val candidates = mutableListOf<DetectionResult>()

        for (i in 0 until spec.outputAnchors) {
            val rawCx = output[0][i]
            val rawCy = output[1][i]
            val rawW = output[2][i]
            val rawH = output[3][i]
            val normalizedCoords = rawCx <= 1.5f && rawCy <= 1.5f && rawW <= 1.5f && rawH <= 1.5f

            val cx = if (normalizedCoords) rawCx * spec.inputWidth else rawCx
            val cy = if (normalizedCoords) rawCy * spec.inputHeight else rawCy
            val boxWidth = if (normalizedCoords) rawW * spec.inputWidth else rawW
            val boxHeight = if (normalizedCoords) rawH * spec.inputHeight else rawH

            var maxConf = 0f
            var maxIdx = -1
            for (c in labels.indices) {
                val conf = output[NUM_COORDS + c][i]
                if (conf > maxConf) {
                    maxConf = conf
                    maxIdx = c
                }
            }

            if (maxConf < CONFIDENCE_THRESHOLD || maxIdx < 0) continue

            candidates.add(
                DetectionResult(
                    label = labels[maxIdx],
                    confidence = maxConf,
                    boundingBox = RectF(
                        ((cx - boxWidth / 2f) * scaleX).coerceIn(0f, origWidth.toFloat()),
                        ((cy - boxHeight / 2f) * scaleY).coerceIn(0f, origHeight.toFloat()),
                        ((cx + boxWidth / 2f) * scaleX).coerceIn(0f, origWidth.toFloat()),
                        ((cy + boxHeight / 2f) * scaleY).coerceIn(0f, origHeight.toFloat())
                    ),
                    sourceWidth = origWidth,
                    sourceHeight = origHeight
                )
            )
        }

        return applyNMS(candidates)
    }

    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<DetectionResult>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(best.boundingBox, it.boundingBox) > IOU_THRESHOLD }
        }

        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, interRight - interLeft) *
            maxOf(0f, interBottom - interTop)
        if (interArea == 0f) return 0f

        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        return interArea / (aArea + bArea - interArea)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        modelSpec = null
    }
}
