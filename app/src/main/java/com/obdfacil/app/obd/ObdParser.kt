package com.obdfacil.app.obd

/**
 * Funções puras para interpretar as respostas de texto do ELM327.
 * Ficam separadas da parte de Bluetooth para poderem ser testadas.
 */
object ObdParser {

    private val HEX = Regex("^[0-9A-F]+$")

    /** Remove espaços, prompt, eco e mensagens de status. Devolve as linhas úteis. */
    fun cleanLines(raw: String): List<String> =
        raw.replace(">", "")
            .replace("SEARCHING...", "")
            .split('\r', '\n')
            .map { it.replace(" ", "").trim().uppercase() }
            .filter { it.isNotEmpty() }

    /**
     * Verdadeiro quando não veio nenhum dado útil (NO DATA, UNABLE TO CONNECT, ?, etc.).
     * Linhas de status como "BUS INIT: ...OK" (comuns em carros K-Line como o 206) são ignoradas.
     */
    fun isError(raw: String): Boolean =
        cleanLines(raw).none { HEX.matches(it.substringAfter(':')) && it.substringAfter(':').length >= 2 }

    /**
     * Encontra os bytes de dados de uma resposta do modo 01.
     * Ex.: pid "0C", resposta "410C1AF8" -> [0x1A, 0xF8]
     */
    fun parseMode01(pid: String, raw: String, expectedBytes: Int): IntArray? {
        if (isError(raw)) return null
        val header = "41" + pid.uppercase()
        for (line in cleanLines(raw)) {
            val data = line.substringAfter(':') // quadros CAN multi-linha "0:41..."
            val idx = data.indexOf(header)
            if (idx < 0) continue
            val hex = data.substring(idx + header.length)
            if (hex.length < expectedBytes * 2) continue
            return IntArray(expectedBytes) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16) }
        }
        return null
    }

    /** Lê a máscara de PIDs suportados (resposta de 0100, 0120, 0140...). */
    fun parseSupportedPids(basePid: Int, raw: String): Set<Int> {
        val pidHex = "%02X".format(basePid)
        val bytes = parseMode01(pidHex, raw, 4) ?: return emptySet()
        val result = mutableSetOf<Int>()
        var mask = 0L
        bytes.forEach { mask = (mask shl 8) or it.toLong() }
        for (i in 0 until 32) {
            if ((mask shr (31 - i)) and 1L == 1L) result += basePid + i + 1
        }
        return result
    }

    /** Converte 4 dígitos hex (ex.: "0133") no código legível (ex.: "P0133"). */
    fun decodeDtc(hex4: String): String {
        val first = hex4[0].digitToInt(16)
        val letter = "PCBU"[first shr 2]
        val digit = first and 0x3
        return "$letter$digit${hex4.substring(1).uppercase()}"
    }

    /**
     * Lê os códigos de falha da resposta do modo 03 (ou 07, header "47").
     * Funciona para carros antigos (K-Line/ISO 9141/KWP: 3 códigos por linha)
     * e para CAN (byte de contagem logo após o "43").
     */
    fun parseDtcs(raw: String, isCan: Boolean, modeHeader: String = "43"): List<String> {
        if (isError(raw)) return emptyList()
        val lines = cleanLines(raw)
        val codes = mutableListOf<String>()

        if (isCan) {
            // Junta quadros multi-linha ("0:4302...", "1:...") e ignora a linha de tamanho ("00A")
            val joined = lines
                .filterNot { it.length <= 3 && !it.contains(':') }
                .joinToString("") { it.substringAfter(':') }
            var pos = 0
            while (true) {
                val idx = joined.indexOf(modeHeader, pos)
                if (idx < 0 || idx + 4 > joined.length) break
                val count = joined.substring(idx + 2, idx + 4).toIntOrNull(16) ?: break
                var p = idx + 4
                repeat(count) {
                    if (p + 4 <= joined.length) {
                        val c = joined.substring(p, p + 4)
                        if (c != "0000") codes += decodeDtc(c)
                    }
                    p += 4
                }
                pos = p
                if (count == 0) pos = idx + 4
            }
        } else {
            for (line in lines) {
                if (!line.startsWith(modeHeader)) continue
                val payload = line.substring(2)
                var p = 0
                while (p + 4 <= payload.length) {
                    val c = payload.substring(p, p + 4)
                    if (c != "0000") codes += decodeDtc(c)
                    p += 4
                }
            }
        }
        return codes.distinct()
    }

    /** "12.6V" -> 12.6 */
    fun parseVoltage(raw: String): Double? =
        Regex("""(\d{1,2}[.,]\d)""").find(raw.replace(" ", ""))
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
}
