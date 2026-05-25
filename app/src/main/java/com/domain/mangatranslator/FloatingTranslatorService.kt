package com.domain.mangatranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class FloatingTranslatorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? {
        return null // Kita tidak menggunakan binding service
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Mencegah aplikasi crash di Android 8.0 ke atas dengan membuat Notifikasi Latar Belakang
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "manga_translator_channel"
            val channel = NotificationChannel(
                channelId,
                "Layanan Penerjemah",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Penerjemah Komik Aktif")
                .setContentText("Menu mengambang sedang berjalan di latar belakang.")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()

            startForeground(1, notification)
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Memanggil desain tampilan layout_floating_menu.xml yang sudah Anda buat sebelumnya
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)

        // Mengatur parameter agar menu bisa mengambang di atas aplikasi lain
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Posisi awal menu (kiri atas)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // Memunculkan menu ke layar
        windowManager.addView(floatingView, params)

        setupButtons()
    }

    private fun setupButtons() {
        val btnTerjemah = floatingView.findViewById<Button>(R.id.btnTerjemah)
        val btnSuara = floatingView.findViewById<Button>(R.id.btnSuara)
        val btnScroll = floatingView.findViewById<Button>(R.id.btnScroll)

        // Logika saat tombol ditekan (saat ini baru memunculkan pesan singkat)
        btnTerjemah.setOnClickListener {
            Toast.makeText(this, "Mulai mengambil gambar layar untuk diterjemahkan...", Toast.LENGTH_SHORT).show()
        }

        btnSuara.setOnClickListener {
            Toast.makeText(this, "Mulai membacakan teks...", Toast.LENGTH_SHORT).show()
        }

        btnScroll.setOnClickListener {
            Toast.makeText(this, "Fitur Scroll Otomatis diaktifkan...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Menghapus menu dari layar saat tombol "Berhenti" di aplikasi ditekan
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}

