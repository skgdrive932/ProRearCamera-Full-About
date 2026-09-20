package com.example.prorearcam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var txtZoomLevel: TextView
    private lateinit var gridOverlay: TableLayout
    private lateinit var proSlidersCard: LinearLayout

    private lateinit var btnFlash: TextView
    private lateinit var btnGrid: TextView
    private lateinit var btnAspectRatio: TextView
    private lateinit var aboutButton: TextView

    private lateinit var modePhoto: TextView
    private lateinit var modePortrait: TextView
    private lateinit var modeMacro: TextView
    private lateinit var modePro: TextView

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lastSavedUri: Uri? = null
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var is169Ratio = false
    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View Binding
        viewFinder = findViewById(R.id.viewFinder)
        imgPreview = findViewById(R.id.imgPreview)
        btnCapture = findViewById(R.id.btnCapture)
        zoomSlider = findViewById(R.id.zoomSlider)
        evSlider = findViewById(R.id.evSlider)
        txtZoomLevel = findViewById(R.id.txtZoomLevel)
        gridOverlay = findViewById(R.id.gridOverlay)
        proSlidersCard = findViewById(R.id.proSlidersCard)

        btnFlash = findViewById(R.id.btnFlash)
        btnGrid = findViewById(R.id.btnGrid)
        btnAspectRatio = findViewById(R.id.btnAspectRatio)
        aboutButton = findViewById(R.id.aboutButton)

        modePhoto = findViewById(R.id.modePhoto)
        modePortrait = findViewById(R.id.modePortrait)
        modeMacro = findViewById(R.id.modeMacro)
        modePro = findViewById(R.id.modePro)

        aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        btnCapture.setOnClickListener { takePhoto() }

        // Tap on circular photo thumbnail to open Gallery preview
        imgPreview.setOnClickListener {
            lastSavedUri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(intent)
            } ?: Toast.makeText(this, "Pehle photo click karein", Toast.LENGTH_SHORT).show()
        }

        setupProControls()
        setupModeSwitchers()

        if (allPermissionsGranted()) {
            startCamera(isMacroLens = false)
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun setupProControls() {
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

        btnGrid.setOnClickListener {
            gridOverlay.visibility = if (gridOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnAspectRatio.setOnClickListener {
            is169Ratio = !is169Ratio
            btnAspectRatio.text = if (is169Ratio) "16:9" else "4:3"
            startCamera(isMacroLens = false)
        }

        // Tap-to-focus
        viewFinder.setOnTouchListener { _, event ->
            val factory = viewFinder.meteringPointFactory
            val point = factory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder(point).build()
            camera?.cameraControl?.startFocusAndMetering(action)
            true
        }
    }

    private fun setupModeSwitchers() {
        val modes = listOf(modePhoto, modePortrait, modeMacro, modePro)

        fun selectMode(selected: TextView) {
            modes.forEach { 
                it.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                it.alpha = 0.5f 
            }
            selected.setTextColor(0xFFFFD700.toInt())
            selected.alpha = 1.0f

            when (selected.id) {
                R.id.modePro -> {
                    proSlidersCard.visibility = View.VISIBLE
                    startCamera(isMacroLens = false)
                }
                R.id.modeMacro -> {
                    proSlidersCard.visibility = View.GONE
                    // Physical Macro Lens call
                    startCamera(isMacroLens = true)
                }
                R.id.modePortrait -> {
                    proSlidersCard.visibility = View.GONE
                    startCamera(isMacroLens = false)
                }
                else -> {
                    proSlidersCard.visibility = View.GONE
                    startCamera(isMacroLens = false)
                }
            }
        }

        modePhoto.setOnClickListener { selectMode(modePhoto) }
        modePortrait.setOnClickListener { selectMode(modePortrait) }
        modeMacro.setOnClickListener { selectMode(modeMacro) }
        modePro.setOnClickListener { selectMode(modePro) }
    }

    private fun startCamera(isMacroLens: Boolean = false) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val targetRatio = if (is169Ratio) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3

                val preview = Preview.Builder().setTargetAspectRatio(targetRatio).build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(targetRatio)
                    .setFlashMode(flashMode)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val availableCameras = cameraProvider.availableCameraInfos.filter {
                    it.lensFacing == CameraSelector.LENS_FACING_BACK
                }

                var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Hardware Micro Lens Selection Logic
                if (isMacroLens && availableCameras.size > 1) {
                    val macroSelector = CameraSelector.Builder()
                        .addCameraFilter { cameraInfos ->
                            cameraInfos.filter { info ->
                                info != cameraProvider.availableCameraInfos.firstOrNull { 
                                    it.lensFacing == CameraSelector.LENS_FACING_BACK 
                                }
                            }
                        }.build()

                    try {
                        if (cameraProvider.hasCamera(macroSelector)) {
                            cameraSelector = macroSelector
                        }
                    } catch (e: Exception) {
                        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    }
                }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

                setupZoomAndEV()

            } catch (exc: Exception) {
                Toast.makeText(this, "Camera load fail: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupZoomAndEV() {
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val linearZoom = progress / 100f
                camera?.cameraControl?.setLinearZoom(linearZoom)

                // Dynamic Zoom Display Range (1.0x se 8.0x)
                val zoomRatio = 1.0f + (linearZoom * 7.0f)
                txtZoomLevel.text = String.format(Locale.US, "%.1fx", zoomRatio)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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
                    Toast.makeText(baseContext, "Photo saved!", Toast.LENGTH_SHORT).show()
                    
                    lastSavedUri = outputFileResults.savedUri
                    lastSavedUri?.let { uri ->
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            imgPreview.setImageBitmap(bitmap)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Failed: ${exception.message}", Toast.LENGTH_SHORT).show()
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
                startCamera(isMacroLens = false)
            } else {
                Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
