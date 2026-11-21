package com.obdfacil.app.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fala o "idioma" OBD2 com o adaptador: inicializa, lê sensores e códigos de falha. */
class ObdClient(private val transport: ObdTransport) {

    private val lock = Mutex()
    var supported: Set<Int> = emptySet(); private set
    var protocolNumber: String = ""; private set
    var adapterVersion: String = ""; private set
    val isCan: Boolean get() = protocolNumber.trimStart('A').firstOrNull()?.let { it in '6'..'9' || it in 'A'..'C' } ?: false
    private var useAdapterVoltage = true
    /** Hora da última resposta válida da central do motor (não conta a tensão lida pelo adaptador). */
    @Volatile var lastEcuResponse: Long = System.currentTimeMillis(); private set

    val protocolName: String
        get() = when (protocolNumber.trimStart('A')) {
            "1" -> "SAE J1850 PWM"; "2" -> "SAE J1850 VPW"
            "3" -> "ISO 9141-2 (K-Line)"; "4" -> "KWP2000 5 baud (K-Line)"; "5" -> "KWP2000 rápido (K-Line)"
            "6", "7", "8", "9" -> "CAN (ISO 15765-4)"
            else -> "Automático"
        }

    suspend fun initialize() = lock.withLock {
        transport.connect()
        adapterVersion = ObdParser.cleanLines(transport.send("ATZ", 5000)).lastOrNull { it.contains("ELM") } ?: "ELM327"
        delay(500)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) transport.send(cmd, 2000)

        // A primeira consulta faz o adaptador descobrir o protocolo do carro (pode demorar em K-Line)
        var first = transport.send("0100", 15000)
        if (ObdParser.isError(first)) { delay(1000); first = transport.send("0100", 15000) }
        if (ObdParser.isError(first)) throw IllegalStateException(
            "O adaptador conectou, mas o carro não respondeu. Ligue a ignição (ou o motor) e tente de novo."
        )
        protocolNumber = ObdParser.cleanLines(transport.send("ATDPN", 2000)).firstOrNull() ?: ""

        val set = ObdParser.parseSupportedPids(0x00, first).toMutableSet()
        if (0x20 in set) {
            set += ObdParser.parseSupportedPids(0x20, transport.send("0120"))
            if (0x40 in set) set += ObdParser.parseSupportedPids(0x40, transport.send("0140"))
        }
        supported = set
        useAdapterVoltage = Pid.MODULE_VOLTAGE.number !in supported
        lastEcuResponse = System.currentTimeMillis()
    }

    fun supports(pid: Pid) = pid == Pid.STATUS || pid.number in supported

    /** Lê uma lista de PIDs e devolve o novo estado. */
    suspend fun read(pids: List<Pid>, base: LiveData, includeVoltage: Boolean): LiveData = lock.withLock {
        var data = base.copy(timestamp = System.currentTimeMillis())
        for (pid in pids) {
            if (!supports(pid)) continue
            val raw = transport.send("01" + pid.code, 3000)
            val bytes = ObdParser.parseMode01(pid.code, raw, pid.bytes) ?: continue
            data = PidMath.apply(pid, bytes, data)
            lastEcuResponse = System.currentTimeMillis()
        }
        if (includeVoltage && useAdapterVoltage) {
            // Tensão medida pelo próprio adaptador no pino 16 da tomada (= bateria)
            ObdParser.parseVoltage(transport.send("ATRV", 2000))?.let { data = data.copy(voltage = it) }
        }
        data
    }

    data class DtcResult(val stored: List<String>, val pending: List<String>, val milOn: Boolean?)

    suspend fun readDtcs(): DtcResult = lock.withLock {
        // Status da luz da injeção; carros K-Line às vezes não respondem na primeira
        val status = ObdParser.parseMode01("01", transport.send("0101", 6000), 4)
            ?: run { delay(300); ObdParser.parseMode01("01", transport.send("0101", 6000), 4) }
            // Em K-Line, "NO DATA" no modo 03 também significa "sem falhas". Por isso só confiamos
            // na leitura se a central respondeu ao status: senão a ignição provavelmente está desligada.
            ?: throw IllegalStateException("O carro não respondeu. Ligue a ignição e tente de novo.")
        val stored = ObdParser.parseDtcs(transport.send("03", 6000), isCan, "43")
        val pending = ObdParser.parseDtcs(transport.send("07", 6000), isCan, "47").filterNot { it in stored }
        DtcResult(stored, pending, status?.let { (it[0] and 0x80) != 0 })
    }

    suspend fun clearDtcs(): Boolean = lock.withLock {
        val r = transport.send("04", 6000)
        ObdParser.cleanLines(r).any { it.startsWith("44") }
    }

    fun close() = transport.close()
}
