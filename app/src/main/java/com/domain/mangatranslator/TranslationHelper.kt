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
        // Sumber bahasa diubah ke ENGLISH
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.INDONESIAN)
            .build()

        val translator = Translation.getClient(options)

        // Jika baru pertama kali, ini akan mendownload kamus 30MB di latar belakang
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
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
