package com.example.prorearcam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
// ViewBinding fix (Iske liye gradle build process complete hona chahiye)
import com.example.prorearcam.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // ViewBinding instantiation fix
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    // VideoCapture correct generic type fix
    private var videoCapture: VideoCapture<Recorder>? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var isFlashOn = false
    private var currentZoomRatio: Float = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding correct usage
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        setupListeners()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupListeners() {
        // XML View reference fix using Binding
        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnFlash.setOnClickListener { toggleFlash() }

        binding.zoom1x.setOnClickListener { setZoom(1.0f) }
        binding.zoom2x.setOnClickListener { setZoom(2.0f) }
        binding.zoom06x.setOnClickListener { setZoom(0.6f) }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            // STRICT REAR CAMERA ONLY SELECTOR
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                // Sahi toast syntax
                Toast.makeText(this, "Camera initialization failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun setZoom(ratio: Float) {
        currentZoomRatio = ratio
        // Direct control of rear camera lens
        camera?.cameraControl?.setZoomRatio(ratio)
        // Toast syntax fix
        Toast.makeText(this, "Zoom set to ${ratio}x", Toast.LENGTH_SHORT).show()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProRearCam-Images")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(baseContext, "Photo saved to gallery!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun toggleFlash() {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                isFlashOn = !isFlashOn
                cam.cameraControl.enableTorch(isFlashOn)
            } else {
                Toast.makeText(this, "Flash unit not available on rear camera", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
