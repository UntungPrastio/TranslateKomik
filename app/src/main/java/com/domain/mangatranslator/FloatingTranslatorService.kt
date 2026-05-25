package com.domain.mangatranslator

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.view.Gravity
importimport android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class FloatingTranslatorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var mediaProjectionManager: MediaProjectionManager
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var dataIntent: Intent? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Menerima data token izin perekaman layar dari MainActivity
        if (intent != null) {
            resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            dataIntent = intent.getParcelableExtra("DATA_INTENT") as? Intent
        }

        // Mencegah aplikasi crash di Android 8.0 ke atas dengan Notifikasi Foreground
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
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Memanggil layout tampilan menu mengambang
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

        // Tombol Terjemah -> Sekarang menjalankan fungsi tangkap layar otomatis
        btnTerjemah.setOnClickListener {
            captureScreenAndProcess()
        }

        btnSuara.setOnClickListener {
            Toast.makeText(this, "Mulai membacakan teks...", Toast.LENGTH_SHORT).show()
        }

        btnScroll.setOnClickListener {
            val scrollService = AutoScrollAccessibilityService.instance
            if (scrollService != null) {
                scrollService.scrollUp()
            } else {
                Toast.makeText(this, "Tolong aktifkan layanan Aksesibilitas di Pengaturan HP Anda", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Fungsi Utama untuk Menangkap Gambar Layar HP
    private fun captureScreenAndProcess() {
        val intentData = dataIntent
        if (resultCode == Activity.RESULT_OK && intentData != null) {
            
            // Sembunyikan menu mengambang sementara agar tidak ikut terfoto di hasil screenshot
            floatingView.visibility = View.INVISIBLE

            // Beri jeda 100 milidetik agar menu benar-benar hilang dari layar sebelum dijepret
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (mediaProjection == null) {
                        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intentData)
                    }

                    // Mendapatkan ukuran resolusi layar asli perangkat asli HP
                    val display = windowManager.defaultDisplay
                    val size = Point()
                    display.getRealSize(size)
                    val width = size.x
                    val height = size.y

                    // Membuat wadah penampung gambar virtual
                    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                    virtualDisplay = mediaProjection?.createVirtualDisplay(
                        "ScreenCapture",
                        width, height, resources.displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader?.surface, null, null
                    )

                    // Memproses gambar saat sistem berhasil menangkap layar
                    imageReader?.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            // Mengubah buffer mentah menjadi data Bitmap Android siap pakai
                            val bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            image.close()

                            // Langsung hentikan penangkapan layar setelah berhasil mendapat 1 frame gambar
                            stopScreenCapture()

                            // Tampilkan kembali menu mengambang ke layar Anda
                            floatingView.visibility = View.VISIBLE

                            Toast.makeText(this, "Layar berhasil ditangkap! Siap diproses terjemahan.", Toast.LENGTH_SHORT).show()
                            
                            // [TAHAP BERIKUTNYA]: Di sinilah kita akan meletakkan mesin Google ML Kit OCR untuk membaca teks dari variabel 'bitmap' ini.
                        }
                    }, Handler(Looper.getMainLooper()))

                } catch (e: Exception) {
                    e.printStackTrace()
                    // Jika gagal, pastikan menu mengambang dimunculkan kembali agar tidak hilang permanen
                    floatingView.visibility = View.VISIBLE
                    Toast.makeText(this, "Gagal menangkap layar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, 100)
        } else {
            Toast.makeText(this, "Izin tangkap layar belum disetujui!", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        mediaProjection?.stop()
        mediaProjection = null
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
