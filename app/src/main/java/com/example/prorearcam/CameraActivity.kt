package com.example.prorearcam

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val shutterBtn = findViewById<View>(R.id.btn_shutter)
        val switchCameraBtn = findViewById<ImageView>(R.id.btn_switch_camera)
        val cameraPreview = findViewById<FrameLayout>(R.id.camera_preview)

        shutterBtn?.setOnClickListener {
            // Photo capture logic
        }

        switchCameraBtn?.setOnClickListener {
            // Switch camera logic
        }
    }
}
