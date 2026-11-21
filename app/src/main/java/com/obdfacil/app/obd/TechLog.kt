package com.obdfacil.app.obd

import java.io.File
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
    private var file: File? = null

    /** O registro também é gravado em arquivo, para não se perder se o Android fechar o app. */
    fun init(dir: File) { file = File(dir, "registro_tecnico.txt") }

    @Synchronized fun save() {
        val f = file ?: return
        if (start.isEmpty() && recent.isEmpty()) return
        runCatching { f.writeText(build()) }
    }

    @Synchronized fun add(line: String) {
        val l = "${fmt.format(Date())} $line"
        if (start.size < MAX_START) start += l
        else { recent.addLast(l); if (recent.size > MAX_RECENT) recent.removeFirst() }
    }

    @Synchronized fun newSession(title: String) {
        start.clear(); recent.clear()
        add("=== $title ===")
    }

    /** Conexão atual se houver; senão, a última gravada em arquivo. */
    @Synchronized fun dump(): String {
        if (start.isNotEmpty()) return build()
        return file?.takeIf { it.exists() }?.readText()
            ?: "Nenhuma conexão registrada ainda. Conecte ao carro e tente de novo."
    }

    private fun build(): String = buildString {
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

    override fun close() { TechLog.add("desconectado"); inner.close(); TechLog.save() }
}
