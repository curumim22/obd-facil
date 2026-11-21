package com.obdfacil.app.obd

/** Uma leitura instantânea do carro. Campos nulos = o carro não informou. */
data class LiveData(
    val timestamp: Long = System.currentTimeMillis(),
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val intakeC: Int? = null,
    val loadPct: Int? = null,
    val throttlePct: Int? = null,
    val voltage: Double? = null,
    val shortTrimPct: Double? = null,
    val longTrimPct: Double? = null,
    val mapKpa: Int? = null,
    val timingDeg: Double? = null,
    val milOn: Boolean? = null,
    val dtcCount: Int? = null,
    /** Status do sistema de combustível (PID 03): 1 frio, 2 malha fechada, 4 desaceleração/carga. */
    val fuelStatus: Int? = null,
    /** Consumo estimado pelo app em L/h (não vem do carro). */
    val fuelLh: Double? = null,
) {
    val engineRunning: Boolean get() = (rpm ?: 0) > 400
}

/** Os PIDs do modo 01 que o app lê, com a fórmula padrão SAE J1979. */
enum class Pid(val code: String, val bytes: Int) {
    STATUS("01", 4),
    FUEL_STATUS("03", 2),
    LOAD("04", 1),
    COOLANT("05", 1),
    SHORT_TRIM("06", 1),
    LONG_TRIM("07", 1),
    MAP("0B", 1),
    RPM("0C", 2),
    SPEED("0D", 1),
    TIMING("0E", 1),
    INTAKE("0F", 1),
    THROTTLE("11", 1),
    MODULE_VOLTAGE("42", 2);

    val number: Int get() = code.toInt(16)
}

object PidMath {
    fun apply(pid: Pid, b: IntArray, current: LiveData): LiveData = when (pid) {
        Pid.STATUS -> current.copy(milOn = (b[0] and 0x80) != 0, dtcCount = b[0] and 0x7F)
        Pid.FUEL_STATUS -> current.copy(fuelStatus = b[0])
        Pid.LOAD -> current.copy(loadPct = Math.round(b[0] * 100 / 255.0).toInt())
        Pid.COOLANT -> current.copy(coolantC = b[0] - 40)
        Pid.SHORT_TRIM -> current.copy(shortTrimPct = round1((b[0] - 128) * 100 / 128.0))
        Pid.LONG_TRIM -> current.copy(longTrimPct = round1((b[0] - 128) * 100 / 128.0))
        Pid.MAP -> current.copy(mapKpa = b[0])
        Pid.RPM -> current.copy(rpm = ((b[0] * 256) + b[1]) / 4)
        Pid.SPEED -> current.copy(speedKmh = b[0])
        Pid.TIMING -> current.copy(timingDeg = round1(b[0] / 2.0 - 64))
        Pid.INTAKE -> current.copy(intakeC = b[0] - 40)
        Pid.THROTTLE -> current.copy(throttlePct = Math.round(b[0] * 100 / 255.0).toInt())
        Pid.MODULE_VOLTAGE -> current.copy(voltage = round1(((b[0] * 256) + b[1]) / 1000.0))
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
