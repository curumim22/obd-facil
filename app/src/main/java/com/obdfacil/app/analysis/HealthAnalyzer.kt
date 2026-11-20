package com.obdfacil.app.analysis

import com.obdfacil.app.obd.LiveData

enum class Level { OK, INFO, WARNING, CRITICAL }

data class Alert(
    val key: String,
    val level: Level,
    val title: String,
    val message: String,
    val action: String,
)

/** Transforma números em avisos simples. Limites pensados para o motor 1.4 TU3 do 206. */
object HealthAnalyzer {

    const val COOLANT_WARN = 104
    const val COOLANT_CRIT = 110

    const val TRIM_LIMIT = 20.0
    const val TRIM_SUSTAIN_MS = 30_000L
    const val WARM_ENGINE_C = 75

    /** A correção da mistura só é confiável com o motor ligado e quente. */
    fun trimReliable(d: LiveData) =
        d.longTrimPct != null && d.engineRunning && (d.coolantC ?: 0) >= WARM_ENGINE_C

    fun trimOutOfRange(d: LiveData) =
        trimReliable(d) && kotlin.math.abs((d.shortTrimPct ?: 0.0) + (d.longTrimPct ?: 0.0)) >= TRIM_LIMIT

    fun analyze(d: LiveData, dtcs: List<DtcInfo>, trimHighForMs: Long = 0): List<Alert> {
        val out = mutableListOf<Alert>()

        d.coolantC?.let { t ->
            when {
                t >= COOLANT_CRIT -> out += Alert("coolant", Level.CRITICAL, "Motor superaquecendo: $t °C",
                    "A temperatura passou do limite seguro. Continuar rodando pode queimar a junta do cabeçote e causar um conserto caro.",
                    "Encoste e desligue o motor assim que for seguro. Espere esfriar antes de verificar a água. Nunca abra o reservatório quente.")
                t >= COOLANT_WARN -> out += Alert("coolant", Level.WARNING, "Motor esquentando: $t °C",
                    "Acima do normal (85 a 100 °C). Pode ser trânsito pesado, ventoinha ou falta de água.",
                    "Desligue o ar-condicionado, evite acelerar forte e observe. Se continuar subindo, pare o carro.")
                d.engineRunning && t < 60 -> out += Alert("cold", Level.INFO, "Motor ainda frio",
                    "O motor está aquecendo. Isso é normal nos primeiros minutos.",
                    "Evite acelerar forte e rotações altas até passar de 70 °C.")
                else -> {}
            }
        }

        d.voltage?.let { v ->
            val running = d.engineRunning || (d.rpm == null && v >= 13.3)
            if (running) when {
                v > 15.2 -> out += Alert("volt", Level.CRITICAL, "Tensão alta demais: $v V",
                    "O alternador está mandando energia demais. Isso pode danificar a bateria e a parte elétrica.",
                    "Leve a um eletricista automotivo o quanto antes.")
                v < 12.0 -> out += Alert("volt", Level.CRITICAL, "Bateria não está carregando: $v V",
                    "Com o motor ligado, a tensão deveria ficar entre 13,5 e 14,5 V. O carro está rodando só com a bateria e pode morrer.",
                    "Desligue o que não precisa (ar, som, faróis extras) e vá direto a um eletricista.")
                v < 12.9 -> out += Alert("volt", Level.WARNING, "Carga da bateria baixa: $v V",
                    "Com o motor ligado o normal é de 13,5 a 14,5 V. O alternador ou a correia podem estar com problema.",
                    "Peça para testarem o alternador e a correia na próxima parada.")
                else -> {}
            } else when {
                v < 11.6 -> out += Alert("volt", Level.CRITICAL, "Bateria muito fraca: $v V",
                    "Com essa carga o carro pode não dar partida.",
                    "Evite desligar o carro longe de casa. Teste a bateria numa loja especializada.")
                v < 12.2 -> out += Alert("volt", Level.WARNING, "Bateria fraca: $v V",
                    "Com o motor desligado o normal é 12,4 V ou mais.",
                    "Teste a bateria. Se ela tiver mais de 3 anos, pode estar no fim da vida.")
                else -> {}
            }
        }

        // Correção da mistura: só vale com o motor quente (antes disso a central enriquece de propósito)
        // e só avisa se ficar alta por um tempo (trimHighForMs), para não alarmar com picos.
        val totalTrim = (d.shortTrimPct ?: 0.0) + (d.longTrimPct ?: 0.0)
        if (trimReliable(d) && trimHighForMs >= TRIM_SUSTAIN_MS) when {
            totalTrim >= TRIM_LIMIT -> out += Alert("trim", Level.WARNING, "Mistura pobre (correção de ${"%.0f".format(totalTrim)}%)",
                "Com o motor quente, a central está colocando bem mais combustível que o normal para compensar. Pode ser entrada falsa de ar, bomba fraca, filtro entupido ou bico sujo.",
                "Trocou de combustível há pouco? Num flex, a central leva alguns quilômetros para se adaptar: rode um pouco e confira de novo. Se continuar, peça para verificar mangueiras de vácuo, filtro e pressão de combustível.")
            totalTrim <= -TRIM_LIMIT -> out += Alert("trim", Level.WARNING, "Mistura rica (correção de ${"%.0f".format(totalTrim)}%)",
                "Com o motor quente, a central está cortando combustível para compensar excesso. Pode ser bico pingando ou sensor com defeito.",
                "Trocou de combustível há pouco? Rode um pouco e confira de novo. Se continuar, leve para limpeza e teste de bicos.")
            else -> {}
        }

        d.rpm?.let { if (it > 6000) out += Alert("rpm", Level.WARNING, "Rotação muito alta: $it rpm",
            "O motor está perto do limite.", "Troque de marcha ou alivie o acelerador.") }

        d.intakeC?.let { if (it > 65) out += Alert("intake", Level.WARNING, "Ar de admissão muito quente: $it °C",
            "O ar que entra no motor está muito quente, o que reduz a força e favorece a batida de pino.",
            "Normal em trânsito parado no calor. Se continuar rodando, verifique a ventoinha.") }

        val worstDtc = dtcs.minByOrNull { it.severity.ordinal }
        if (dtcs.isNotEmpty()) {
            val critical = dtcs.filter { it.severity == Severity.CRITICAL }
            if (critical.isNotEmpty()) out += Alert("dtc", Level.CRITICAL,
                "Falha grave registrada no motor",
                critical.joinToString("\n") { "• ${it.code}: ${it.title}" },
                Severity.CRITICAL.action)
            else out += Alert("dtc", Level.WARNING,
                if (dtcs.size == 1) "1 falha registrada" else "${dtcs.size} falhas registradas",
                dtcs.joinToString("\n") { "• ${it.code}: ${it.title}" },
                worstDtc?.severity?.action ?: "")
        } else if (d.milOn == true) {
            out += Alert("mil", Level.WARNING, "Luz da injeção acesa",
                "A central acendeu a luz de aviso do motor.", "Abra a aba Diagnóstico para ver o motivo.")
        }

        return out.sortedByDescending { it.level.ordinal }
    }

    fun overall(alerts: List<Alert>): Level = alerts.maxOfOrNull { it.level } ?: Level.OK
}
