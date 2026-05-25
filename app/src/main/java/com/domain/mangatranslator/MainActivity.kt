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
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

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
                // Jika izin overlay sudah ada, lanjut minta izin menangkap layar
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

    // Fungsi mengecek izin menu mengambang
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    // Fungsi meminta izin menu mengambang
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    // Fungsi meminta izin perekaman/penangkapan layar ponsel
    private fun requestMediaProjectionPermission() {
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQ_CODE
        )
    }

    // Fungsi menjalankan service dengan mengirimkan token izin rekam layar
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

    // Fungsi menghentikan menu mengambang
    private fun stopFloatingService() {
        val intent = Intent(this, FloatingTranslatorService::class.java)
        stopService(intent)
        Toast.makeText(this, "Layanan Terjemahan Dihentikan", Toast.LENGTH_SHORT).show()
    }

    // Menangani hasil keputusan pemberian izin dari pengguna
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Cek hasil izin overlay
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (checkOverlayPermission()) {
                requestMediaProjectionPermission()
            } else {
                Toast.makeText(this, "Izin Overlay diperlukan untuk aplikasi ini!", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Cek hasil izin tangkap layar
        if (requestCode == MEDIA_PROJECTION_REQ_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Jika disetujui, jalankan menu mengambang dan kirimkan token layarnya
                startFloatingService(resultCode, data)
            } else {
                Toast.makeText(this, "Izin Merekam Layar ditolak!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
