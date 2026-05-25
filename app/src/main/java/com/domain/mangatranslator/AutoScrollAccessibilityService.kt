package com.domain.mangatranslator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AutoScrollAccessibilityService : AccessibilityService() {

    // Membuat objek instance agar bisa dipanggil dari Menu Mengambang
    companion object {
        var instance: AutoScrollAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Dibiarkan kosong karena kita hanya butuh fitur gestur, bukan membaca event layar
    }

    override fun onInterrupt() {
        // Terpanggil jika sistem Android menghentikan layanan secara paksa
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // Fungsi ini akan menjalankan gestur usap layar (Swipe) dari bawah ke atas
    fun scrollUp() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // Menentukan titik koordinat sentuhan jari virtual (X dan Y)
        val startX = screenWidth / 2
        val startY = screenHeight * 0.8f // Mulai dari 80% layar bawah
        val endY = screenHeight * 0.2f   // Tarik hingga 20% layar atas

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)

        val gestureBuilder = GestureDescription.Builder()
        // Angka 500 adalah kecepatan usapan jari dalam milidetik (0.5 detik)
        val stroke = GestureDescription.StrokeDescription(path, 0, 500)
        gestureBuilder.addStroke(stroke)

        dispatchGesture(gestureBuilder.build(), null, null)
    }
}

