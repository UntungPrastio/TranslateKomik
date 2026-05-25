package com.domain.mangatranslator

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationHelper {

    interface TranslationListener {
        fun onSuccess(translatedText: String)
        fun onFailure(errorMessage: String)
    }

    fun translateText(text: String, listener: TranslationListener) {
        // Mengatur konfigurasi penerjemahan dari bahasa Jepang ke bahasa Indonesia
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.INDONESIAN)
            .build()

        val translator = Translation.getClient(options)

        // Mengecek dan mengunduh model bahasa di latar belakang jika belum tersedia di HP
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                // Proses menerjemahkan teks asing yang dikirim
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        listener.onSuccess(translatedText)
                    }
                    .addOnFailureListener { e ->
                        listener.onFailure(e.message ?: "Gagal menerjemahkan teks.")
                    }
            }
            .addOnFailureListener { e ->
                listener.onFailure("Gagal menyiapkan model bahasa: ${e.message}")
            }
    }
}

