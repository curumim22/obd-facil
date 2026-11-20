package com.obdfacil.app.obd

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro técnico: guarda a conversa crua com o adaptador (comandos e respostas).
 * Serve para entender por que algum dado não aparece no carro de um usuário.
 * Mantém o início da conexão inteiro e as últimas leituras.
 */
object TechLog {
    private const val MAX_START = 400
    private const val MAX_RECENT = 4000
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val start = ArrayList<String>()
    private val recent = ArrayDeque<String>()
    @Volatile var info: String = ""

    @Synchronized fun add(line: String) {
        val l = "${fmt.format(Date())} $line"
        if (start.size < MAX_START) start += l
        else { recent.addLast(l); if (recent.size > MAX_RECENT) recent.removeFirst() }
    }

    @Synchronized fun newSession(title: String) {
        start.clear(); recent.clear()
        add("=== $title ===")
    }

    @Synchronized fun dump(): String = buildString {
        appendLine(info)
        appendLine("--- Início da conexão ---")
        start.forEach { appendLine(it) }
        if (recent.isNotEmpty()) { appendLine("--- Últimas leituras ---"); recent.forEach { appendLine(it) } }
    }

    fun clean(raw: String) = raw.replace("\r", "⏎").replace("\n", "")
}

/** Envolve o transporte real e anota tudo no registro técnico. */
class LoggingTransport(private val inner: ObdTransport) : ObdTransport {
    override suspend fun connect() {
        TechLog.add("conectando…")
        try { inner.connect(); TechLog.add("conectado") }
        catch (e: Exception) { TechLog.add("ERRO ao conectar: ${e.message} / ${e.cause?.message}"); throw e }
    }

    override suspend fun send(command: String, timeoutMs: Long): String {
        val t0 = System.currentTimeMillis()
        return try {
            val r = inner.send(command, timeoutMs)
            TechLog.add("> $command  < ${TechLog.clean(r)}  (${System.currentTimeMillis() - t0} ms)")
            r
        } catch (e: Exception) {
            TechLog.add("> $command  ERRO: ${e.message}")
            throw e
        }
    }

    override fun close() { TechLog.add("desconectado"); inner.close() }
}
