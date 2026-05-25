package com.domain.mangatranslator

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class FloatingTranslatorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var mediaProjectionManager: MediaProjectionManager
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var dataIntent: Intent? = null

    private var textToSpeech: TextToSpeech? = null
    private var isVoiceMode = false
    private var isCapturing = false

    private val overlayViews = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("RESULT_CODE")) {
            resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("DATA_INTENT", Intent::class.java)
            } else {
                intent.getParcelableExtra("DATA_INTENT") as? Intent
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "manga_translator_channel"
            val channel = NotificationChannel(channelId, "Layanan", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Manga Translator Aktif").setSmallIcon(android.R.drawable.ic_menu_camera).build()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1, notification)
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        textToSpeech = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) textToSpeech?.language = Locale("id", "ID") }
        
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(floatingView, params)
        setupButtons()
    }

    private fun setupButtons() {
        floatingView.findViewById<Button>(R.id.btnTerjemah).setOnClickListener { 
            if (!isCapturing) { isVoiceMode = false; captureScreenAndProcess() }
        }
        floatingView.findViewById<Button>(R.id.btnSuara).setOnClickListener { 
            if (!isCapturing) { isVoiceMode = true; captureScreenAndProcess() }
        }
    }

    private fun captureScreenAndProcess() {
        if (resultCode != Activity.RESULT_OK || dataIntent == null) {
            Toast.makeText(this, "Izin rekam layar tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        isCapturing = true
        floatingView.visibility = View.INVISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (mediaProjection == null) mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, dataIntent!!)

                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels

                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCapture", width, height, resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null
                )

                imageReader?.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(image.planes[0].buffer)
                        image.close()
                        stopScreenCapture()
                        
                        Handler(Looper.getMainLooper()).post {
                            floatingView.visibility = View.VISIBLE
                            processImage(bitmap)
                            isCapturing = false
                        }
                    }
                }, Handler(Looper.getMainLooper()))

            } catch (e: Exception) {
                isCapturing = false
                floatingView.visibility = View.VISIBLE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, 500) // Penambahan jeda agar VirtualDisplay siap
    }

    private fun processImage(bitmap: Bitmap) {
        OCRHelper().extractTextFromBitmap(bitmap, object : OCRHelper.OCRListener {
            override fun onSuccess(blocks: List<OCRHelper.TextBlockModel>) {
                val translator = TranslationHelper()
                for (block in blocks) {
                    translator.translateText(block.text, object : TranslationHelper.TranslationListener {
                        override fun onSuccess(translatedText: String) {
                            if (isVoiceMode) textToSpeech?.speak(translatedText, TextToSpeech.QUEUE_ADD, null, null)
                            else drawTextOverlay(translatedText, block.boundingBox)
                        }
                        override fun onFailure(e: String) {}
                    })
                }
            }
            override fun onFailure(e: String) { Toast.makeText(this@FloatingTranslatorService, e, Toast.LENGTH_SHORT).show() }
        })
    }

    private fun drawTextOverlay(text: String, rect: android.graphics.Rect) {
        Handler(Looper.getMainLooper()).post {
            val tv = TextView(this).apply {
                this.text = text; this.setTextColor(Color.BLACK); this.setBackgroundColor(Color.WHITE)
                this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            val params = WindowManager.LayoutParams(
                rect.width(), rect.height(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
            ).apply { x = rect.left; y = rect.top }
            windowManager.addView(tv, params)
            overlayViews.add(tv)
        }
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.surface?.release(); imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        mediaProjection?.stop()
        textToSpeech?.shutdown()
        overlayViews.forEach { try { windowManager.removeView(it) } catch(e:Exception){} }
    }
}
