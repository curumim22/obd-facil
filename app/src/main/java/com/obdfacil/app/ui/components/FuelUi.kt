package com.obdfacil.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.obdfacil.app.FuelUi
import com.obdfacil.app.ui.theme.Palette

fun br(v: Double, decimals: Int = 1) = "%.${decimals}f".format(v).replace('.', ',')
private fun parseBr(s: String) = s.trim().replace("R$", "").replace(" ", "").replace(',', '.').toDoubleOrNull()

/** Níveis do ponteiro que um leigo consegue ler no painel. */
val LEVELS = listOf("Reserva" to 0.08, "1/4" to 0.25, "1/2" to 0.5, "3/4" to 0.75, "Cheio" to 1.0)

/** Cartão de combustível do Painel: consumo agora, média da viagem, tanque e autonomia. */
@Composable
fun FuelCard(f: FuelUi, connected: Boolean, onRefuel: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Combustível", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Pill(f.fuelType.label, Palette.Info)
            }
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Agora", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val (value, unit) = when {
                            f.fuelCut -> "0" to "sem gastar"
                            f.instantKmL != null -> br(f.instantKmL) to "km/l"
                            f.instantLh != null -> br(f.instantLh) to "L/h parado"
                            else -> "--" to ""
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(value, style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.width(6.dp))
                            Text(unit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Média da viagem", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(f.tripKmL?.let { br(it) } ?: "--", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.width(6.dp))
                            Text("km/l", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Text("${br(f.tripFuelL)} L gastos", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (f.fuelCut) Text("Pé fora do acelerador: a injeção cortou o combustível.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Good)
            }

            // Tanque
            val frac = f.tankFraction
            if (frac == null) {
                Text("Toque em \"Abasteci\" para o app começar a estimar quanto tem no tanque.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val barColor = when { frac < 0.12 -> Palette.Bad; frac < 0.25 -> Palette.Warn; else -> Palette.Good }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tanque: ~${br(f.tankL ?: 0.0, 0)} L", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(f.rangeKm?.let { "Dá para ~${br(it, 0)} km" } ?: "Autonomia: rode um pouco mais",
                        style = MaterialTheme.typography.titleMedium, color = barColor)
                }
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(frac.toFloat()).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(barColor))
                }
            }
            Button(onClick = onRefuel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Rounded.LocalGasStation, null); Spacer(Modifier.width(8.dp)); Text("Abasteci")
            }
            Text("Estimativa calculada pelo app (o 206 não informa o nível do tanque). Só conta o que foi rodado com o app conectado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Diálogo "Abasteci": valor pago + preço do litro, ou litros direto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelDialog(
    tankKnown: Boolean,
    capacityL: Double,
    onDismiss: () -> Unit,
    onSave: (liters: Double, pricePerL: Double?, total: Double?, full: Boolean, levelBefore: Double?) -> Unit,
) {
    var byMoney by remember { mutableStateOf(true) }
    var total by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var litersText by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf<Double?>(null) }

    val t = parseBr(total); val p = parseBr(price); val lDirect = parseBr(litersText)
    val liters = if (byMoney) (if (t != null && p != null && p > 0) t / p else null) else lDirect
    val needsLevel = !tankKnown && !full
    val valid = liters != null && liters > 0 && liters <= capacityL * 1.1 && (!needsLevel || level != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abasteci") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(byMoney, { byMoney = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Valor pago") }
                    SegmentedButton(!byMoney, { byMoney = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Litros") }
                }
                if (byMoney) {
                    OutlinedTextField(total, { total = it }, label = { Text("Valor pago (R$)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(price, { price = it }, label = { Text("Preço do litro (R$)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    liters?.let { Text("= ${br(it)} litros", style = MaterialTheme.typography.titleMedium) }
                } else {
                    OutlinedTextField(litersText, { litersText = it }, label = { Text("Litros") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(full, { full = it })
                    Text("Completei o tanque", style = MaterialTheme.typography.bodyLarge)
                }
                if (full) Text("Ótimo: de tanque cheio a tanque cheio o app mede o seu consumo real e fica mais preciso.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (needsLevel) {
                    Text("Antes de abastecer, onde estava o ponteiro?", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LEVELS.dropLast(1).forEach { (label, v) ->
                            FilterChip(selected = level == v, onClick = { level = v }, label = { Text(label) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(liters!!, if (byMoney) p else null, if (byMoney) t else null, full, if (needsLevel) level else null)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
