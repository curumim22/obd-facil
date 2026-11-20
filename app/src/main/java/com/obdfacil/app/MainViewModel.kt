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
import com.obdfacil.app.data.EventRow
import com.obdfacil.app.data.HistoryDb
import com.obdfacil.app.data.ScanRow
import com.obdfacil.app.data.Trip
import com.obdfacil.app.data.TripStats
import com.obdfacil.app.export.Exporter
import com.obdfacil.app.obd.BluetoothTransport
import com.obdfacil.app.obd.DemoTransport
import com.obdfacil.app.obd.LiveData
import com.obdfacil.app.obd.ObdClient
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
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("obd", Context.MODE_PRIVATE)
    val db = HistoryDb(app)
    private val exporter = Exporter(app, db)

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
    private var lastSampleSaved = 0L
    private val activeKeys = mutableMapOf<String, Level>()
    private val dismissedCritical = mutableSetOf<String>()

    init { refreshHistory() }

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
        start(ObdClient(BluetoothTransport(adapter, device)), isDemo = false)
    }

    fun connectDemo() {
        val t = DemoTransport().also { demo = it }
        t.simulateOverheat = _ui.value.demoOverheat
        t.simulateLowBattery = _ui.value.demoLowBattery
        start(ObdClient(t), isDemo = true)
    }

    private fun start(c: ObdClient, isDemo: Boolean) {
        disconnect()
        client = c
        _ui.update { it.copy(conn = ConnState.CONNECTING, connStep = "Conectando ao adaptador…", error = null, isDemo = isDemo) }
        loop = viewModelScope.launch(Dispatchers.IO) {
            try {
                _ui.update { it.copy(connStep = "Conversando com o carro… (pode levar até 15 segundos)") }
                c.initialize()
                _ui.update { it.copy(conn = ConnState.CONNECTED, protocol = c.protocolName, adapter = c.adapterVersion,
                    supportedCount = c.supported.size, connStep = "") }
                beginTrip()
                scanDtcsInternal(c)
                pollLoop(c)
            } catch (e: CancellationException) {
                throw e
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
    }

    private val fast = listOf(Pid.RPM, Pid.SPEED, Pid.COOLANT, Pid.LOAD, Pid.THROTTLE)
    private val slow = listOf(Pid.INTAKE, Pid.SHORT_TRIM, Pid.LONG_TRIM, Pid.MAP, Pid.TIMING, Pid.STATUS, Pid.MODULE_VOLTAGE)

    private suspend fun pollLoop(c: ObdClient) {
        var cycle = 0
        var failures = 0
        var live = LiveData()
        while (currentCoroutineContext().isActive) {
            try {
                val pids = if (cycle % 4 == 0) fast + slow else fast
                live = c.read(pids, live, includeVoltage = cycle % 4 == 0)
                failures = 0
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (++failures >= 3) throw IllegalStateException("A conexão com o adaptador caiu. Toque em Conectar para tentar de novo.")
                delay(500); continue
            }
            cycle++
            onNewData(live)
            delay(150)
        }
    }

    private fun onNewData(d: LiveData) {
        stats.add(d)
        tripId?.let { id ->
            if (d.timestamp - lastSampleSaved >= 2000) { db.addSample(id, d); lastSampleSaved = d.timestamp; sampleCount++ }
        }
        // Conta há quanto tempo a correção da mistura está fora do normal
        trimHighSince = if (HealthAnalyzer.trimOutOfRange(d)) (trimHighSince ?: d.timestamp) else null
        val trimHighFor = trimHighSince?.let { d.timestamp - it } ?: 0L
        val alerts = HealthAnalyzer.analyze(d, _ui.value.stored + _ui.value.pending, trimHighFor)
        handleAlerts(alerts)
        _ui.update { s ->
            val cutoff = d.timestamp - 120_000
            s.copy(live = d, alerts = alerts, overall = HealthAnalyzer.overall(alerts),
                recent = (s.recent + d).filter { it.timestamp >= cutoff }, tripKm = stats.distanceKm)
        }
    }

    private fun handleAlerts(alerts: List<Alert>) {
        val now = alerts.filter { it.level >= Level.WARNING }.associateBy { it.key }
        for ((key, a) in now) {
            val prev = activeKeys[key]
            if (prev == null || a.level > prev) {
                db.addEvent(tripId, a); stats.alerts++
                if (a.level == Level.CRITICAL && key !in dismissedCritical) {
                    vibrate()
                    _ui.update { it.copy(popup = a) }
                } else if (a.level == Level.WARNING) vibrate(short = true)
            }
            activeKeys[key] = a.level
        }
        // alerta que sumiu pode voltar a avisar no futuro
        activeKeys.keys.filter { it !in now }.forEach { activeKeys.remove(it); dismissedCritical.remove(it) }
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
            db.addScan(r.milOn, r.stored, r.pending)
            _ui.update { it.copy(stored = r.stored.map(DtcDatabase::lookup), pending = r.pending.map(DtcDatabase::lookup),
                milOn = r.milOn, lastScanTs = System.currentTimeMillis(), scanning = false) }
            refreshHistory()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            _ui.update { it.copy(scanning = false, toast = "Não foi possível ler as falhas agora.") }
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
        stats = TripStats(); sampleCount = 0; lastSampleSaved = 0
        val now = System.currentTimeMillis()
        tripId = db.startTrip(now)
        _ui.update { it.copy(tripStartTs = now) }
    }

    private fun endTrip() {
        val id = tripId ?: return
        tripId = null
        if (sampleCount < 5) db.deleteTrip(id) else db.finishTrip(id, stats, System.currentTimeMillis())
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val trips = db.trips().filter { it.endTs != null }
            _ui.update { it.copy(trips = trips, scans = db.scans(), events = db.events()) }
        }
    }

    fun deleteTrip(id: Long) = viewModelScope.launch(Dispatchers.IO) { db.deleteTrip(id); refreshHistory() }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { db.clearAll(); refreshHistory() }

    fun samples(tripId: Long) = db.samples(tripId)
    fun tripEvents(tripId: Long) = db.events(tripId)

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
