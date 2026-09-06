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
                    ?.sortedWith(
                        compareByDescending<android.speech.tts.Voice> { it.locale.country == "BR" }
                            .thenByDescending { it.quality }
                    )
                    ?.firstOrNull()
                if (voice != null) {
                    tts?.voice = voice
                    tts?.language = Locale("pt", "BR")
                    tts?.setSpeechRate(0.98f)
                    tts?.setPitch(1.02f)
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
                finishUtterance(id, "Voz offline pronta")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                finishUtterance(utteranceId, if (ready) "Voz offline pronta" else "Voz interrompida")
            }

            @Deprecated("Android callback")
            override fun onError(id: String?) {
                finishUtterance(id, "Falha na voz")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishUtterance(utteranceId, "Falha na voz")
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

    private fun finishUtterance(id: String?, finalStatus: String) {
        if (id != active) return
        active = null
        activeText = ""
        viseme(null)
        status(finalStatus)
    }

    fun speak(text: String) {
        if (!ready) {
            status("Nenhuma voz portuguesa offline disponível")
            return
        }

        // Do not call the public stop() here because it would briefly publish "pronta" between two
        // utterances and make the UI flicker. Reset the engine silently, then start the new id.
        active = null
        activeText = ""
        viseme(null)
        tts?.stop()

        activeText = text.take(TextToSpeech.getMaxSpeechInputLength())
        if (activeText.isBlank()) {
            status("Voz offline pronta")
            return
        }
        active = UUID.randomUUID().toString()
        status("Preparando voz…")
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
        // Previously the UI could stay permanently on "Falando" because active was cleared before
        // Android delivered onStop/onDone. Publish the terminal state synchronously.
        if (ready) status("Voz offline pronta")
    }

    fun close() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
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
