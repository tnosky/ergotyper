package com.example.ergotyper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import org.tensorflow.lite.support.image.TensorImage
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var objectDetector: ObjectDetector
    // track last known hand positions
    private var lastLeftHand: Pair<Float, Float>? = null
    private var lastRightHand: Pair<Float, Float>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            val options = ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.5f)
                .build()
            objectDetector = ObjectDetector.createFromFileAndOptions(
                this,
                "model.tflite",
                options
            )
        } catch (e: Exception) {
            Log.e("ErgoTyper", "Model load failed: ${e.message}")
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // capture key presses from external keyboard
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keyLabel = event?.displayLabel?.toString() ?: "?"
        val timestamp = System.currentTimeMillis()

        // sync: log key + last known hand positions
        Log.d("ErgoTyper", "KEY_EVENT: Key=$keyLabel, Time=$timestamp, " +
                "Left=${lastLeftHand?.first ?: 0},${lastLeftHand?.second ?: 0}, " +
                "Right=${lastRightHand?.first ?: 0},${lastRightHand?.second ?: 0}")

        return super.onKeyDown(keyCode, event)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("ErgoTyper", "Camera binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        if (bitmap != null) {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val results = objectDetector.detect(tensorImage)
            val points = mutableListOf<Pair<Float, Float>>()

            for (detection in results) {
                val box = detection.boundingBox
                points.add(Pair(box.centerX(), box.centerY()))
            }
            points.sortBy { it.first }

            // update global state
            if (points.size >= 2) {
                lastLeftHand = points[0]
                lastRightHand = points[1]
                Log.d("ErgoTyper", "HAND_POS: Left=${points[0]}, Right=${points[1]}")
            } else if (points.size == 1) {

                if (points[0].first < bitmap.width / 2) lastLeftHand = points[0]
                else lastRightHand = points[0]
                Log.d("ErgoTyper", "HAND_POS: Single=${points[0]}")
            }
        }
        imageProxy.close()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera() else Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
        }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}