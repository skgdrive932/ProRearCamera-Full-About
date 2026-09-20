package com.example.prorearcam

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.prorearcam.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // उदाहरण स्वरूप बटन क्लिक लिस्टनर (यदि आपके लेआउट में aboutButton है):
        val aboutButton = findViewById<Button>(R.id.aboutButton)
        aboutButton?.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }
}
