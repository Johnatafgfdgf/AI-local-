package dev.nyra.local

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** Android offline TTS plus word-range events used to drive lip sync. */
class OfflineVoice(
    context: Context,
    private val status: (String) -> Unit,
    private val viseme: (String?) -> Unit = {}
) {
    private var ready = false
    private var tts: TextToSpeech? = null
    private var active: String? = null
    private var activeText: String = ""

    init {
        tts = TextToSpeech(context.applicationContext) { code ->
            if (code == TextToSpeech.SUCCESS) {
                val voice = tts?.voices
                    ?.filter { !it.isNetworkConnectionRequired && it.locale.language == "pt" }
                    ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "BR" }
                        .thenByDescending { it.quality })
                    ?.firstOrNull()
                if (voice != null) {
                    tts?.voice = voice
                    tts?.language = Locale("pt", "BR")
                    ready = true
                    status("Voz offline pronta")
                } else {
                    status("Instale uma voz portuguesa offline nas configurações do Android.")
                }
            } else {
                status("Motor de voz indisponível")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == active) status("Falando")
            }

            override fun onDone(id: String?) {
                if (id == active) {
                    active = null
                    activeText = ""
                    viseme(null)
                    status("Voz offline pronta")
                }
            }

            @Deprecated("Android callback")
            override fun onError(id: String?) {
                if (id == active) {
                    active = null
                    activeText = ""
                    viseme(null)
                    status("Falha na voz")
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != active || activeText.isEmpty()) return
                val safeStart = start.coerceIn(0, activeText.length)
                val safeEnd = end.coerceIn(safeStart, activeText.length)
                val slice = activeText.substring(safeStart, safeEnd)
                viseme(visemeFor(slice))
            }
        })
    }

    fun speak(text: String) {
        if (!ready) {
            status("Nenhuma voz portuguesa offline disponível")
            return
        }
        stop()
        activeText = text.take(TextToSpeech.getMaxSpeechInputLength())
        active = UUID.randomUUID().toString()
        val result = tts?.speak(activeText, TextToSpeech.QUEUE_FLUSH, null, active)
        if (result != TextToSpeech.SUCCESS) {
            active = null
            activeText = ""
            viseme(null)
            status("Não foi possível iniciar a voz")
        }
    }

    fun stop() {
        active = null
        activeText = ""
        viseme(null)
        tts?.stop()
    }

    fun close() {
        stop()
        tts?.shutdown()
    }

    private fun visemeFor(text: String): String? {
        val normalized = text.lowercase(Locale.ROOT)
        // Pick the last pronounced vowel in the current word-range. It maps naturally to the
        // A/I/U/E/O blend shapes present in the supplied VRM and is synchronized to TTS ranges.
        return normalized.lastOrNull { it in "aeiouáàâãéêíóôõúü" }?.let { vowel ->
            when (vowel) {
                'a', 'á', 'à', 'â', 'ã' -> "A"
                'i', 'í' -> "I"
                'u', 'ú', 'ü' -> "U"
                'e', 'é', 'ê' -> "E"
                'o', 'ó', 'ô', 'õ' -> "O"
                else -> null
            }
        }
    }
}
