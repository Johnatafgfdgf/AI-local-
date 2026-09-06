package dev.nyra.local

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager

enum class ResponseMode(val label: String) {
    FAST("Rápido"),
    AUTO("Automático"),
    THINK("Pensar")
}

data class RuntimeBudget(
    val maxOutputTokens: Int,
    val thermalStatus: Int,
    val availableRamMb: Long,
    val reason: String
)

/**
 * First measurable PurpleCore governor: response budgets react to user mode, free RAM and Android
 * thermal status. It deliberately does not claim GPU/NPU routing until those paths are benchmarked.
 */
object PurpleGovernor {
    fun budget(context: Context, mode: ResponseMode): RuntimeBudget {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        val availableMb = info.availMem / 1048576L
        val thermal = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus

        var tokens = when (mode) {
            ResponseMode.FAST -> 320
            ResponseMode.AUTO -> 512
            ResponseMode.THINK -> 768
        }
        val reasons = mutableListOf(mode.label)

        if (availableMb < 1800L) {
            tokens = minOf(tokens, 320)
            reasons += "RAM baixa"
        } else if (availableMb < 2800L) {
            tokens = minOf(tokens, 448)
            reasons += "RAM moderada"
        }

        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            tokens = minOf(tokens, 256)
            reasons += "temperatura alta"
        } else if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
            tokens = minOf(tokens, 384)
            reasons += "controle térmico"
        }

        return RuntimeBudget(
            maxOutputTokens = tokens.coerceIn(128, 1024),
            thermalStatus = thermal,
            availableRamMb = availableMb,
            reason = reasons.joinToString(" • ")
        )
    }

    fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "leve"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderado"
        PowerManager.THERMAL_STATUS_SEVERE -> "alto"
        PowerManager.THERMAL_STATUS_CRITICAL -> "crítico"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergência"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "desligamento"
        else -> "desconhecido"
    }
}
