package com.obdfacil.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdfacil.app.CarSession
import com.obdfacil.app.UiState
import com.obdfacil.app.analysis.DtcDatabase
import com.obdfacil.app.analysis.HealthAnalyzer
import com.obdfacil.app.data.EventRow
import com.obdfacil.app.data.Trip
import com.obdfacil.app.obd.LiveData
import com.obdfacil.app.ui.components.AppCard
import com.obdfacil.app.ui.components.LineChart
import com.obdfacil.app.ui.components.Pill
import com.obdfacil.app.ui.components.SectionTitle
import com.obdfacil.app.ui.components.br
import com.obdfacil.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("EEE, dd/MM 'às' HH:mm", Locale("pt", "BR"))

@Composable
fun HistoryScreen(s: UiState, vm: CarSession) {
    var openTrip by remember { mutableStateOf<Trip?>(null) }
    openTrip?.let { t -> TripDetail(t, vm, onBack = { openTrip = null }); return }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Histórico", style = MaterialTheme.typography.headlineSmall)
            Text("Tudo fica salvo no celular, mesmo sem internet.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.exportPdf() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Relatório PDF")
                }
                OutlinedButton(onClick = { vm.exportCsv() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Rounded.TableChart, null); Spacer(Modifier.width(6.dp)); Text("Planilha")
                }
            }
            Text("O PDF é ótimo para mostrar ao mecânico. A planilha abre no Excel ou Google Planilhas.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }

        item { SectionTitle("Viagens") }
        if (s.trips.isEmpty()) item {
            Text("Nenhuma viagem ainda. Cada vez que você conecta ao carro, uma viagem é gravada.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.trips.forEach { t -> item(key = "t${t.id}") { TripCard(t) { openTrip = t } } }

        if (s.fuel.refuels.isNotEmpty()) {
            item { SectionTitle("Abastecimentos") }
            s.fuel.refuels.take(30).forEach { r ->
                item(key = "rf${r.id}") {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(dayFmt.format(Date(r.ts)).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (r.full) Pill("Tanque cheio", Palette.Info)
                                IconButton(onClick = { vm.deleteRefuel(r.id) }) { Icon(Icons.Rounded.Delete, "Apagar") }
                            }
                            Text("${br(r.liters)} L de ${r.fuel.lowercase()}" + (r.total?.let { " · R$ ${br(it, 2)}" } ?: "") +
                                (r.pricePerL?.let { " (R$ ${br(it, 2)}/L)" } ?: ""),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            r.odometer?.let { Text("Quilometragem: ${com.obdfacil.app.ui.components.brKm(it)} km",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            r.realKmPerL?.let { Text("Consumo real do tanque: ${br(it)} km/l",
                                style = MaterialTheme.typography.bodyLarge, color = Palette.Good) }
                        }
                    }
                }
            }
        }

        if (s.scans.isNotEmpty()) {
            item { SectionTitle("Leituras de falhas") }
            s.scans.take(30).forEach { sc ->
                item(key = "sc${sc.id}") {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(dayFmt.format(Date(sc.ts)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                val n = sc.codes.size + sc.pending.size
                                Pill(if (n == 0) "Sem falhas" else "$n falha(s)", if (n == 0) Palette.Good else Palette.Warn)
                            }
                            (sc.codes + sc.pending).forEach { c ->
                                Text("• $c · ${DtcDatabase.lookup(c).title}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TripCard(t: Trip, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayFmt.format(Date(t.startTs)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (t.alerts > 0) Pill("${t.alerts} alerta(s)", Palette.Warn) else Pill("OK", Palette.Good)
            }
            Spacer(Modifier.height(6.dp))
            Text("${t.durationMin} min · ${"%.1f".format(t.distanceKm)} km · máx ${t.maxSpeed} km/h",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            t.kmPerL?.let { Text("Consumo estimado: ${br(it)} km/l · ${br(t.fuelL ?: 0.0)} L",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
            Text("Temp. máx ${t.maxCoolant ?: "--"} °C · Bateria ${t.minVoltage ?: "--"} a ${t.maxVoltage ?: "--"} V",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TripDetail(t: Trip, vm: CarSession, onBack: () -> Unit) {
    var samples by remember { mutableStateOf<List<LiveData>>(emptyList()) }
    var events by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(t.id) {
        withContext(Dispatchers.IO) { samples = vm.samples(t.id); events = vm.tripEvents(t.id) }
    }
    androidx.activity.compose.BackHandler(onBack = onBack)

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
                Column(Modifier.weight(1f)) {
                    Text("Viagem", style = MaterialTheme.typography.headlineSmall)
                    Text(dayFmt.format(Date(t.startTs)), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Apagar") }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("Duração", "${t.durationMin} min")
                    Stat("Distância", "${"%.1f".format(t.distanceKm)} km")
                    Stat("Velocidade média / máxima", "${"%.0f".format(t.avgSpeed)} / ${t.maxSpeed} km/h")
                    Stat("Rotação máxima", "${t.maxRpm} rpm")
                    t.kmPerL?.let { Stat("Consumo médio (estimado)", "${br(it)} km/l") }
                    t.fuelL?.let { Stat("Combustível gasto (estimado)", "${br(it)} L") }
                    Stat("Temperatura máxima", "${t.maxCoolant ?: "--"} °C")
                    Stat("Bateria (mín – máx)", "${t.minVoltage ?: "--"} – ${t.maxVoltage ?: "--"} V")
                }
            }
        }
        val coolant = samples.mapNotNull { it.coolantC?.toFloat() }
        if (coolant.size > 2) item {
            ChartCard("Temperatura do motor (°C)", "Máx: ${coolant.max().toInt()} °C · limite de atenção ${HealthAnalyzer.COOLANT_WARN} °C") {
                LineChart(coolant, if (coolant.max() >= HealthAnalyzer.COOLANT_WARN) Palette.Warn else Palette.Good,
                    Modifier.fillMaxWidth().height(120.dp), minY = 30f, maxY = 120f)
            }
        }
        val speed = samples.mapNotNull { it.speedKmh?.toFloat() }
        if (speed.size > 2) item {
            ChartCard("Velocidade (km/h)", "Máx: ${speed.max().toInt()} km/h") {
                LineChart(speed, Palette.Info, Modifier.fillMaxWidth().height(120.dp), minY = 0f)
            }
        }
        val lh = samples.mapNotNull { it.fuelLh?.toFloat() }
        if (lh.size > 2) item {
            ChartCard("Consumo instantâneo (L/h)", "Picos = aceleradas; zero = descida com o pé fora do acelerador") {
                LineChart(lh, Palette.Warn, Modifier.fillMaxWidth().height(120.dp), minY = 0f)
            }
        }
        val volt = samples.mapNotNull { it.voltage?.toFloat() }
        if (volt.size > 2) item {
            ChartCard("Bateria (V)", "Com motor ligado o normal é 13,5 a 14,5 V") {
                LineChart(volt, Palette.Good, Modifier.fillMaxWidth().height(120.dp), minY = 11f, maxY = 15.5f)
            }
        }
        if (events.isNotEmpty()) {
            item { SectionTitle("Alertas nesta viagem") }
            events.forEach { e ->
                item(key = "e${e.id}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(if (e.level == "CRITICAL") "GRAVE" else "ATENÇÃO", if (e.level == "CRITICAL") Palette.Bad else Palette.Warn)
                        Spacer(Modifier.width(8.dp))
                        Text(e.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { vm.exportTrip(t) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Description, null); Spacer(Modifier.width(8.dp)); Text("Exportar leituras desta viagem")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Apagar esta viagem?") },
        text = { Text("As leituras e alertas desta viagem serão apagados do celular. Não dá para desfazer.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteTrip(t.id); onBack() }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ChartCard(title: String, sub: String, chart: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            chart()
        }
    }
}
