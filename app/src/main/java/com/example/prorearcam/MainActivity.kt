package com.example.prorearcam

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.SeekBar
import android.widget.TextView
import android.content.Intent
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {
    private lateinit var preview: TextureView
    private lateinit var shutterText: TextView
    private lateinit var isoText: TextView
    private lateinit var exposureText: TextView
    private lateinit var flashButton: TextView
    private lateinit var focusButton: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var request: CaptureRequest.Builder? = null
    private var characteristics: CameraCharacteristics? = null
    private var iso = 0
    private var ev = 0
    private var manualIso = false
    private var manualShutter = false
    private var manualFocus = false
    private var flash = CaptureRequest.CONTROL_AE_MODE_ON
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.preview)
        shutterText = findViewById(R.id.shutterText)
        isoText = findViewById(R.id.isoText)
        exposureText = findViewById(R.id.exposureText)
        flashButton = findViewById(R.id.flashButton)
        focusButton = findViewById(R.id.focusButton)

        findViewById<SeekBar>(R.id.exposureBar).apply {
            max = 40; progress = 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, u: Boolean) { ev = p - 20; exposureText.text = "Exposure: ${ev / 10f} EV"; apply() }
                override fun onStartTrackingTouch(b: SeekBar) {}
                override fun onStopTrackingTouch(b: SeekBar) {}
            })
        }
        findViewById<SeekBar>(R.id.isoBar).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, u: Boolean) {
                    val r = characteristics?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return
                    manualIso = p > 0
                    iso = r.lower + (r.upper - r.lower) * p / 100
                    isoText.text = if (manualIso) "ISO: $iso" else "ISO: Auto"
                    apply()
                }
                override fun onStartTrackingTouch(b: SeekBar) {}
                override fun onStopTrackingTouch(b: SeekBar) {}
            })
        }
        findViewById<SeekBar>(R.id.shutterBar).apply {
            max = 13
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, u: Boolean) {
                    manualShutter = p > 0
                    shutterText.text = if (manualShutter) "Shutter: 1/${1 shl p}s" else "Shutter: Auto"
                    apply()
                }
                override fun onStartTrackingTouch(b: SeekBar) {}
                override fun onStopTrackingTouch(b: SeekBar) {}
            })
        }
        findViewById<TextView>(R.id.aboutButton).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        flashButton.setOnClickListener {
            flash = when (flash) {
                CaptureRequest.CONTROL_AE_MODE_ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                else -> CaptureRequest.CONTROL_AE_MODE_ON
            }
            flashButton.text = when (flash) {
                CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "FLASH ON"
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH -> "FLASH AUTO"
                else -> "FLASH OFF"
            }
            apply()
        }
        focusButton.setOnClickListener { manualFocus = !manualFocus; focusButton.text = if (manualFocus) "FOCUS MANUAL" else "FOCUS AUTO"; apply() }
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = openRearCamera()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
    }

    private fun openRearCamera() {
        val manager = getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: return
        characteristics = manager.getCameraCharacteristics(id)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(c: CameraDevice) { camera = c; startPreview() }
            override fun onDisconnected(c: CameraDevice) { c.close() }
            override fun onError(c: CameraDevice, e: Int) { c.close() }
        }, handler)
    }

    private fun startPreview() {
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(preview.width, preview.height)
        val surface = Surface(texture)
        val c = camera ?: return
        request = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
        c.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) { session = s; apply() }
            override fun onConfigureFailed(s: CameraCaptureSession) {}
        }, handler)
    }

    private fun apply() {
        val b = request ?: return
        val s = session ?: return
        b.set(CaptureRequest.CONTROL_AE_MODE, flash)
        b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev)
        if (manualIso && iso > 0) { b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF); b.set(CaptureRequest.SENSOR_SENSITIVITY, iso) }
        if (manualFocus) b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF) else b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        s.setRepeatingRequest(b.build(), null, handler)
    }

    override fun onDestroy() { session?.close(); camera?.close(); super.onDestroy() }
}
