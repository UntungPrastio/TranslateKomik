package com.domain.mangatranslator

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class OCRHelper {

    // Model data terstruktur untuk mengikat teks beserta koordinat posisinya di layar
    data class TextBlockModel(
        val text: String,
        val boundingBox: Rect
    )

    interface OCRListener {
        fun onSuccess(blocks: List<TextBlockModel>)
        fun onFailure(errorMessage: String)
    }

    fun extractTextFromBitmap(bitmap: Bitmap, listener: OCRListener) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blockList = mutableListOf<TextBlockModel>()
                
                // Melakukan perulangan untuk mengambil setiap blok tulisan / balon kata komik
                for (block in visionText.textBlocks) {
                    val box = block.boundingBox
                    if (box != null && block.text.isNotBlank()) {
                        // Simpan teks beserta koordinat areanya
                        blockList.add(TextBlockModel(block.text, box))
                    }
                }

                if (blockList.isNotEmpty()) {
                    listener.onSuccess(blockList)
                } else {
                    listener.onFailure("Tidak ada teks komik yang terdeteksi.")
                }
            }
            .addOnFailureListener { e ->
                listener.onFailure(e.message ?: "Gagal memindai gambar.")
            }
    }
}
