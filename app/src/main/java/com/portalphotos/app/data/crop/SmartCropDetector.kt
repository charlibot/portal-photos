package com.portalphotos.app.data.crop

import android.graphics.Bitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable

class SmartCropDetector : Closeable {

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        ObjectDetection.getClient(options)
    }

    /**
     * Calculates the primary visual focal point normalized from (0.0, 0.0) top-left to (1.0, 1.0) bottom-right.
     */
    suspend fun calculateFocalPoint(bitmap: Bitmap): Offset = withContext(Dispatchers.Default) {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext Offset(0.5f, 0.5f)
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Pass 1: Google ML Kit Face Detection
        try {
            val faces = faceDetector.process(inputImage).await()
            if (faces.isNotEmpty()) {
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                val centerX = primaryFace.boundingBox.exactCenterX() / bitmap.width.toFloat()
                val centerY = primaryFace.boundingBox.exactCenterY() / bitmap.height.toFloat()
                return@withContext Offset(centerX.coerceIn(0f, 1f), centerY.coerceIn(0f, 1f))
            }
        } catch (e: Exception) {
            // Fallback to Object Detector
        }

        // Pass 2: Google ML Kit Object & Artwork Subject Detection
        try {
            val objects = objectDetector.process(inputImage).await()
            if (objects.isNotEmpty()) {
                val primaryObject = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                val centerX = primaryObject.boundingBox.exactCenterX() / bitmap.width.toFloat()
                val centerY = primaryObject.boundingBox.exactCenterY() / bitmap.height.toFloat()
                return@withContext Offset(centerX.coerceIn(0f, 1f), centerY.coerceIn(0f, 1f))
            }
        } catch (e: Exception) {
            // Fallback to Saliency Map
        }

        // Pass 3: Saliency & High-Frequency Contrast Edge Energy Map
        calculateSaliencyFocalPoint(bitmap)
    }

    private fun calculateSaliencyFocalPoint(bitmap: Bitmap): Offset {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= 10 || height <= 10) return Offset(0.5f, 0.5f)

        var maxEnergy = -1.0
        var bestX = 0.5f
        var bestY = 0.5f

        val stepX = width / 3
        val stepY = height / 3

        for (row in 0..2) {
            for (col in 0..2) {
                val startX = col * stepX
                val startY = row * stepY
                val endX = (startX + stepX).coerceAtMost(width)
                val endY = (startY + stepY).coerceAtMost(height)

                var totalLuminance = 0.0
                var pixelCount = 0

                for (x in startX until endX step 8) {
                    for (y in startY until endY step 8) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val lum = 0.299 * r + 0.587 * g + 0.114 * b
                        totalLuminance += lum
                        pixelCount++
                    }
                }

                val avgLum = if (pixelCount > 0) totalLuminance / pixelCount else 128.0
                val energy = Math.abs(avgLum - 128.0)
                if (energy > maxEnergy) {
                    maxEnergy = energy
                    bestX = (startX + (stepX / 2f)) / width
                    bestY = (startY + (stepY / 2f)) / height
                }
            }
        }

        return Offset(bestX.coerceIn(0f, 1f), bestY.coerceIn(0f, 1f))
    }

    override fun close() {
        try {
            faceDetector.close()
            objectDetector.close()
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        fun toBiasAlignment(focalPoint: Offset): Alignment {
            val biasX = (focalPoint.x * 2f) - 1f
            val biasY = (focalPoint.y * 2f) - 1f
            return BiasAlignment(biasX.coerceIn(-1f, 1f), biasY.coerceIn(-1f, 1f))
        }
    }
}
