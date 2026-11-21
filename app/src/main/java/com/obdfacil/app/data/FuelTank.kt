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

    // Quilometragem do painel (o 206 não informa pela OBD; o usuário digita)
    private var lastOdo: Double? = prefs.getFloat("last_odo", -1f).takeIf { it >= 0 }?.toDouble()
    private var appKmSinceOdo = prefs.getFloat("app_km_since_odo", 0f).toDouble()
    private var odoAtLastFull: Double? = prefs.getFloat("odo_at_full", -1f).takeIf { it >= 0 }?.toDouble()

    /** Odômetro estimado: último valor informado + o que o app mediu desde então. */
    val estimatedOdometer: Double? get() = lastOdo?.let { it + appKmSinceOdo }

    fun consume(liters: Double, km: Double) {
        if (liters <= 0 && km <= 0) return
        litersInTank = litersInTank?.let { (it - liters).coerceAtLeast(0.0) }
        if (hasFullRef) { estSinceFull += liters; kmSinceFull += km }
        appKmSinceOdo += km
    }

    fun setLevel(fraction: Double) {
        litersInTank = (capacityL * fraction).coerceIn(0.0, capacityL)
        save()
    }

    data class RefuelResult(val realKmPerL: Double?, val calibrated: Boolean, val kmWithoutApp: Double = 0.0)

    /**
     * @param levelBefore nível antes de abastecer (0..1), quando o app ainda não sabe quanto havia.
     * @param odometer quilometragem do painel, se informada.
     * @param refKmPerL consumo médio de referência, para descontar o que foi rodado sem o app.
     */
    fun refuel(liters: Double, full: Boolean, levelBefore: Double?, odometer: Double? = null, refKmPerL: Double? = null): RefuelResult {
        levelBefore?.let { litersInTank = capacityL * it }
        var realKmPerL: Double? = null
        var calibrated = false
        var kmWithoutApp = 0.0

        // Com o odômetro, dá para saber quanto foi rodado sem o app e descontar do tanque
        val prevOdo = lastOdo
        if (odometer != null && prevOdo != null && odometer > prevOdo) {
            kmWithoutApp = (odometer - prevOdo - appKmSinceOdo).coerceAtLeast(0.0)
            if (kmWithoutApp > 2 && levelBefore == null && refKmPerL != null && refKmPerL > 0) {
                litersInTank = litersInTank?.let { (it - kmWithoutApp / refKmPerL).coerceAtLeast(0.0) }
            }
        }

        if (full) {
            val realLiters = addedSinceFull + liters
            val odoKm = if (odometer != null) odoAtLastFull?.let { odometer - it }?.takeIf { it > 0 } else null
            if (hasFullRef && odoKm != null && odoKm > 40) {
                // Consumo real pelo painel: vale mesmo se parte foi rodada sem o app
                realKmPerL = odoKm / realLiters
                // Só calibra o cálculo se o app acompanhou quase tudo (90% dos km)
                if (estSinceFull > 3 && kmSinceFull >= odoKm * 0.9) {
                    calibration = FuelModel.recalibrate(calibration, realLiters * kmSinceFull / odoKm, estSinceFull)
                    calibrated = true
                }
            } else if (hasFullRef && estSinceFull > 3 && kmSinceFull > 40) {
                realKmPerL = kmSinceFull / realLiters
                calibration = FuelModel.recalibrate(calibration, realLiters, estSinceFull)
                calibrated = true
            }
            litersInTank = capacityL
            hasFullRef = true; estSinceFull = 0.0; kmSinceFull = 0.0; addedSinceFull = 0.0
            odoAtLastFull = odometer
        } else {
            litersInTank = litersInTank?.let { (it + liters).coerceAtMost(capacityL) }
            if (hasFullRef) addedSinceFull += liters
        }
        if (odometer != null) { lastOdo = odometer; appKmSinceOdo = 0.0 }
        save()
        return RefuelResult(realKmPerL, calibrated, kmWithoutApp)
    }

    /** O usuário corrige a quilometragem atual do painel. */
    fun setOdometer(km: Double) {
        lastOdo = km; appKmSinceOdo = 0.0
        save()
    }

    /** Ajuste manual: o usuário informa o consumo real que conhece. */
    fun calibrateTo(realKmPerL: Double, estimatedKmPerL: Double) {
        if (realKmPerL <= 0 || estimatedKmPerL <= 0) return
        calibration = (calibration * estimatedKmPerL / realKmPerL).coerceIn(0.4, 2.5)
    }

    fun reset() {
        litersInTank = null; hasFullRef = false; estSinceFull = 0.0; kmSinceFull = 0.0; addedSinceFull = 0.0
        lastOdo = null; appKmSinceOdo = 0.0; odoAtLastFull = null
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
            .putFloat("last_odo", lastOdo?.toFloat() ?: -1f)
            .putFloat("app_km_since_odo", appKmSinceOdo.toFloat())
            .putFloat("odo_at_full", odoAtLastFull?.toFloat() ?: -1f)
            .apply()
    }
}
