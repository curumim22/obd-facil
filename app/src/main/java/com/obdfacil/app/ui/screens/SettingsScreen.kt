package com.obdfacil.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.input.KeyboardType
import com.obdfacil.app.analysis.FuelType
import com.obdfacil.app.ui.components.LEVELS
import com.obdfacil.app.ui.components.br
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdfacil.app.ConnState
import com.obdfacil.app.MainViewModel
import com.obdfacil.app.UiState
import com.obdfacil.app.ui.components.AppCard
import com.obdfacil.app.ui.components.SectionTitle
import com.obdfacil.app.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(s: UiState, vm: MainViewModel) {
    var vehicle by remember(s.vehicle) { mutableStateOf(s.vehicle) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)); Text("Ajustes", style = MaterialTheme.typography.headlineSmall) }

        item {
            SectionTitle("Veículo")
            OutlinedTextField(vehicle, { vehicle = it; vm.setVehicle(it) }, Modifier.fillMaxWidth(),
                label = { Text("Nome do carro (aparece no relatório)") }, singleLine = true)
        }

        item {
            SectionTitle("Aparência")
            val options = listOf("Automático", "Escuro", "Claro")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, label ->
                    SegmentedButton(selected = s.themeMode == i, onClick = { vm.setTheme(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
                }
            }
        }

        item {
            SectionTitle("Combustível")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("O que você mais abastece", style = MaterialTheme.typography.titleMedium)
                    val types = FuelType.entries
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        types.forEachIndexed { i, f ->
                            SegmentedButton(selected = s.fuel.fuelType == f, onClick = { vm.setFuelType(f) },
                                shape = SegmentedButtonDefaults.itemShape(i, types.size)) { Text(f.label) }
                        }
                    }

                    var cap by remember(s.fuel.capacityL) { mutableStateOf(br(s.fuel.capacityL, 0)) }
                    OutlinedTextField(cap, { v -> cap = v; v.replace(',', '.').toDoubleOrNull()?.let { if (it >= 20) vm.setTankCapacity(it) } },
                        label = { Text("Capacidade do tanque (litros)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                    Text("Corrigir nível do tanque agora", style = MaterialTheme.typography.titleMedium)
                    Text("Use se o ponteiro do painel não bater com o app (por exemplo, se rodou sem o app conectado).",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LEVELS.forEach { (label, v) ->
                            FilterChip(selected = false, onClick = { vm.setTankLevel(v) }, label = { Text(label) })
                        }
                    }

                    Text("Calibrar com o seu consumo real", style = MaterialTheme.typography.titleMedium)
                    Text("Se você sabe quanto o carro faz (ex.: R$ 100 de etanol rendem 250 km = 10 km/l), informe aqui para o cálculo ficar mais preciso. Também calibra sozinho a cada \"Completei o tanque\".",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var real by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(real, { real = it }, label = { Text("km/l real") }, singleLine = true,
                            modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { real.replace(',', '.').toDoubleOrNull()?.let { if (it in 2.0..40.0) vm.calibrateWithKnownKmL(it) } }) {
                            Text("Calibrar")
                        }
                    }
                    s.fuel.refKmL?.let {
                        Text("Média estimada das últimas viagens: ${br(it)} km/l", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (s.conn == ConnState.CONNECTED) item {
            SectionTitle("Conexão")
            OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Desconectar do carro") }
        }

        if (s.isDemo && s.conn == ConnState.CONNECTED) item {
            SectionTitle("Modo demonstração")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle("Simular superaquecimento", "Para ver como é o alerta grave", s.demoOverheat) { vm.setDemoOverheat(it) }
                    Toggle("Simular bateria fraca", "Para ver o alerta de bateria", s.demoLowBattery) { vm.setDemoLowBattery(it) }
                }
            }
        }

        item {
            SectionTitle("Ajuda")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Help("O app não encontra o adaptador", "Pareie primeiro em Configurações > Bluetooth do celular. A senha costuma ser 1234 ou 0000.")
                    Help("Conecta, mas o carro não responde", "Ligue a ignição. Carros de 2007 a 2009 às vezes demoram para responder na primeira vez: tente de novo.")
                    Help("Alguns valores aparecem como --", "O seu carro não informa esse dado. É normal em carros mais antigos.")
                    Help("Bateria", "O app mede a tensão direto na tomada OBD, que vem da bateria.")
                    Help("Mantenha a tela ligada", "Enquanto conectado, a tela fica acesa para gravar a viagem. Use um suporte de celular.")
                }
            }
        }

        item {
            SectionTitle("Suporte")
            OutlinedButton(onClick = { vm.exportTechLog() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Exportar registro técnico")
            }
            Text("Arquivo com a conversa entre o app e o carro na última conexão. Envie para quem está ajudando a corrigir o app, se algum dado não aparecer.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }

        item {
            SectionTitle("Dados")
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Apagar todo o histórico", color = Palette.Bad)
            }
        }
        item {
            Text("OBD Fácil ${com.obdfacil.app.BuildConfig.VERSION_NAME} · Este app ajuda a entender o carro, mas não substitui um mecânico.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp))
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Apagar todo o histórico?") },
        text = { Text("Todas as viagens, alertas e leituras de falhas serão apagados. Se quiser guardar, exporte antes na aba Histórico. Não dá para desfazer.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Apagar tudo") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun Toggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Help(q: String, a: String) {
    Column {
        Text(q, style = MaterialTheme.typography.titleMedium)
        Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
