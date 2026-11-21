package com.obdfacil.app.data

import android.content.SharedPreferences
import com.obdfacil.app.analysis.FuelModel
import com.obdfacil.app.analysis.FuelType

/**
 * Controle estimado do tanque. O 206 não informa o nível de combustível pela OBD,
 * então o app parte dos abastecimentos informados e desconta o consumo estimado.
 * Só conta o que foi rodado com o app conectado.
 */
class FuelTank(private val prefs: SharedPreferences) {

    var fuelType: FuelType = runCatching { FuelType.valueOf(prefs.getString("fuel_type", null) ?: "") }.getOrDefault(FuelType.ETANOL)
        set(v) { field = v; prefs.edit().putString("fuel_type", v.name).apply() }

    var capacityL: Double = prefs.getFloat("tank_capacity", 50f).toDouble()
        set(v) { field = v.coerceIn(20.0, 120.0); prefs.edit().putFloat("tank_capacity", field.toFloat()).apply() }

    /** Fator que corrige o modelo de consumo; ajustado pelos abastecimentos de tanque cheio. */
    var calibration: Double = prefs.getFloat("fuel_cal", 1f).toDouble()
        set(v) { field = v; prefs.edit().putFloat("fuel_cal", v.toFloat()).apply() }

    /** Litros estimados no tanque; null = desconhecido. */
    var litersInTank: Double? = prefs.getFloat("tank_l", -1f).takeIf { it >= 0 }?.toDouble()
        private set

    // Acumulados desde o último "tanque cheio", usados para medir o consumo real e calibrar
    private var hasFullRef = prefs.getBoolean("full_ref", false)
    private var estSinceFull = prefs.getFloat("est_since_full", 0f).toDouble()
    private var kmSinceFull = prefs.getFloat("km_since_full", 0f).toDouble()
    private var addedSinceFull = prefs.getFloat("added_since_full", 0f).toDouble()

    fun consume(liters: Double, km: Double) {
        if (liters <= 0 && km <= 0) return
        litersInTank = litersInTank?.let { (it - liters).coerceAtLeast(0.0) }
        if (hasFullRef) { estSinceFull += liters; kmSinceFull += km }
    }

    fun setLevel(fraction: Double) {
        litersInTank = (capacityL * fraction).coerceIn(0.0, capacityL)
        save()
    }

    data class RefuelResult(val realKmPerL: Double?, val calibrated: Boolean)

    /**
     * @param levelBefore nível antes de abastecer (0..1), quando o app ainda não sabe quanto havia.
     */
    fun refuel(liters: Double, full: Boolean, levelBefore: Double?): RefuelResult {
        levelBefore?.let { litersInTank = capacityL * it }
        var realKmPerL: Double? = null
        var calibrated = false
        if (full) {
            if (hasFullRef && estSinceFull > 3 && kmSinceFull > 40) {
                val realLiters = addedSinceFull + liters
                realKmPerL = kmSinceFull / realLiters
                calibration = FuelModel.recalibrate(calibration, realLiters, estSinceFull)
                calibrated = true
            }
            litersInTank = capacityL
            hasFullRef = true; estSinceFull = 0.0; kmSinceFull = 0.0; addedSinceFull = 0.0
        } else {
            litersInTank = litersInTank?.let { (it + liters).coerceAtMost(capacityL) }
            if (hasFullRef) addedSinceFull += liters
        }
        save()
        return RefuelResult(realKmPerL, calibrated)
    }

    /** Ajuste manual: o usuário informa o consumo real que conhece. */
    fun calibrateTo(realKmPerL: Double, estimatedKmPerL: Double) {
        if (realKmPerL <= 0 || estimatedKmPerL <= 0) return
        calibration = (calibration * estimatedKmPerL / realKmPerL).coerceIn(0.4, 2.5)
    }

    fun reset() {
        litersInTank = null; hasFullRef = false; estSinceFull = 0.0; kmSinceFull = 0.0; addedSinceFull = 0.0
        calibration = 1.0
        save()
    }

    fun save() {
        prefs.edit()
            .putFloat("tank_l", litersInTank?.toFloat() ?: -1f)
            .putBoolean("full_ref", hasFullRef)
            .putFloat("est_since_full", estSinceFull.toFloat())
            .putFloat("km_since_full", kmSinceFull.toFloat())
            .putFloat("added_since_full", addedSinceFull.toFloat())
            .apply()
    }
}
