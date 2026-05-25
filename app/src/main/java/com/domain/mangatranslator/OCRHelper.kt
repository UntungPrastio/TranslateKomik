package com.domain.mangatranslator

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions // Berubah ke Latin

class OCRHelper {

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
        // Mesin diubah untuk membaca huruf Latin (Inggris/Indonesia)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blockList = mutableListOf<TextBlockModel>()
                
                for (block in visionText.textBlocks) {
                    val box = block.boundingBox
                    if (box != null && block.text.isNotBlank()) {
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
