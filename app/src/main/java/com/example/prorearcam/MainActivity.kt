package com.example.prorearcam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var imgPreview: ImageView
    private lateinit var btnCapture: ImageButton
    private lateinit var zoomSlider: SeekBar
    private lateinit var evSlider: SeekBar
    private lateinit var gridOverlay: TableLayout
    
    // Top Bar TextView Pills
    private lateinit var btnFlash: TextView
    private lateinit var btnGrid: TextView
    private lateinit var btnAspectRatio: TextView
    private lateinit var aboutButton: TextView

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var is169Ratio = false
    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Components Binding
        viewFinder = findViewById(R.id.viewFinder)
        imgPreview = findViewById(R.id.imgPreview)
        btnCapture = findViewById(R.id.btnCapture)
        zoomSlider = findViewById(R.id.zoomSlider)
        evSlider = findViewById(R.id.evSlider)
        gridOverlay = findViewById(R.id.gridOverlay)

        // Top Bar TextView Binding
        btnFlash = findViewById(R.id.btnFlash)
        btnGrid = findViewById(R.id.btnGrid)
        btnAspectRatio = findViewById(R.id.btnAspectRatio)
        aboutButton = findViewById(R.id.aboutButton)

        // Navigation
        aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // Shutter Button
        btnCapture.setOnClickListener { 
            takePhoto() 
        }

        // Setup Pro Camera Controls
        setupProControls()

        // Camera Permission Check
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun setupProControls() {
        // Flash Mode Switcher (OFF -> AUTO -> ON)
        btnFlash.setOnClickListener {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> {
                    btnFlash.text = "⚡ AUTO"
                    ImageCapture.FLASH_MODE_AUTO
                }
                ImageCapture.FLASH_MODE_AUTO -> {
                    btnFlash.text = "⚡ ON"
                    ImageCapture.FLASH_MODE_ON
                }
                else -> {
                    btnFlash.text = "⚡ OFF"
                    ImageCapture.FLASH_MODE_OFF
                }
            }
            imageCapture?.flashMode = flashMode
        }

        // Grid Toggle (Show/Hide 3x3 Grid Overlay)
        btnGrid.setOnClickListener {
            gridOverlay.visibility = if (gridOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Aspect Ratio Toggle (4:3 <-> 16:9)
        btnAspectRatio.setOnClickListener {
            is169Ratio = !is169Ratio
            btnAspectRatio.text = if (is169Ratio) "16:9" else "4:3"
            startCamera() // Restart camera with new aspect ratio
        }

        // Tap to Focus Implementation
        viewFinder.setOnTouchListener { _, event ->
            val factory = viewFinder.meteringPointFactory
            val point = factory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder(point).build()
            camera?.cameraControl?.startFocusAndMetering(action)
            true
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val targetRatio = if (is169Ratio) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3

                val preview = Preview.Builder()
                    .setTargetAspectRatio(targetRatio)
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(targetRatio)
                    .setFlashMode(flashMode)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                setupZoomAndEV()

            } catch (exc: Exception) {
                Toast.makeText(this, "Camera error: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupZoomAndEV() {
        // Zoom Slider Configuration
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                camera?.cameraControl?.setLinearZoom(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Exposure Compensation (EV) Slider Configuration
        evSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val evRange = camera?.cameraInfo?.exposureState?.exposureCompensationRange
                if (evRange != null && evRange.contains(progress - 10)) {
                    camera?.cameraControl?.setExposureCompensationIndex(progress - 10)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "PRO_IMG_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProRearCamera")
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
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(baseContext, "Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
                    
                    // Update Circular Preview Thumbnail
                    outputFileResults.savedUri?.let { uri ->
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            imgPreview.setImageBitmap(bitmap)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
