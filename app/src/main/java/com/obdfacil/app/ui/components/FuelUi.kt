package com.obdfacil.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.obdfacil.app.FuelUi
import com.obdfacil.app.analysis.FuelCompare
import com.obdfacil.app.analysis.FuelType
import com.obdfacil.app.data.Refuel
import com.obdfacil.app.ui.theme.Palette

fun br(v: Double, decimals: Int = 1) = "%.${decimals}f".format(v).replace('.', ',')
fun brKm(v: Double) = "%,.0f".format(java.util.Locale.US, v).replace(',', '.')
private fun parseBr(s: String) = s.trim().replace("R$", "").replace(" ", "").replace(',', '.').toDoubleOrNull()

/** Níveis do ponteiro que um leigo consegue ler no painel. */
val LEVELS = listOf("Reserva" to 0.08, "1/4" to 0.25, "1/2" to 0.5, "3/4" to 0.75, "Cheio" to 1.0)

class FuelActions(val onRefuel: () -> Unit, val onCompare: () -> Unit)

/** Cartão de combustível do Painel: consumo agora, média da viagem, tanque e autonomia. */
@Composable
fun FuelCard(f: FuelUi, connected: Boolean, odometer: Double?, actions: FuelActions, modifier: Modifier = Modifier) {
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
                Text("Registre um abastecimento para o app estimar quanto tem no tanque.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val barColor = when { frac < 0.12 -> Palette.Bad; frac < 0.25 -> Palette.Warn; else -> Palette.Good }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tanque: ~${br(f.tankL ?: 0.0, 0)} L", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(f.rangeKm?.let { "Dá para ~${br(it, 0)} km" } ?: "Autonomia: rode mais um pouco",
                        style = MaterialTheme.typography.titleMedium, color = barColor)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(frac.toFloat()).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(barColor))
                }
            }
            odometer?.let {
                Text("Quilometragem: ~${brKm(it)} km", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onRefuel, modifier = Modifier.weight(1f).height(44.dp)) {
                    Icon(Icons.Rounded.LocalGasStation, null); Spacer(Modifier.width(6.dp)); Text("Abasteci")
                }
                OutlinedButton(onClick = actions.onCompare, modifier = Modifier.weight(1f).height(44.dp)) {
                    Icon(Icons.Rounded.CompareArrows, null); Spacer(Modifier.width(6.dp)); Text("Qual compensa?")
                }
            }
            Text("Estimativa do app: conta só o que foi rodado com o app conectado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class RefuelInput(
    val liters: Double, val pricePerL: Double?, val total: Double?, val full: Boolean,
    val levelBefore: Double?, val odometer: Double?, val fuel: FuelType,
)

/** Diálogo "Abasteci": combustível, valor pago + preço do litro (ou litros), tanque cheio, quilometragem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelDialog(
    tankKnown: Boolean,
    capacityL: Double,
    currentFuel: FuelType,
    estimatedOdometer: Double?,
    onDismiss: () -> Unit,
    onSave: (RefuelInput) -> Unit,
) {
    var fuel by remember { mutableStateOf(currentFuel) }
    var byMoney by remember { mutableStateOf(true) }
    var total by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var litersText by remember { mutableStateOf("") }
    var odoText by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf<Double?>(null) }

    val t = parseBr(total); val p = parseBr(price); val lDirect = parseBr(litersText)
    val liters = if (byMoney) (if (t != null && p != null && p > 0) t / p else null) else lDirect
    val odo = odoText.filter { it.isDigit() }.toDoubleOrNull()
    val needsLevel = !tankKnown && !full
    val valid = liters != null && liters > 0 && liters <= capacityL * 1.1 && (!needsLevel || level != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abasteci") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FuelType.entries.forEachIndexed { i, f ->
                        SegmentedButton(fuel == f, { fuel = f }, SegmentedButtonDefaults.itemShape(i, FuelType.entries.size)) { Text(f.label) }
                    }
                }
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
                OutlinedTextField(odoText, { odoText = it }, singleLine = true,
                    label = { Text("Quilometragem do painel (opcional)") },
                    placeholder = { estimatedOdometer?.let { Text("~${brKm(it)}") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Com a quilometragem, o app mede o consumo real mesmo nas viagens em que não estava conectado.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(full, { full = it })
                    Text("Completei o tanque", style = MaterialTheme.typography.bodyLarge)
                }
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
                onSave(RefuelInput(liters!!, if (byMoney) p else null, if (byMoney) t else null, full,
                    if (needsLevel) level else null, odo, fuel))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Etanol ou gasolina? Compara o custo por km com o consumo do próprio carro quando possível. */
@Composable
fun FuelCompareDialog(
    lastEthanol: Double?,
    lastGasoline: Double?,
    refuels: List<Refuel>,
    estimatedKmL: Double?,
    currentFuel: FuelType,
    onDismiss: () -> Unit,
) {
    var pe by remember { mutableStateOf(lastEthanol?.let { br(it, 2) } ?: "") }
    var pg by remember { mutableStateOf(lastGasoline?.let { br(it, 2) } ?: "") }
    // Consumo real medido (tanque cheio a tanque cheio) de cada combustível
    fun realAvg(f: FuelType) = refuels.filter { it.fuel == f.label }.mapNotNull { it.realKmPerL }.takeIf { it.isNotEmpty() }?.average()
    val kE = realAvg(FuelType.ETANOL) ?: if (currentFuel == FuelType.ETANOL) estimatedKmL else null
    val kG = realAvg(FuelType.GASOLINA) ?: if (currentFuel == FuelType.GASOLINA) estimatedKmL else null
    val e = parseBr(pe); val g = parseBr(pg)
    val r = if (e != null && g != null && e > 0 && g > 0) FuelCompare.compare(e, g, kE, kG) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etanol ou gasolina?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(pe, { pe = it }, label = { Text("Preço do etanol (R$/L)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pg, { pg = it }, label = { Text("Preço da gasolina (R$/L)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (r == null) {
                    Text("Digite os dois preços do posto.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val winColor = if (r.ethanolWins) Palette.Good else Palette.Warn
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(winColor.copy(alpha = 0.15f)).padding(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (r.ethanolWins) "Compensa o ETANOL" else "Compensa a GASOLINA",
                                style = MaterialTheme.typography.titleLarge, color = winColor, fontWeight = FontWeight.Bold)
                            Text("O etanol vale a pena até R$ ${br(r.breakEvenEthanolPrice, 2)} com esse preço de gasolina.",
                                style = MaterialTheme.typography.bodyMedium)
                            if (r.costPerKmEthanol != null && r.costPerKmGasoline != null) {
                                Text("Custo por km: etanol R$ ${br(r.costPerKmEthanol, 2)} · gasolina R$ ${br(r.costPerKmGasoline, 2)}",
                                    style = MaterialTheme.typography.bodyMedium)
                                r.savingPer1000Km?.let {
                                    Text("Diferença: ~R$ ${br(it, 0)} a cada 1.000 km", style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Text(
                        if (r.ratioFromOwnData) "Conta feita com o consumo real do seu carro nos dois combustíveis (o etanol rende ${br(r.ratioUsed * 100, 0)}% da gasolina)."
                        else "Conta feita com a regra dos 70% (o etanol rende cerca de 70% da gasolina). Depois de completar o tanque com cada combustível, o app passa a usar o consumo real do seu carro.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
