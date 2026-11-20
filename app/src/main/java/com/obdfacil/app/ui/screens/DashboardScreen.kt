package com.obdfacil.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obdfacil.app.ConnState
import com.obdfacil.app.MainViewModel
import com.obdfacil.app.UiState
import com.obdfacil.app.analysis.HealthAnalyzer
import com.obdfacil.app.analysis.Level
import com.obdfacil.app.ui.components.AlertCard
import com.obdfacil.app.ui.components.AppCard
import com.obdfacil.app.ui.components.ArcGauge
import com.obdfacil.app.ui.components.BigNumber
import com.obdfacil.app.ui.components.DetailRow
import com.obdfacil.app.ui.components.SectionTitle
import com.obdfacil.app.ui.components.StatusHero
import com.obdfacil.app.ui.theme.Palette

@Composable
fun DashboardScreen(s: UiState, vm: MainViewModel, onRequestBluetooth: () -> Unit) {
    when (s.conn) {
        ConnState.DISCONNECTED -> ConnectPanel(s, vm, onRequestBluetooth)
        ConnState.CONNECTING -> Connecting(s)
        ConnState.CONNECTED -> LivePanel(s)
    }
}

@Composable
private fun Connecting(s: UiState) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(80.dp))
        CircularProgressIndicator(Modifier.size(64.dp), strokeWidth = 6.dp)
        Spacer(Modifier.height(24.dp))
        Text(s.connStep, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("Deixe a ignição ligada (ou o motor funcionando).", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectPanel(s: UiState, vm: MainViewModel, onRequestBluetooth: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            StatusHero(Level.INFO, "Vamos conectar", "Leva menos de um minuto")
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Como conectar", style = MaterialTheme.typography.titleLarge)
                    Step(1, "Encaixe o adaptador na tomada OBD do carro (no 206 fica embaixo do volante, atrás de uma tampinha).")
                    Step(2, "Ligue a ignição ou o motor.")
                    Step(3, "Na primeira vez, pareie o adaptador no Bluetooth do celular. A senha costuma ser 1234 ou 0000.")
                    Step(4, "Toque em \"Procurar adaptador\" e escolha o seu.")
                }
            }
        }
        s.error?.let { err -> item { AlertCard(com.obdfacil.app.analysis.Alert("err", Level.WARNING, "Não conectou", err, "")) } }
        item {
            Button(onClick = onRequestBluetooth, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Bluetooth, null); Spacer(Modifier.width(8.dp))
                Text("Procurar adaptador", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (s.devices.isNotEmpty()) {
            item { SectionTitle("Aparelhos pareados — toque para conectar") }
            s.devices.forEach { d ->
                item {
                    AppCard(Modifier.fillMaxWidth().clickable { vm.connect(d.address) },
                        borderColor = if (d.address == s.savedDevice) Palette.Good else null) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bluetooth, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (d.address == s.savedDevice) "Usado da última vez" else d.address,
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { vm.connectDemo() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.PlayCircle, null); Spacer(Modifier.width(8.dp))
                Text("Modo demonstração (sem o carro)")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row {
        Text("$n.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LivePanel(s: UiState) {
    val d = s.live
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val running = d.engineRunning

    val (heroTitle, heroSub) = when (s.overall) {
        Level.CRITICAL -> "Pare o carro" to "Há um problema grave. Veja o aviso abaixo."
        Level.WARNING -> "Atenção" to (if (s.alerts.count { it.level == Level.WARNING } == 1) "1 ponto para ficar de olho"
            else "${s.alerts.count { it.level >= Level.WARNING }} pontos para ficar de olho")
        else -> "Tudo certo" to (if (running) "Nenhum problema detectado" else "Motor desligado · ignição ligada")
    }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)); StatusHero(s.overall, heroTitle, heroSub) }

        s.alerts.forEach { a -> item(key = "a_" + a.key) { AlertCard(a) } }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val t = d.coolantC
                val tLevel = when {
                    t == null -> Level.OK
                    t >= HealthAnalyzer.COOLANT_CRIT -> Level.CRITICAL
                    t >= HealthAnalyzer.COOLANT_WARN -> Level.WARNING
                    else -> Level.OK
                }
                val tHint = when {
                    t == null -> "Sem leitura"
                    t < 60 -> "Frio, aquecendo"
                    t <= 100 -> "Normal"
                    t < HealthAnalyzer.COOLANT_WARN -> "Quente, normal no trânsito"
                    t < HealthAnalyzer.COOLANT_CRIT -> "Esquentando demais"
                    else -> "PERIGO"
                }
                ArcGauge(t?.toFloat(), 40f, 130f, "Temperatura do motor", t?.toString() ?: "--", "°C", tLevel,
                    listOf(70f..HealthAnalyzer.COOLANT_WARN.toFloat() to Palette.Good,
                        HealthAnalyzer.COOLANT_WARN.toFloat()..HealthAnalyzer.COOLANT_CRIT.toFloat() to Palette.Warn,
                        HealthAnalyzer.COOLANT_CRIT.toFloat()..130f to Palette.Bad),
                    tHint, Modifier.weight(1f))

                val v = d.voltage
                val vAlert = s.alerts.firstOrNull { it.key == "volt" }
                val vLevel = vAlert?.level ?: Level.OK
                val vHint = when {
                    v == null -> "Sem leitura"
                    vAlert != null -> if (vAlert.level == Level.CRITICAL) "PROBLEMA" else "Baixa"
                    running -> "Carregando bem"
                    else -> "Boa"
                }
                ArcGauge(v?.toFloat(), 10f, 16f, "Bateria", v?.let { "%.1f".format(it) } ?: "--", "volts", vLevel,
                    if (running) listOf(10f..12f to Palette.Bad, 12f..12.9f to Palette.Warn, 12.9f..15.2f to Palette.Good, 15.2f..16f to Palette.Bad)
                    else listOf(10f..11.6f to Palette.Bad, 11.6f..12.2f to Palette.Warn, 12.2f..13.2f to Palette.Good),
                    vHint, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val rpm = d.rpm
                BigNumber("Rotação", rpm?.toString() ?: "--", "rpm",
                    when { rpm == null -> "Sem leitura"; rpm < 400 -> "Motor desligado"; rpm < 1100 && (d.speedKmh ?: 0) < 3 -> "Marcha lenta"; rpm > 6000 -> "Alta demais"; else -> "Normal" },
                    Modifier.weight(1f), hintColor = if ((rpm ?: 0) > 6000) Palette.Warn else null)
                BigNumber("Velocidade", d.speedKmh?.toString() ?: "--", "km/h",
                    "Viagem: ${"%.1f".format(s.tripKm)} km", Modifier.weight(1f))
            }
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { showDetails = !showDetails }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mais detalhes", style = MaterialTheme.typography.titleLarge)
                            Text("Para quem quer ver tudo", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (showDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    if (showDetails) {
                        HorizontalDivider()
                        DetailRow("Esforço do motor", "Quanto da força do motor está em uso agora", d.loadPct?.let { "$it%" } ?: "--", d.loadPct?.let { Level.OK })
                        DetailRow("Pedal do acelerador", "Quanto a borboleta está aberta", d.throttlePct?.let { "$it%" } ?: "--", d.throttlePct?.let { Level.OK })
                        DetailRow("Temperatura do ar", "Ar que entra no motor. Normal: até 60 °C", d.intakeC?.let { "$it °C" } ?: "--",
                            d.intakeC?.let { if (it > 65) Level.WARNING else Level.OK })
                        val trim = (d.shortTrimPct ?: 0.0) + (d.longTrimPct ?: 0.0)
                        val reliable = HealthAnalyzer.trimReliable(d)
                        DetailRow("Ajuste da mistura",
                            if (reliable || d.longTrimPct == null) "Quanto a central corrige o combustível. Ideal: perto de 0%"
                            else "Motor ainda frio: este valor só vale acima de ${HealthAnalyzer.WARM_ENGINE_C} °C",
                            d.longTrimPct?.let { "%+.0f%%".format(trim) } ?: "--",
                            d.longTrimPct?.let { if (!reliable) null else if (kotlin.math.abs(trim) >= HealthAnalyzer.TRIM_LIMIT) Level.WARNING else Level.OK })
                        DetailRow("Pressão no coletor", "Vácuo do motor. Em marcha lenta: 25 a 40 kPa", d.mapKpa?.let { "$it kPa" } ?: "--", d.mapKpa?.let { Level.OK })
                        DetailRow("Avanço da ignição", "Momento da faísca, ajustado pela central", d.timingDeg?.let { "%.0f°".format(it) } ?: "--", d.timingDeg?.let { Level.OK })
                        DetailRow("Luz da injeção", "Luz de aviso do motor no painel",
                            when (d.milOn) { true -> "Acesa"; false -> "Apagada"; null -> "--" },
                            d.milOn?.let { if (it) Level.WARNING else Level.OK })
                    }
                }
            }
        }

        item {
            Text("${if (s.isDemo) "Modo demonstração · " else ""}${s.adapter} · ${s.protocol} · ${s.supportedCount} sensores disponíveis",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
