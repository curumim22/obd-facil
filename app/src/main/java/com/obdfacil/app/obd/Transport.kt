package com.obdfacil.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Canal de texto com o adaptador: envia um comando e devolve a resposta até o prompt '>'. */
interface ObdTransport {
    suspend fun connect()
    suspend fun send(command: String, timeoutMs: Long = 4000): String
    fun close()
}

/** Adaptador ELM327 Bluetooth clássico (perfil SPP). */
@SuppressLint("MissingPermission")
class BluetoothTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
) : ObdTransport {

    private var socket: BluetoothSocket? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        runCatching { adapter.cancelDiscovery() }
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (first: IOException) {
            // Muitos adaptadores genéricos só aceitam o canal 1 direto
            try {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                (m.invoke(device, 1) as BluetoothSocket).also { it.connect() }
            } catch (e: Exception) {
                throw IOException("Não foi possível conectar ao adaptador. Verifique se o carro está ligado e o adaptador encaixado.", first)
            }
        }
        socket = s
    }

    override suspend fun send(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("Adaptador desconectado")
        val input = s.inputStream
        val output = s.outputStream
        // Descarta sobras de respostas anteriores
        while (input.available() > 0) input.read()
        output.write((command + "\r").toByteArray())
        output.flush()

        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val c = input.read()
                if (c < 0) throw IOException("Conexão encerrada")
                if (c.toChar() == '>') return@withContext sb.toString()
                sb.append(c.toChar())
            } else {
                delay(5)
            }
        }
        sb.toString()
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/**
 * Simula um ELM327 ligado num Peugeot 206 1.4 Flex, para testar o app sem o carro.
 * Responde no mesmo formato de texto do adaptador real (protocolo K-Line).
 */
class DemoTransport : ObdTransport {
    @Volatile var simulateOverheat = false
    @Volatile var simulateLowBattery = false
    private var dtcsCleared = false
    private val start = System.currentTimeMillis()

    override suspend fun connect() { delay(1200) }

    override suspend fun send(command: String, timeoutMs: Long): String {
        delay(40)
        val t = (System.currentTimeMillis() - start) / 1000.0
        val cmd = command.uppercase().replace(" ", "")
        fun hex(v: Int) = "%02X".format(v.coerceIn(0, 255))

        val coolant = if (simulateOverheat) (95 + t * 0.4).coerceAtMost(118.0)
        else (35 + t * 0.9).coerceAtMost(91.0)
        val cycle = (t % 60) / 60.0
        val speed = when {
            cycle < 0.15 -> 0.0
            cycle < 0.45 -> (cycle - 0.15) / 0.30 * 70
            cycle < 0.75 -> 70 + Math.sin(t) * 6
            else -> (1 - (cycle - 0.75) / 0.25) * 70
        }.coerceAtLeast(0.0)
        val rpm = if (speed < 1) 850 + Math.sin(t * 3) * 25 else 1400 + speed * 28 + Math.sin(t) * 80

        return when {
            cmd == "ATZ" -> "ELM327 v1.5\r\r"
            cmd == "ATRV" -> if (simulateLowBattery) "11.6V\r" else if (rpm > 400) "14.1V\r" else "12.5V\r"
            cmd == "ATDPN" -> "A3\r"
            cmd.startsWith("AT") -> "OK\r"
            cmd == "0100" -> "4100BE3EB811\r"
            cmd == "0120" -> "412080000000\r"   // 0x42 não suportado (comum em carros antigos)
            cmd == "0140" -> "NO DATA\r"
            cmd == "0101" -> if (dtcsCleared) "410100076500\r" else "410182076500\r"
            cmd == "0104" -> "4104${hex((20 + speed * 0.7).toInt() * 255 / 100)}\r"
            cmd == "0105" -> "4105${hex(coolant.toInt() + 40)}\r"
            cmd == "0106" -> "4106${hex(128 + (Math.sin(t * 2) * 6).toInt())}\r"
            cmd == "0107" -> "4107${hex(if (dtcsCleared) 131 else 151)}\r"   // ~ +18% (mistura pobre)
            cmd == "010B" -> "410B${hex((35 + speed * 0.6).toInt())}\r"
            cmd == "010C" -> { val v = (rpm * 4).toInt(); "410C${hex(v shr 8)}${hex(v and 0xFF)}\r" }
            cmd == "010D" -> "410D${hex(speed.toInt())}\r"
            cmd == "010E" -> "410E${hex(((12 + speed * 0.2) + 64).toInt() * 2)}\r"
            cmd == "010F" -> "410F${hex(28 + 40 + (t / 30).toInt().coerceAtMost(12))}\r"
            cmd == "0111" -> "4111${hex((if (speed < 1) 0.0 else 12 + speed * 0.3).toInt() * 255 / 100)}\r"
            cmd == "03" -> if (dtcsCleared) "43000000000000\r" else "43017104200000\r"  // P0171 e P0420
            cmd == "07" -> "47000000000000\r"
            cmd == "04" -> { dtcsCleared = true; "44\r" }
            else -> "NO DATA\r"
        }
    }

    override fun close() {}
}
