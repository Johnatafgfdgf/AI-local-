package dev.nyra.local

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.UUID

class OfflineVoice(context: Context, private val status: (String) -> Unit) {
    private var ready = false
    private var tts: TextToSpeech? = null
    private var active: String? = null
    init {
        tts = TextToSpeech(context.applicationContext) { code ->
            if (code == TextToSpeech.SUCCESS) {
                val voice = tts?.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == "pt" }
                    ?.sortedByDescending { it.locale.country == "BR" }?.firstOrNull()
                if (voice != null) {
                    tts?.voice = voice; ready = true; status("Voz offline pronta")
                } else status("Instale uma voz portuguesa offline nas configurações do Android.")
            } else status("Motor de voz indisponível")
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { if (id == active) status("Falando") }
            override fun onDone(id: String?) { if (id == active) { active = null; status("Voz offline pronta") } }
            @Deprecated("Android callback") override fun onError(id: String?) { if (id == active) { active = null; status("Falha na voz") } }
        })
    }
    fun speak(text: String) {
        if (!ready) { status("Nenhuma voz portuguesa offline disponível"); return }
        stop(); active = UUID.randomUUID().toString()
        if (tts?.speak(text.take(TextToSpeech.getMaxSpeechInputLength()), TextToSpeech.QUEUE_FLUSH, null, active) != TextToSpeech.SUCCESS) status("Não foi possível iniciar a voz")
    }
    fun stop() { active = null; tts?.stop() }
    fun close() { stop(); tts?.shutdown() }
}
