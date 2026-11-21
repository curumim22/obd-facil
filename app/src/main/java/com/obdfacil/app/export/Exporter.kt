package com.obdfacil.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.obdfacil.app.analysis.DtcDatabase
import com.obdfacil.app.data.HistoryDb
import com.obdfacil.app.data.Trip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gera arquivos CSV (abre no Excel) e PDF (para mostrar ao mecânico) e abre o "Compartilhar". */
class Exporter(private val context: Context, private val db: HistoryDb) {

    private val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    private val stampSec = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    private val fileStamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
    private fun dir() = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun num(v: Number?) = v?.toString()?.replace('.', ',') ?: ""

    /** Todas as leituras de uma viagem, segundo a segundo. Separador ';' para o Excel em português. */
    fun tripCsv(trip: Trip): File {
        val f = File(dir(), "viagem_${fileStamp.format(Date(trip.startTs))}.csv")
        f.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿") // BOM: acentos corretos no Excel
            w.write("Data/Hora;RPM;Velocidade (km/h);Temp. motor (°C);Temp. ar (°C);Carga (%);Acelerador (%);Bateria (V);Ajuste curto (%);Ajuste longo (%);Pressão coletor (kPa);Consumo estimado (L/h)\n")
            db.samples(trip.id).forEach { s ->
                w.write(listOf(stampSec.format(Date(s.timestamp)),
                    num(s.rpm), num(s.speedKmh), num(s.coolantC), num(s.intakeC), num(s.loadPct),
                    num(s.throttlePct), num(s.voltage), num(s.shortTrimPct), num(s.longTrimPct), num(s.mapKpa),
                    s.fuelLh?.let { "%.2f".format(it).replace('.', ',') } ?: ""
                ).joinToString(";") + "\n")
            }
        }
        return f
    }

    /** Resumo de todas as viagens + todas as leituras de falhas. */
    fun summaryCsv(): File {
        val f = File(dir(), "historico_${fileStamp.format(Date())}.csv")
        f.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write("VIAGENS\nInício;Duração (min);Distância (km);Vel. média (km/h);Vel. máx (km/h);RPM máx;Temp. máx (°C);Bateria mín (V);Bateria máx (V);Alertas;Combustível estimado (L);Consumo estimado (km/l)\n")
            db.trips().filter { it.endTs != null }.forEach { t ->
                w.write(listOf(stamp.format(Date(t.startTs)), num(t.durationMin), "%.1f".format(t.distanceKm),
                    "%.0f".format(t.avgSpeed), num(t.maxSpeed), num(t.maxRpm), num(t.maxCoolant),
                    num(t.minVoltage), num(t.maxVoltage), num(t.alerts),
                    t.fuelL?.let { "%.2f".format(it).replace('.', ',') } ?: "",
                    t.kmPerL?.let { "%.1f".format(it).replace('.', ',') } ?: "").joinToString(";") + "\n")
            }
            w.write("\nLEITURAS DE FALHAS\nData/Hora;Luz da injeção;Código;Descrição;Gravidade;Tipo\n")
            db.scans().forEach { s ->
                val mil = when (s.milOn) { true -> "Acesa"; false -> "Apagada"; null -> "" }
                if (s.codes.isEmpty() && s.pending.isEmpty()) w.write("${stamp.format(Date(s.ts))};$mil;-;Nenhuma falha;-;-\n")
                (s.codes.map { it to "Confirmada" } + s.pending.map { it to "Pendente" }).forEach { (c, type) ->
                    val info = DtcDatabase.lookup(c)
                    w.write("${stamp.format(Date(s.ts))};$mil;$c;${info.title};${info.severity.label};$type\n")
                }
            }
            w.write("\nABASTECIMENTOS\nData/Hora;Litros;Valor (R$);Preço/L (R$);Combustível;Tanque cheio;Consumo real (km/l)\n")
            db.refuels().forEach { r ->
                w.write(listOf(stamp.format(Date(r.ts)), "%.2f".format(r.liters).replace('.', ','),
                    r.total?.let { "%.2f".format(it).replace('.', ',') } ?: "", r.pricePerL?.let { "%.2f".format(it).replace('.', ',') } ?: "",
                    r.fuel, if (r.full) "Sim" else "Não", r.realKmPerL?.let { "%.1f".format(it).replace('.', ',') } ?: ""
                ).joinToString(";") + "\n")
            }
            w.write("\nALERTAS\nData/Hora;Nível;Alerta\n")
            db.events().forEach { e ->
                val lvl = if (e.level == "CRITICAL") "Grave" else "Atenção"
                w.write("${stamp.format(Date(e.ts))};$lvl;${e.title}\n")
            }
        }
        return f
    }

    /** Relatório em PDF com a última leitura de falhas e as últimas viagens. */
    fun reportPdf(vehicle: String): File {
        val f = File(dir(), "relatorio_${fileStamp.format(Date())}.pdf")
        val pdf = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 40f
        var pageNo = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        var canvas = page.canvas
        var y = margin

        val title = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val h2 = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 60, 110); isAntiAlias = true }
        val body = Paint().apply { textSize = 10.5f; color = Color.rgb(30, 30, 30); isAntiAlias = true }
        val muted = Paint(body).apply { color = Color.GRAY; textSize = 9.5f }
        val red = Paint(body).apply { color = Color.rgb(190, 30, 30); typeface = Typeface.DEFAULT_BOLD }
        val orange = Paint(body).apply { color = Color.rgb(200, 110, 0); typeface = Typeface.DEFAULT_BOLD }

        fun newPageIfNeeded(h: Float) {
            if (y + h > pageH - margin) {
                pdf.finishPage(page); pageNo++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                canvas = page.canvas; y = margin
            }
        }
        fun line(text: String, p: Paint, gap: Float = 4f) {
            // quebra de linha simples por largura
            val maxW = pageW - margin * 2
            val words = text.split(' ')
            var cur = ""
            for (w in words) {
                val test = if (cur.isEmpty()) w else "$cur $w"
                if (p.measureText(test) > maxW) {
                    newPageIfNeeded(p.textSize + gap); canvas.drawText(cur, margin, y + p.textSize, p); y += p.textSize + gap; cur = w
                } else cur = test
            }
            if (cur.isNotEmpty()) { newPageIfNeeded(p.textSize + gap); canvas.drawText(cur, margin, y + p.textSize, p); y += p.textSize + gap }
        }

        line("Relatório do veículo", title, 6f)
        line(vehicle, body)
        line("Gerado em ${stamp.format(Date())} pelo app OBD Fácil", muted, 14f)

        line("Códigos de falha (última leitura)", h2, 8f)
        val scan = db.scans().firstOrNull()
        if (scan == null) line("Nenhuma leitura de falhas feita ainda.", body, 12f)
        else {
            line("Lida em ${stamp.format(Date(scan.ts))} · Luz da injeção: " +
                when (scan.milOn) { true -> "ACESA"; false -> "apagada"; null -> "não informado" }, muted, 8f)
            if (scan.codes.isEmpty() && scan.pending.isEmpty()) line("Nenhuma falha registrada.", body, 12f)
            (scan.codes.map { it to "" } + scan.pending.map { it to " (pendente)" }).forEach { (c, suffix) ->
                val info = DtcDatabase.lookup(c)
                val p = when (info.severity.name) { "CRITICAL" -> red; "HIGH" -> orange; else -> body }
                line("$c$suffix · ${info.severity.label.uppercase()} · ${info.title}", p, 2f)
                line(info.explanation, body, 2f)
                info.tip?.let { line("Dica: $it", muted, 2f) }
                y += 8f
            }
        }

        y += 6f
        line("Últimas viagens", h2, 8f)
        val trips = db.trips().filter { it.endTs != null }.take(15)
        if (trips.isEmpty()) line("Nenhuma viagem registrada.", body)
        trips.forEach { t ->
            val temp = t.maxCoolant?.let { "temp. máx $it °C" } ?: "temp. sem leitura"
            val bat = if (t.minVoltage == null) "bateria sem leitura" else "bateria ${num(t.minVoltage)} a ${num(t.maxVoltage)} V"
            line("${stamp.format(Date(t.startTs))} · ${t.durationMin} min · ${"%.1f".format(t.distanceKm).replace('.', ',')} km · " +
                "máx ${t.maxSpeed} km/h · ${t.maxRpm} rpm · $temp · $bat" +
                (t.kmPerL?.let { " · ${"%.1f".format(it).replace('.', ',')} km/l (estimado)" } ?: "") +
                " · ${t.alerts} alerta(s)", body, 5f)
        }

        val events = db.events().take(20)
        if (events.isNotEmpty()) {
            y += 10f
            line("Alertas recentes", h2, 8f)
            events.forEach { e -> line("${stamp.format(Date(e.ts))} · ${e.title}", if (e.level == "CRITICAL") red else orange, 4f) }
        }

        pdf.finishPage(page)
        f.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return f
    }

    fun techLog(text: String): File =
        File(dir(), "registro_tecnico_${fileStamp.format(Date())}.txt").apply { writeText(text) }

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val mime = when (file.extension) { "pdf" -> "application/pdf"; "txt" -> "text/plain"; else -> "text/csv" }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Exportar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
