package com.domain.mangatranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast

// PERBAIKAN: Menggunakan Activity bawaan asli Android agar tidak bentrok dengan tema
class MainActivity : Activity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234
    private val MEDIA_PROJECTION_REQ_CODE = 5678

    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inisialisasi pengelola perekaman layar sistem Android
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        // Aksi ketika tombol Memulai ditekan
        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                requestMediaProjectionPermission()
            } else {
                requestOverlayPermission()
            }
        }

        // Aksi ketika tombol Berhenti ditekan
        btnStop.setOnClickListener {
            stopFloatingService()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    private fun requestMediaProjectionPermission() {
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQ_CODE
        )
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, FloatingTranslatorService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("DATA_INTENT", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Layanan Terjemahan Dimulai", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingTranslatorService::class.java)
        stopService(intent)
        Toast.makeText(this, "Layanan Terjemahan Dihentikan", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (checkOverlayPermission()) {
                requestMediaProjectionPermission()
            } else {
                Toast.makeText(this, "Izin Overlay diperlukan untuk aplikasi ini!", Toast.LENGTH_SHORT).show()
            }
        }
        
        if (requestCode == MEDIA_PROJECTION_REQ_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startFloatingService(resultCode, data)
            } else {
                Toast.makeText(this, "Izin Merekam Layar ditolak!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
