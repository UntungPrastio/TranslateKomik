package com.domain.mangatranslator

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OCRHelper {

    // Jembatan komunikasi untuk mengirimkan hasil teks yang berhasil dibaca
    interface OCRListener {
        fun onSuccess(resultText: String)
        fun onFailure(errorMessage: String)
    }

    // Fungsi utama membaca gambar
    fun extractTextFromBitmap(bitmap: Bitmap, listener: OCRListener) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        // Catatan: Untuk saat ini kita atur mesinnya untuk membaca huruf Jepang (Manga).
        // Anda bisa menggantinya dengan 'KoreanTextRecognizerOptions' untuk Manhwa nanti.
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

        // Memulai proses pemindaian gambar
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Menggabungkan seluruh teks yang berhasil ditemukan di layar
                val extractedText = visionText.text
                if (extractedText.isNotBlank()) {
                    listener.onSuccess(extractedText)
                } else {
                    listener.onFailure("Tidak ada teks yang terdeteksi di layar.")
                }
            }
            .addOnFailureListener { e ->
                listener.onFailure(e.message ?: "Gagal memindai teks dari gambar.")
            }
    }
}

