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
    private var isCapturing = false // Mencegah tombol dipencet berkali-kali

    private val overlayViews = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            dataIntent = intent.getParcelableExtra("DATA_INTENT") as? Intent
        }

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1, notification)
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("id", "ID")
            }
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)

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

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(floatingView, params)

        setupButtons()
    }

    private fun setupButtons() {
        val btnTerjemah = floatingView.findViewById<Button>(R.id.btnTerjemah)
        val btnSuara = floatingView.findViewById<Button>(R.id.btnSuara)
        val btnScroll = floatingView.findViewById<Button>(R.id.btnScroll)

        btnTerjemah.setOnClickListener {
            if (isCapturing) return@setOnClickListener
            isVoiceMode = false
            clearOldOverlays() 
            captureScreenAndProcess()
        }

        btnSuara.setOnClickListener {
            if (isCapturing) return@setOnClickListener
            isVoiceMode = true
            clearOldOverlays()
            if (textToSpeech?.isSpeaking == true) {
                textToSpeech?.stop()
            }
            captureScreenAndProcess()
        }

        btnScroll.setOnClickListener {
            val scrollService = AutoScrollAccessibilityService.instance
            if (scrollService != null) {
                scrollService.scrollUp()
            } else {
                Toast.makeText(this, "Aksesibilitas belum aktif. Buka Pengaturan HP.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun captureScreenAndProcess() {
        val intentData = dataIntent
        if (resultCode == Activity.RESULT_OK && intentData != null) {
            
            isCapturing = true
            floatingView.visibility = View.INVISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (mediaProjection == null) {
                        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intentData)
                    }

                    val display = windowManager.defaultDisplay
                    val size = Point()
                    display.getRealSize(size)
                    val width = size.x
                    val height = size.y

                    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                    virtualDisplay = mediaProjection?.createVirtualDisplay(
                        "ScreenCapture",
                        width, height, resources.displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader?.surface, null, null
                    )

                    // Memunculkan pesan ini berfungsi ganda untuk memaksa layar agar bergerak/refresh
                    Toast.makeText(this@FloatingTranslatorService, "Menjepret layar...", Toast.LENGTH_SHORT).show()

                    var imageProcessed = false

                    imageReader?.setOnImageAvailableListener({ reader ->
                        if (imageProcessed) return@setOnImageAvailableListener
                        val image = reader.acquireLatestImage()
                        
                        if (image != null) {
                            imageProcessed = true // Pastikan hanya memproses 1 gambar
                            
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            val bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            image.close()

                            stopScreenCapture()
                            floatingView.visibility = View.VISIBLE
                            isCapturing = false

                            Toast.makeText(this@FloatingTranslatorService, "Membaca teks...", Toast.LENGTH_SHORT).show()
                            
                            val ocrHelper = OCRHelper()
                            ocrHelper.extractTextFromBitmap(bitmap, object : OCRHelper.OCRListener {
                                override fun onSuccess(blocks: List<OCRHelper.TextBlockModel>) {
                                    Toast.makeText(this@FloatingTranslatorService, "Ditemukan ${blocks.size} balon teks. Menerjemahkan...", Toast.LENGTH_LONG).show()
                                    val translationHelper = TranslationHelper()
                                    
                                    for (block in blocks) {
                                        translationHelper.translateText(block.text, object : TranslationHelper.TranslationListener {
                                            override fun onSuccess(translatedText: String) {
                                                if (isVoiceMode) {
                                                    textToSpeech?.speak(translatedText, TextToSpeech.QUEUE_ADD, null, null)
                                                } else {
                                                    drawTextOverlay(translatedText, block.boundingBox)
                                                }
                                            }

                                            override fun onFailure(errorMessage: String) {}
                                        })
                                    }
                                }

                                override fun onFailure(errorMessage: String) {
                                    Toast.makeText(this@FloatingTranslatorService, errorMessage, Toast.LENGTH_SHORT).show()
                                }
                            })

                        }
                    }, Handler(Looper.getMainLooper()))

                } catch (e: Exception) {
                    e.printStackTrace()
                    isCapturing = false
                    floatingView.visibility = View.VISIBLE
                    Toast.makeText(this, "Gagal menangkap layar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, 200) // Sedikit memperlama jeda agar menu benar-benar hilang sebelum difoto
        } else {
            Toast.makeText(this, "Izin tangkap layar belum disetujui! Restart aplikasi.", Toast.LENGTH_LONG).show()
        }
    }

    private fun drawTextOverlay(text: String, rect: android.graphics.Rect) {
        val context = this
        Handler(Looper.getMainLooper()).post {
            val textView = TextView(context).apply {
                this.text = text
                this.setTextColor(Color.BLACK)
                this.setBackgroundColor(Color.WHITE)
                this.gravity = Gravity.CENTER
                this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                this.setPadding(4, 4, 4, 4)
            }

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                rect.width(),
                rect.height(),
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = rect.left
                y = rect.top
            }

            try {
                windowManager.addView(textView, params)
                overlayViews.add(textView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun clearOldOverlays() {
        for (view in overlayViews) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayViews.clear()
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        clearOldOverlays()
        stopScreenCapture()
        
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        
        mediaProjection?.stop()
        mediaProjection = null
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
