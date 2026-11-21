package com.obdfacil.app

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obdfacil.app.analysis.Alert
import com.obdfacil.app.analysis.DtcDatabase
import com.obdfacil.app.analysis.DtcInfo
import com.obdfacil.app.analysis.HealthAnalyzer
import com.obdfacil.app.analysis.Level
import com.obdfacil.app.analysis.FuelModel
import com.obdfacil.app.analysis.FuelType
import com.obdfacil.app.data.EventRow
import com.obdfacil.app.data.FuelTank
import com.obdfacil.app.data.Refuel
import com.obdfacil.app.data.HistoryDb
import com.obdfacil.app.data.ScanRow
import com.obdfacil.app.data.Trip
import com.obdfacil.app.data.TripStats
import com.obdfacil.app.export.Exporter
import com.obdfacil.app.obd.BluetoothTransport
import com.obdfacil.app.obd.DemoTransport
import com.obdfacil.app.obd.LiveData
import com.obdfacil.app.obd.LoggingTransport
import com.obdfacil.app.obd.ObdClient
import com.obdfacil.app.obd.TechLog
import com.obdfacil.app.obd.Pid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

/** Tudo que a tela de combustível precisa mostrar. */
data class FuelUi(
    val instantLh: Double? = null,
    val instantKmL: Double? = null,
    val fuelCut: Boolean = false,
    val tripFuelL: Double = 0.0,
    val tripKmL: Double? = null,
    val tankL: Double? = null,
    val capacityL: Double = 50.0,
    val refKmL: Double? = null,
    val fuelType: FuelType = FuelType.ETANOL,
    val calibration: Double = 1.0,
    val refuels: List<Refuel> = emptyList(),
) {
    val rangeKm: Double? get() = if (tankL != null && refKmL != null) tankL * refKmL else null
    val tankFraction: Double? get() = tankL?.let { (it / capacityL).coerceIn(0.0, 1.0) }
}

data class DeviceItem(val name: String, val address: String)

data class UiState(
    val conn: ConnState = ConnState.DISCONNECTED,
    val connStep: String = "",
    val error: String? = null,
    val isDemo: Boolean = false,
    val live: LiveData = LiveData(),
    val recent: List<LiveData> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val overall: Level = Level.OK,
    val stored: List<DtcInfo> = emptyList(),
    val pending: List<DtcInfo> = emptyList(),
    val milOn: Boolean? = null,
    val lastScanTs: Long? = null,
    val scanning: Boolean = false,
    val protocol: String = "",
    val adapter: String = "",
    val supportedCount: Int = 0,
    val popup: Alert? = null,
    val devices: List<DeviceItem> = emptyList(),
    val savedDevice: String? = null,
    val vehicle: String = "Peugeot 206 1.4 Flex 2008",
    val trips: List<Trip> = emptyList(),
    val scans: List<ScanRow> = emptyList(),
    val events: List<EventRow> = emptyList(),
    val tripStartTs: Long? = null,
    val tripKm: Double = 0.0,
    val demoOverheat: Boolean = false,
    val demoLowBattery: Boolean = false,
    val toast: String? = null,
    val themeMode: Int = 0, // 0 = automático, 1 = escuro, 2 = claro
    val notice: String? = null,
    val fuel: FuelUi = FuelUi(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val REPEAT_MS = 10 * 60_000L
        private const val IGNITION_OFF_MS = 15_000L
    }

    private class IgnitionOff : Exception()

    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("obd", Context.MODE_PRIVATE)
    val db = HistoryDb(app)
    private val exporter = Exporter(app, db)
    private val tank = FuelTank(prefs)

    private val _ui = MutableStateFlow(UiState(
        savedDevice = prefs.getString("device", null),
        vehicle = prefs.getString("vehicle", null) ?: "Peugeot 206 1.4 Flex 2008",
        themeMode = prefs.getInt("theme", 1),
    ))
    val ui: StateFlow<UiState> = _ui

    private var client: ObdClient? = null
    private var demo: DemoTransport? = null
    private var loop: Job? = null
    private var tripId: Long? = null
    private var stats = TripStats()
    private var sampleCount = 0
    private var trimHighSince: Long? = null
    private var lastSummarySaved = 0L
    /** Última vez que cada alerta foi gravado no histórico, para não repetir. */
    private val lastLogged = mutableMapOf<String, Pair<Long, Level>>()
    private var lastSampleSaved = 0L
    private val activeKeys = mutableMapOf<String, Level>()
    private val dismissedCritical = mutableSetOf<String>()
    // Consumo
    private var throttleMin: Int? = null
    private var throttleReadings = 0
    private var lastFuelTs: Long? = null
    private var smoothLh: Double? = null
    private var smoothSpeed: Double? = null

    init {
        TechLog.init(app.filesDir)
        publishFuel()
        viewModelScope.launch(Dispatchers.IO) { runCatching { db.repairOpenTrips() }; refreshHistory() }
    }

    // ---------- Bluetooth ----------

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) { _ui.update { it.copy(error = "Este celular não tem Bluetooth.") }; return }
        if (!adapter.isEnabled) { _ui.update { it.copy(error = "Ligue o Bluetooth do celular e tente de novo.") }; return }
        val list = runCatching { adapter.bondedDevices.map { DeviceItem(it.name ?: "Sem nome", it.address) } }.getOrDefault(emptyList())
            .sortedByDescending { d -> listOf("OBD", "ELM", "V-LINK", "VLINK", "ICAR", "KONNWEI", "VGATE").any { d.name.uppercase().contains(it) } }
        _ui.update { it.copy(devices = list, error = if (list.isEmpty()) "Nenhum aparelho pareado. Pareie o adaptador nas configurações de Bluetooth do celular (senha geralmente 1234 ou 0000)." else null) }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        prefs.edit().putString("device", address).apply()
        _ui.update { it.copy(savedDevice = address) }
        TechLog.newSession("Adaptador ${runCatching { device.name }.getOrNull() ?: address}")
        start(ObdClient(LoggingTransport(BluetoothTransport(adapter, device))), isDemo = false)
    }

    fun connectDemo() {
        val t = DemoTransport().also { demo = it }
        t.simulateOverheat = _ui.value.demoOverheat
        t.simulateLowBattery = _ui.value.demoLowBattery
        TechLog.newSession("Modo demonstração")
        start(ObdClient(LoggingTransport(t)), isDemo = true)
    }

    private fun start(c: ObdClient, isDemo: Boolean) {
        disconnect()
        client = c
        _ui.update { it.copy(conn = ConnState.CONNECTING, connStep = "Conectando ao adaptador…", error = null, notice = null, isDemo = isDemo) }
        loop = viewModelScope.launch(Dispatchers.IO) {
            try {
                _ui.update { it.copy(connStep = "Conversando com o carro… (pode levar até 15 segundos)") }
                c.initialize()
                TechLog.info = "OBD Fácil ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
                    "Veículo: ${_ui.value.vehicle}\nAdaptador: ${c.adapterVersion} · Protocolo: ${c.protocolName} (${c.protocolNumber})\n" +
                    "PIDs suportados: ${c.supported.sorted().joinToString(" ") { "%02X".format(it) }}"
                _ui.update { it.copy(conn = ConnState.CONNECTED, protocol = c.protocolName, adapter = c.adapterVersion,
                    supportedCount = c.supported.size, connStep = "") }
                throttleMin = null; throttleReadings = 0
                if (!isDemo) beginTrip() else { stats = TripStats(); lastFuelTs = null; smoothLh = null; smoothSpeed = null }
                scanDtcsInternal(c)
                pollLoop(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IgnitionOff) {
                endTrip()
                c.close()
                _ui.update { it.copy(conn = ConnState.DISCONNECTED, connStep = "", error = null,
                    notice = "Ignição desligada. A viagem foi salva no Histórico.",
                    live = LiveData(), recent = emptyList(), alerts = emptyList(), overall = Level.OK) }
            } catch (e: Exception) {
                endTrip()
                c.close()
                _ui.update { it.copy(conn = ConnState.DISCONNECTED, error = e.message ?: "Falha na conexão.", connStep = "") }
            }
        }
    }

    fun disconnect() {
        loop?.cancel(); loop = null
        client?.close(); client = null
        endTrip()
        activeKeys.clear(); dismissedCritical.clear(); trimHighSince = null
        _ui.update { it.copy(conn = ConnState.DISCONNECTED, live = LiveData(), recent = emptyList(), alerts = emptyList(),
            overall = Level.OK, connStep = "", tripStartTs = null, tripKm = 0.0) }
        publishFuel()
    }

    // Rápidos: o que o cálculo de consumo precisa a cada leitura. Lentos: o que muda devagar.
    private val fast = listOf(Pid.RPM, Pid.SPEED, Pid.MAP, Pid.THROTTLE)
    private val slow = listOf(Pid.COOLANT, Pid.FUEL_STATUS, Pid.LOAD, Pid.INTAKE, Pid.SHORT_TRIM, Pid.LONG_TRIM,
        Pid.TIMING, Pid.STATUS, Pid.MODULE_VOLTAGE)

    private suspend fun pollLoop(c: ObdClient) {
        var cycle = 0
        var failures = 0
        var live = LiveData()
        while (currentCoroutineContext().isActive) {
            try {
                val pids = if (cycle % 3 == 0) fast + slow else fast
                live = c.read(pids, live, includeVoltage = cycle % 3 == 0)
                failures = 0
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (++failures >= 3) throw IllegalStateException("A conexão com o adaptador caiu. Toque em Conectar para tentar de novo.")
                delay(500); continue
            }
            // Ignição desligada: a central para de responder (o adaptador continua ligado na bateria)
            if (System.currentTimeMillis() - c.lastEcuResponse > IGNITION_OFF_MS) throw IgnitionOff()
            cycle++
            onNewData(live)
            delay(150)
        }
    }

    private fun onNewData(raw: LiveData) {
        val d = withFuel(raw)
        stats.add(d)
        tripId?.let { id ->
            if (d.timestamp - lastSampleSaved >= 2000) { db.addSample(id, d); lastSampleSaved = d.timestamp; sampleCount++ }
            // Guarda o resumo a cada 30 s: se o app for fechado de repente, a viagem não se perde
            if (d.timestamp - lastSummarySaved >= 30_000 && sampleCount >= 5) {
                db.finishTrip(id, stats, d.timestamp); lastSummarySaved = d.timestamp
                tank.save(); TechLog.save()
            }
        }
        // Conta há quanto tempo a correção da mistura está fora do normal
        val trimActive = _ui.value.alerts.any { it.key == "trim" }
        val trimLimit = if (trimActive) HealthAnalyzer.TRIM_RELEASE else HealthAnalyzer.TRIM_LIMIT
        trimHighSince = if (HealthAnalyzer.trimOutOfRange(d, trimLimit)) (trimHighSince ?: d.timestamp) else null
        val trimHighFor = trimHighSince?.let { d.timestamp - it } ?: 0L
        val alerts = (HealthAnalyzer.analyze(d, _ui.value.stored + _ui.value.pending, trimHighFor, trimLimit) +
            listOfNotNull(fuelAlert())).sortedByDescending { it.level.ordinal }
        handleAlerts(alerts)
        _ui.update { s ->
            val cutoff = d.timestamp - 120_000
            s.copy(live = d, alerts = alerts, overall = HealthAnalyzer.overall(alerts),
                recent = (s.recent + d).filter { it.timestamp >= cutoff }, tripKm = stats.distanceKm)
        }
        publishFuel()
    }

    // ---------- Consumo ----------

    /** Calcula o consumo desta leitura e desconta do tanque. */
    private fun withFuel(d: LiveData): LiveData {
        d.throttlePct?.let { t -> throttleMin = minOf(throttleMin ?: t, t); throttleReadings++ }
        val closedMax = if (throttleReadings >= 5) throttleMin?.plus(2) else null
        val lh = FuelModel.litersPerHour(d, tank.fuelType, tank.calibration, closedMax)
        val out = d.copy(fuelLh = lh)
        val prev = lastFuelTs
        lastFuelTs = d.timestamp
        if (prev != null && lh != null && !_ui.value.isDemo) {
            val gap = d.timestamp - prev
            if (gap in 1..10_000) tank.consume(lh * gap / 3_600_000.0, (d.speedKmh ?: 0) * gap / 3_600_000.0)
        }
        // Suaviza para o número não ficar pulando na tela
        if (lh != null) smoothLh = smoothLh?.let { it + (lh - it) * 0.4 } ?: lh
        d.speedKmh?.let { v -> smoothSpeed = smoothSpeed?.let { it + (v - it) * 0.4 } ?: v.toDouble() }
        return out
    }

    private fun fuelAlert(): Alert? {
        val l = tank.litersInTank ?: return null
        if (l >= 6.0) return null
        val km = _ui.value.fuel.refKmL?.let { " (uns ${"%.0f".format(it * l)} km)" } ?: ""
        return Alert("fuel", Level.WARNING, "Combustível na reserva",
            "Pela estimativa do app restam cerca de ${"%.0f".format(l)} litros$km.",
            "Abasteça assim que puder. Rodar na reserva pode puxar sujeira do fundo do tanque para a bomba.")
    }

    private fun publishFuel() {
        val live = _ui.value.live
        val connected = _ui.value.conn == ConnState.CONNECTED
        val cut = connected && live.fuelLh == 0.0 && (live.rpm ?: 0) > 400
        _ui.update { s -> s.copy(fuel = s.fuel.copy(
            instantLh = if (connected) smoothLh else null,
            instantKmL = if (connected) FuelModel.kmPerLiter(smoothSpeed, smoothLh) else null,
            fuelCut = cut,
            tripFuelL = stats.fuelL,
            tripKmL = if (stats.fuelL > 0.1 && stats.distanceKm > 0.3) stats.distanceKm / stats.fuelL else null,
            tankL = tank.litersInTank,
            capacityL = tank.capacityL,
            fuelType = tank.fuelType,
            calibration = tank.calibration,
        )) }
    }

    /** Consumo médio estimado das últimas viagens (até ~300 km), usado para a autonomia. */
    private fun referenceKmPerL(trips: List<Trip>): Double? {
        var km = 0.0; var l = 0.0
        for (t in trips) {
            val f = t.fuelL ?: continue
            if (f <= 0.05) continue
            km += t.distanceKm; l += f
            if (km >= 300) break
        }
        return if (km >= 5 && l > 0) km / l else null
    }

    fun refuel(liters: Double, pricePerL: Double?, total: Double?, full: Boolean, levelBefore: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = tank.refuel(liters, full, levelBefore)
            db.addRefuel(liters, pricePerL, total, full, tank.fuelType.label, r.realKmPerL)
            val msg = when {
                r.realKmPerL != null -> "Abastecimento salvo. Consumo real deste tanque: ${"%.1f".format(r.realKmPerL)} km/l. O cálculo foi ajustado."
                full -> "Abastecimento salvo. Tanque cheio: a partir de agora o app mede o consumo real."
                else -> "Abastecimento salvo."
            }
            _ui.update { it.copy(toast = msg) }
            publishFuel(); refreshHistory()
        }
    }

    fun deleteRefuel(id: Long) = viewModelScope.launch(Dispatchers.IO) { db.deleteRefuel(id); refreshHistory() }

    fun setTankLevel(fraction: Double) { tank.setLevel(fraction); publishFuel() }

    fun setFuelType(f: FuelType) { tank.fuelType = f; publishFuel() }

    fun setTankCapacity(l: Double) { tank.capacityL = l; publishFuel() }

    /** Ajusta o cálculo para bater com o consumo real que o usuário conhece. */
    fun calibrateWithKnownKmL(real: Double) {
        val est = _ui.value.fuel.refKmL
        val msg = if (est == null) "Rode pelo menos 5 km com o app conectado antes de calibrar." else {
            tank.calibrateTo(real, est)
            publishFuel()
            "Pronto! As próximas leituras usam ${"%.1f".format(real)} km/l como referência."
        }
        _ui.update { it.copy(toast = msg) }
    }

    fun resetFuel() { tank.reset(); publishFuel() }

    /**
     * Grava e avisa só quando um alerta é novo, quando piora, ou depois de 10 minutos.
     * Assim um valor oscilando perto do limite não gera dezenas de alertas iguais.
     */
    private fun handleAlerts(alerts: List<Alert>) {
        val nowTs = System.currentTimeMillis()
        val now = alerts.filter { it.level >= Level.WARNING }.associateBy { it.key }
        for ((key, a) in now) {
            val last = lastLogged[key]
            val isNews = last == null || a.level > last.second || nowTs - last.first >= REPEAT_MS
            if (isNews) {
                lastLogged[key] = nowTs to a.level
                if (!_ui.value.isDemo) { db.addEvent(tripId, a); stats.alerts++ }
                if (a.level == Level.CRITICAL && key !in dismissedCritical) {
                    vibrate()
                    _ui.update { it.copy(popup = a) }
                } else if (a.level == Level.WARNING) vibrate(short = true)
            }
            activeKeys[key] = a.level
        }
        activeKeys.keys.filter { it !in now }.forEach { activeKeys.remove(it) }
        // depois de 10 minutos, um alerta grave dispensado pode voltar a aparecer
        dismissedCritical.removeAll { k -> lastLogged[k]?.let { nowTs - it.first >= REPEAT_MS } ?: true }
    }

    fun dismissPopup() {
        _ui.value.popup?.let { dismissedCritical += it.key }
        _ui.update { it.copy(popup = null) }
    }

    private fun vibrate(short: Boolean = false) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else ctx.getSystemService(Vibrator::class.java)
        val effect = if (short) VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
        else VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
        runCatching { v?.vibrate(effect) }
    }

    // ---------- Falhas ----------

    fun scanDtcs() {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) { scanDtcsInternal(c) }
    }

    private suspend fun scanDtcsInternal(c: ObdClient) {
        _ui.update { it.copy(scanning = true) }
        try {
            val r = c.readDtcs()
            if (!_ui.value.isDemo) db.addScan(r.milOn, r.stored, r.pending)
            _ui.update { it.copy(stored = r.stored.map(DtcDatabase::lookup), pending = r.pending.map(DtcDatabase::lookup),
                milOn = r.milOn, lastScanTs = System.currentTimeMillis(), scanning = false) }
            refreshHistory()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            _ui.update { it.copy(scanning = false, toast = e.message ?: "Não foi possível ler as falhas agora.") }
        }
    }

    fun clearDtcs() {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { c.clearDtcs() }.getOrDefault(false)
            _ui.update { it.copy(toast = if (ok) "Códigos apagados. Se o problema continuar, eles voltam." else "O carro não aceitou apagar. Tente com o motor desligado e a ignição ligada.") }
            delay(800)
            scanDtcsInternal(c)
        }
    }

    // ---------- Viagens / histórico ----------

    private fun beginTrip() {
        stats = TripStats(); sampleCount = 0; lastSampleSaved = 0; lastSummarySaved = 0; lastLogged.clear()
        lastFuelTs = null; smoothLh = null; smoothSpeed = null
        val now = System.currentTimeMillis()
        tripId = db.startTrip(now)
        _ui.update { it.copy(tripStartTs = now) }
    }

    private fun endTrip() {
        val id = tripId ?: return
        tripId = null
        if (sampleCount < 5) db.deleteTrip(id) else db.finishTrip(id, stats, System.currentTimeMillis())
        tank.save(); TechLog.save()
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val trips = db.trips().filter { it.endTs != null }
            val refKmL = referenceKmPerL(trips)
            _ui.update { it.copy(trips = trips, scans = db.scans(), events = db.events(),
                fuel = it.fuel.copy(refuels = db.refuels(), refKmL = refKmL)) }
        }
    }

    fun deleteTrip(id: Long) = viewModelScope.launch(Dispatchers.IO) { db.deleteTrip(id); refreshHistory() }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { db.clearAll(); tank.reset(); publishFuel(); refreshHistory() }

    fun samples(tripId: Long) = db.samples(tripId)
    fun tripEvents(tripId: Long) = db.events(tripId)

    fun exportTechLog() = export { exporter.techLog(TechLog.dump()) }
    fun exportPdf() = export { exporter.reportPdf(_ui.value.vehicle) }
    fun exportCsv() = export { exporter.summaryCsv() }
    fun exportTrip(t: Trip) = export { exporter.tripCsv(t) }

    private fun export(block: () -> java.io.File) {
        viewModelScope.launch {
            val f = runCatching { withContext(Dispatchers.IO) { block() } }.getOrNull()
            if (f == null) _ui.update { it.copy(toast = "Não foi possível gerar o arquivo.") } else exporter.share(f)
        }
    }

    // ---------- Ajustes ----------

    fun setVehicle(name: String) { prefs.edit().putString("vehicle", name).apply(); _ui.update { it.copy(vehicle = name) } }
    fun setDemoOverheat(on: Boolean) { demo?.simulateOverheat = on; _ui.update { it.copy(demoOverheat = on) } }
    fun setDemoLowBattery(on: Boolean) { demo?.simulateLowBattery = on; _ui.update { it.copy(demoLowBattery = on) } }
    fun setTheme(mode: Int) { prefs.edit().putInt("theme", mode).apply(); _ui.update { it.copy(themeMode = mode) } }
    fun consumeToast() = _ui.update { it.copy(toast = null) }
    fun showError(msg: String) = _ui.update { it.copy(error = msg) }
    fun clearError() = _ui.update { it.copy(error = null) }

    override fun onCleared() { disconnect(); super.onCleared() }
}
