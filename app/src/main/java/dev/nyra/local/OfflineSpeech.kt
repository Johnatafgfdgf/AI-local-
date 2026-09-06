package dev.nyra.local

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * On-device-only speech recognition. Nyra never silently falls back to a network recognizer.
 * Android 12+ exposes a dedicated on-device recognizer when a local speech pack is available.
 */
class OfflineSpeech(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onText: (String, Boolean) -> Unit,
    private val onListening: (Boolean) -> Unit
) {
    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val available: Boolean
        get() = SpeechRecognizer.isOnDeviceRecognitionAvailable(app)

    init {
        if (available) {
            runCatching {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(app).also { recognizer ->
                    recognizer.setRecognitionListener(listener)
                }
                onStatus("Ditado offline pronto")
            }.onFailure {
                recognizer = null
                onStatus("Reconhecimento de voz offline indisponível")
            }
        } else {
            onStatus("Instale o pacote de reconhecimento de voz offline do Android")
        }
    }

    fun start() {
        val local = recognizer
        if (local == null) {
            onStatus("Reconhecimento de voz offline não está disponível neste aparelho")
            return
        }
        if (listening) return
        listening = true
        onListening(true)
        onStatus("Ouvindo no aparelho…")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { local.startListening(intent) }
            .onFailure {
                finish("Não foi possível iniciar o microfone offline")
            }
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        finish("Ditado cancelado")
    }

    fun close() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        listening = false
        onListening(false)
    }

    private fun finish(status: String) {
        listening = false
        onListening(false)
        onStatus(status)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStatus("Pode falar")
        }

        override fun onBeginningOfSpeech() {
            onStatus("Escutando…")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            onStatus("Processando voz localmente…")
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Falha ao capturar áudio"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone necessária"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O reconhecedor tentou usar rede; Nyra bloqueou o fallback"
                SpeechRecognizer.ERROR_NO_MATCH -> "Não consegui entender. Tente falar novamente."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado. Tente novamente."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma fala detectada"
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Reconhecedor offline desconectado"
                else -> "Falha no ditado offline (código $error)"
            }
            finish(message)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) onText(text, true)
            finish(if (text.isBlank()) "Nenhuma fala reconhecida" else "Ditado offline concluído")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) onText(text, false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
