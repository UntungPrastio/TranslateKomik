package com.domain.mangatranslator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        // Aksi ketika tombol Memulai ditekan
        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }

        // Aksi ketika tombol Berhenti ditekan
        btnStop.setOnClickListener {
            stopFloatingService()
        }
    }

    // Fungsi untuk mengecek apakah izin memunculkan menu mengambang sudah aktif
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    // Fungsi untuk meminta izin menu mengambang ke sistem Android
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    // Fungsi untuk menjalankan menu mengambang di latar belakang
    private fun startFloatingService() {
        val intent = Intent(this, FloatingTranslatorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Layanan Terjemahan Dimulai", Toast.LENGTH_SHORT).show()
    }

    // Fungsi untuk menghentikan menu mengambang
    private fun stopFloatingService() {
        val intent = Intent(this, FloatingTranslatorService::class.java)
        stopService(intent)
        Toast.makeText(this, "Layanan Terjemahan Dihentikan", Toast.LENGTH_SHORT).show()
    }

    // Mengecek hasil setelah pengguna memberikan izin atau menolaknya
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                Toast.makeText(this, "Izin Overlay diperlukan untuk aplikasi ini!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

