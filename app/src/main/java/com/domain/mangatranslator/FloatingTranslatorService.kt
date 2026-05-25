package com.domain.mangatranslator

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*

class FloatingTranslatorService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var dataIntent: Intent? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("RESULT_CODE")) {
            resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("DATA_INTENT", Intent::class.java)
            } else {
                intent.getParcelableExtra("DATA_INTENT")
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(floatingView, params)
        
        floatingView.findViewById<Button>(R.id.btnTerjemah).setOnClickListener { 
            performCapture() 
        }
    }

    private fun performCapture() {
        if (resultCode != Activity.RESULT_OK || dataIntent == null) {
            Toast.makeText(this, "Izin rekam layar tidak ditemukan!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (mediaProjection == null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, dataIntent!!)
            }

            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            
            // Menggunakan ImageReader yang lebih stabil untuk Android 14
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "Capture", width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(image.planes[0].buffer)
                    image.close()
                    stopCapture()
                    
                    // Proses di sini
                    Toast.makeText(this, "Gambar berhasil ditangkap!", Toast.LENGTH_SHORT).show()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Toast.makeText(this, "Error: " + e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
    }
    
    override fun onBind(intent: Intent?) = null
}
