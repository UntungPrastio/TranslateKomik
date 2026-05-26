package com.domain.mangatranslator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.Toast

class FloatingTranslatorService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(floatingView, params)

        // Cukup tes tombol apakah aplikasi masih hidup
        floatingView.findViewById<Button>(R.id.btnTerjemah).setOnClickListener {
            Toast.makeText(this, "Tombol Terjemah Ditekan! Aplikasi Hidup.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingView)
    }
}
