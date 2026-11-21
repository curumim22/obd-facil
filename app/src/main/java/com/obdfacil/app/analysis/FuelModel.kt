package com.obdfacil.app.analysis

import com.obdfacil.app.obd.LiveData

enum class FuelType(val label: String, val stoichAfr: Double, val densityKgL: Double) {
    // Etanol hidratado (o da bomba): ~9,0 do etanol puro, descontada a água
    ETANOL("Etanol", 8.4, 0.809),
    // Gasolina comum brasileira (com ~27% de etanol)
    GASOLINA("Gasolina", 13.2, 0.745),
}

/**
 * Estima o consumo sem sensor de fluxo de ar (o 206 não tem MAF):
 * calcula a massa de ar que o motor puxa a partir da pressão do coletor (MAP),
 * rotação e temperatura do ar ("speed-density"), e daí o combustível necessário
 * para a mistura correta. O fator de calibração corrige o erro do modelo com
 * base nos abastecimentos reais.
 */
object FuelModel {
    const val DISPLACEMENT_L = 1.36       // motor TU3 1.4
    const val VOLUMETRIC_EFF = 0.80       // eficiência volumétrica média estimada
    private const val R_AIR = 287.05      // J/(kg·K)

    /** Fluxo de ar em g/s. */
    fun airGramsPerSecond(mapKpa: Int, rpm: Int, intakeC: Int?): Double {
        val tK = (intakeC ?: 35) + 273.15
        val m3PerSecond = DISPLACEMENT_L / 1000.0 * VOLUMETRIC_EFF * rpm / 120.0  // 4 tempos: meia volta por admissão
        return mapKpa * 1000.0 * m3PerSecond / (R_AIR * tK) * 1000.0
    }

    /** Corte de combustível em desaceleração: pé fora do acelerador, rotação alta e carro andando. */
    fun isFuelCut(d: LiveData, throttleClosedMax: Int?): Boolean {
        val rpm = d.rpm ?: return false
        val throttle = d.throttlePct ?: return false
        val closed = throttleClosedMax != null && throttle <= throttleClosedMax
        if (!closed || rpm < 1300 || (d.speedKmh ?: 0) < 8) return false
        // Status 4 = malha aberta por desaceleração/carga; com borboleta fechada é corte
        return d.fuelStatus == null || d.fuelStatus == 4 || (d.mapKpa ?: 100) < 28
    }

    /** Fator lambda: <1 quando a central enriquece (motor frio ou acelerando forte). */
    fun lambda(d: LiveData, throttleClosedMax: Int?): Double {
        val t = d.coolantC ?: 90
        return when {
            t < 40 -> 0.80
            t < 70 -> 0.90
            d.fuelStatus == 4 && (throttleClosedMax == null || (d.throttlePct ?: 0) > throttleClosedMax) -> 0.88
            else -> 1.0
        }
    }

    /** Consumo instantâneo em litros por hora (null se faltar dado). */
    fun litersPerHour(d: LiveData, fuel: FuelType, calibration: Double, throttleClosedMax: Int?): Double? {
        val rpm = d.rpm ?: return null
        if (rpm < 300) return 0.0
        val map = d.mapKpa ?: return null
        if (isFuelCut(d, throttleClosedMax)) return 0.0
        val fuelGs = airGramsPerSecond(map, rpm, d.intakeC) / (fuel.stoichAfr * lambda(d, throttleClosedMax))
        return fuelGs * 3600.0 / (fuel.densityKgL * 1000.0) * calibration
    }

    /** km/l a partir de velocidade e L/h; parado ou sem consumo não faz sentido em km/l. */
    fun kmPerLiter(speedKmh: Double?, litersPerHour: Double?): Double? {
        if (speedKmh == null || litersPerHour == null || speedKmh < 5 || litersPerHour <= 0.05) return null
        return (speedKmh / litersPerHour).coerceAtMost(99.0)
    }

    /**
     * Novo fator de calibração depois de comparar o gasto real (litros) com o estimado.
     * Anda só metade do caminho a cada tanque para não oscilar com um abastecimento impreciso.
     */
    fun recalibrate(current: Double, realLiters: Double, estimatedLiters: Double): Double {
        if (realLiters <= 0 || estimatedLiters <= 0) return current
        val target = current * realLiters / estimatedLiters
        return (current + (target - current) * 0.5).coerceIn(0.4, 2.5)
    }
}
