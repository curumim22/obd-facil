package com.obdfacil.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.obdfacil.app.data.OilChange
import com.obdfacil.app.data.OilPlan
import com.obdfacil.app.data.OilStatus
import com.obdfacil.app.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
private fun fmtDate(ts: Long) = dateFmt.format(Date(ts))

val OIL_TYPES = listOf("5W30 sintético", "5W40 sintético", "10W40 semissintético", "15W40 mineral", "20W50 mineral")
val OIL_INTERVALS = listOf(5000, 7500, 10000)

/** Cartão com a situação da próxima troca de óleo. */
@Composable
fun OilCard(plan: OilPlan?, history: List<OilChange>, onRegister: () -> Unit, onDelete: (Long) -> Unit,
            modifier: Modifier = Modifier) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val color = when (plan?.status) {
        OilStatus.DUE -> Palette.Bad
        OilStatus.SOON -> Palette.Warn
        OilStatus.OK -> Palette.Good
        else -> null
    }
    AppCard(modifier.fillMaxWidth(), borderColor = if (plan?.status == OilStatus.DUE) Palette.Bad else null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Troca de óleo", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                when (plan?.status) {
                    OilStatus.DUE -> Pill("Trocar já", Palette.Bad)
                    OilStatus.SOON -> Pill("Em breve", Palette.Warn)
                    OilStatus.OK -> Pill("Em dia", Palette.Good)
                    else -> {}
                }
            }
            if (plan == null) {
                Text("Registre a última troca (tipo de óleo e quilometragem) para o app avisar quando chegar a hora da próxima.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                val r = plan.remainingKm
                val big = when {
                    r == null -> "Aos ${brKm(plan.nextKm)} km"
                    r >= 0 -> "Faltam ${brKm(r)} km"
                    else -> "Passou ${brKm(-r)} km"
                }
                Text(big, style = MaterialTheme.typography.headlineMedium, color = color ?: MaterialTheme.colorScheme.onSurface)
                if (r != null) {
                    val used = ((plan.last.intervalKm - r) / plan.last.intervalKm).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { used }, color = color ?: Palette.Info,
                        modifier = Modifier.fillMaxWidth().height(8.dp))
                }
                Text("Próxima: ${brKm(plan.nextKm)} km" +
                        (if (plan.last.months > 0) " ou ${fmtDate(plan.dueTs)}, o que vier primeiro" else ""),
                    style = MaterialTheme.typography.bodyMedium)
                if (plan.status == OilStatus.DUE && plan.reason == "tempo")
                    Text("Venceu pelo tempo: o óleo envelhece mesmo rodando pouco.", style = MaterialTheme.typography.bodyMedium, color = Palette.Bad)
                Text("Última: ${fmtDate(plan.last.ts)} aos ${brKm(plan.last.km)} km · ${plan.last.oilType.ifBlank { "óleo não informado" }}" +
                        if (plan.last.filter) " · com filtro" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r == null) Text("Informe a quilometragem do painel em Ajustes para ver quanto falta.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
            }
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Icon(Icons.Rounded.OilBarrel, null); Spacer(Modifier.width(6.dp))
                Text(if (plan == null) "Registrar última troca" else "Troquei o óleo")
            }
            if (history.isNotEmpty()) {
                Text(if (showHistory) "Ocultar histórico" else "Ver histórico (${history.size})",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Info,
                    modifier = Modifier.clickable { showHistory = !showHistory }.padding(vertical = 4.dp))
                if (showHistory) history.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${fmtDate(c.ts)} · ${brKm(c.km)} km", style = MaterialTheme.typography.bodyMedium)
                            Text("${c.oilType.ifBlank { "óleo não informado" }} · a cada ${brKm(c.intervalKm.toDouble())} km" +
                                    (if (c.filter) " · com filtro" else ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(c.ts) }) { Icon(Icons.Rounded.Delete, "Apagar") }
                    }
                }
            }
            Text("A luz do óleo no painel não é o lembrete de troca: ela indica nível ou pressão baixos. " +
                    "Se acender, pare e confira a vareta.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class OilInput(val km: Double, val oilType: String, val intervalKm: Int, val months: Int, val filter: Boolean, val ts: Long)

@Composable
fun OilDialog(estimatedOdometer: Double?, firstTime: Boolean, lastType: String, lastInterval: Int, lastMonths: Int,
              onDismiss: () -> Unit, onSave: (OilInput) -> Unit) {
    var kmText by remember { mutableStateOf(if (!firstTime) estimatedOdometer?.let { "%.0f".format(it) } ?: "" else "") }
    var type by remember { mutableStateOf(lastType) }
    var intervalText by remember { mutableStateOf(lastInterval.toString()) }
    var monthsText by remember { mutableStateOf(lastMonths.toString()) }
    var filter by remember { mutableStateOf(false) }
    var daysAgoText by remember { mutableStateOf("") }

    val km = kmText.filter { it.isDigit() }.toDoubleOrNull()
    val interval = intervalText.filter { it.isDigit() }.toIntOrNull()
    val months = monthsText.filter { it.isDigit() }.toIntOrNull() ?: 0
    val daysAgo = daysAgoText.filter { it.isDigit() }.toIntOrNull() ?: 0
    val valid = km != null && km > 0 && interval != null && interval in 1000..30000 && months in 0..24

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (firstTime) "Última troca de óleo" else "Troquei o óleo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(kmText, { kmText = it }, singleLine = true,
                    label = { Text("Quilometragem do painel na troca") },
                    placeholder = { estimatedOdometer?.let { Text("~${brKm(it)}") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(type, { type = it }, singleLine = true, label = { Text("Tipo de óleo") },
                    placeholder = { Text("Ex.: 5W30 sintético") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OIL_TYPES.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
                }
                Text("Trocar a cada", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OIL_INTERVALS.forEach { i ->
                        FilterChip(selected = interval == i, onClick = { intervalText = i.toString() },
                            label = { Text("${brKm(i.toDouble())} km") })
                    }
                }
                OutlinedTextField(intervalText, { intervalText = it }, singleLine = true, label = { Text("Intervalo (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(monthsText, { monthsText = it }, singleLine = true, label = { Text("Ou a cada (meses)") },
                    supportingText = { Text("O que vier primeiro. 0 = só por km.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(filter, { filter = it })
                    Text("Troquei também o filtro de óleo", style = MaterialTheme.typography.bodyLarge)
                }
                if (firstTime) OutlinedTextField(daysAgoText, { daysAgoText = it }, singleLine = true,
                    label = { Text("Há quantos dias foi? (0 = hoje)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Na dúvida, siga o manual ou a etiqueta da oficina. Com etanol, muito trânsito ou trajetos curtos, " +
                        "prefira o intervalo menor.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(OilInput(km!!, type, interval!!, months, filter,
                    System.currentTimeMillis() - daysAgo * 86_400_000L))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
