package com.obdfacil.app.analysis

/**
 * Etanol ou gasolina? Compara o custo por km.
 * Usa o consumo real do próprio carro quando existe; senão, a regra dos 70%
 * (o etanol rende cerca de 70% da gasolina — no 206 1.4 flex as fichas indicam ~71% a 74%).
 */
object FuelCompare {
    const val DEFAULT_RATIO = 0.70

    data class Result(
        val ratioUsed: Double,          // km/l etanol ÷ km/l gasolina
        val ratioFromOwnData: Boolean,
        val ethanolWins: Boolean,
        val breakEvenEthanolPrice: Double, // até este preço o etanol compensa
        val costPerKmEthanol: Double?,
        val costPerKmGasoline: Double?,
        val savingPer1000Km: Double?,
    )

    fun compare(
        priceEthanol: Double,
        priceGasoline: Double,
        kmlEthanol: Double?,
        kmlGasoline: Double?,
    ): Result {
        val own = kmlEthanol != null && kmlGasoline != null && kmlGasoline > 0
        val ratio = if (own) (kmlEthanol!! / kmlGasoline!!).coerceIn(0.5, 0.95) else DEFAULT_RATIO
        val ethanolWins = priceEthanol / priceGasoline < ratio
        // km/l de referência: o real, ou derivado do outro combustível pela proporção
        val kE = kmlEthanol ?: kmlGasoline?.let { it * ratio }
        val kG = kmlGasoline ?: kmlEthanol?.let { it / ratio }
        val cE = kE?.let { priceEthanol / it }
        val cG = kG?.let { priceGasoline / it }
        val saving = if (cE != null && cG != null) kotlin.math.abs(cG - cE) * 1000 else null
        return Result(ratio, own, ethanolWins, priceGasoline * ratio, cE, cG, saving)
    }
}
