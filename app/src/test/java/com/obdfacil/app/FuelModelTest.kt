package com.obdfacil.app

import com.obdfacil.app.analysis.FuelModel
import com.obdfacil.app.analysis.FuelType
import com.obdfacil.app.obd.LiveData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelModelTest {

    // Valores reais do registro técnico do 206 (marcha lenta e ~70 km/h)
    private val idle = LiveData(rpm = 760, speedKmh = 0, mapKpa = 34, intakeC = 48, coolantC = 88, throttlePct = 12, fuelStatus = 2)
    private val cruise = LiveData(rpm = 2200, speedKmh = 70, mapKpa = 45, intakeC = 45, coolantC = 90, throttlePct = 20, fuelStatus = 2)

    @Test fun idleAirFlowIsPlausible() {
        val g = FuelModel.airGramsPerSecond(34, 760, 48)
        assertTrue("ar em marcha lenta = $g g/s", g in 1.5..4.0)
    }

    @Test fun idleConsumptionIsPlausible() {
        val lh = FuelModel.litersPerHour(idle, FuelType.ETANOL, 1.0, 14)!!
        assertTrue("marcha lenta = $lh L/h", lh in 0.6..2.0)
        assertNull(FuelModel.kmPerLiter(0.0, lh))
    }

    @Test fun cruiseConsumptionIsPlausible() {
        val lh = FuelModel.litersPerHour(cruise, FuelType.ETANOL, 1.0, 14)!!
        val kml = FuelModel.kmPerLiter(70.0, lh)!!
        assertTrue("etanol a 70 km/h = $kml km/l", kml in 7.0..18.0)
        val gas = FuelModel.kmPerLiter(70.0, FuelModel.litersPerHour(cruise, FuelType.GASOLINA, 1.0, 14))!!
        assertTrue("gasolina rende mais que etanol", gas > kml)
    }

    @Test fun decelerationCutsFuel() {
        val coasting = cruise.copy(rpm = 2400, throttlePct = 12, mapKpa = 22, fuelStatus = 4)
        assertEquals(0.0, FuelModel.litersPerHour(coasting, FuelType.ETANOL, 1.0, 14)!!, 0.0)
        // pé no acelerador com status 4 = enriquecimento, não corte
        val pushing = cruise.copy(throttlePct = 60, mapKpa = 90, fuelStatus = 4, rpm = 3500)
        assertTrue(FuelModel.litersPerHour(pushing, FuelType.ETANOL, 1.0, 14)!! > 5.0)
    }

    @Test fun calibrationMovesHalfway() {
        // estimou 20 L, gastou 25 L de verdade -> alvo 1.25, anda metade -> 1.125
        assertEquals(1.125, FuelModel.recalibrate(1.0, 25.0, 20.0), 1e-9)
    }
}
