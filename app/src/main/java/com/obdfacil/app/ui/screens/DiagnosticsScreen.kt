package com.obdfacil.app.ui.screens

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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obdfacil.app.ConnState
import com.obdfacil.app.MainViewModel
import com.obdfacil.app.UiState
import com.obdfacil.app.analysis.DtcInfo
import com.obdfacil.app.analysis.Level
import com.obdfacil.app.analysis.Severity
import com.obdfacil.app.ui.components.AppCard
import com.obdfacil.app.ui.components.Pill
import com.obdfacil.app.ui.components.SectionTitle
import com.obdfacil.app.ui.components.StatusHero
import com.obdfacil.app.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Severity.color() = when (this) { Severity.CRITICAL -> Palette.Bad; Severity.HIGH -> Palette.Warn; Severity.LOW -> Palette.Info }

@Composable
fun DiagnosticsScreen(s: UiState, vm: MainViewModel) {
    var confirmClear by remember { mutableStateOf(false) }
    val connected = s.conn == ConnState.CONNECTED
    val all = s.stored + s.pending
    val fmt = remember { SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            when {
                !connected && s.lastScanTs == null -> StatusHero(Level.INFO, "Diagnóstico", "Conecte ao carro para procurar falhas")
                s.scanning -> StatusHero(Level.INFO, "Procurando falhas…", "Isso leva alguns segundos")
                all.isEmpty() && s.lastScanTs != null -> StatusHero(Level.OK, "Nenhuma falha", "A central do motor não registrou problemas")
                all.any { it.severity == Severity.CRITICAL } -> StatusHero(Level.CRITICAL, "Falha grave encontrada", "Veja abaixo o que significa e o que fazer")
                all.isNotEmpty() -> StatusHero(Level.WARNING, if (all.size == 1) "1 falha encontrada" else "${all.size} falhas encontradas", "Veja abaixo o que significa cada uma")
                else -> StatusHero(Level.INFO, "Diagnóstico", "Toque em Procurar falhas")
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Luz da injeção: ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (s.milOn) {
                    true -> Pill("ACESA", Palette.Warn)
                    false -> Pill("Apagada", Palette.Good)
                    null -> Pill("—", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                s.lastScanTs?.let { Text("Lido em ${fmt.format(Date(it))}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Button(onClick = { vm.scanDtcs() }, enabled = connected && !s.scanning, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                if (s.scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                else Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Procurar falhas", style = MaterialTheme.typography.titleMedium)
            }
            if (!connected) Text("Conecte ao carro na aba Painel para fazer uma nova leitura.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }

        if (s.stored.isNotEmpty()) {
            item { SectionTitle("Falhas confirmadas") }
            s.stored.forEach { d -> item(key = "s" + d.code) { DtcCard(d, pending = false) } }
        }
        if (s.pending.isNotEmpty()) {
            item {
                SectionTitle("Falhas pendentes")
                Text("A central percebeu algo uma vez, mas ainda não confirmou. Fique de olho.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.pending.forEach { d -> item(key = "p" + d.code) { DtcCard(d, pending = true) } }
        }

        if (connected && all.isNotEmpty()) {
            item {
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Apagar códigos e luz da injeção")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Apagar os códigos?") },
        text = { Text("Apagar não conserta o problema: só limpa a memória e apaga a luz da injeção. Se o defeito continuar, o código volta.\n\nFaça isso depois do conserto, ou para confirmar se o problema volta.\n\nO ideal é fazer com o motor desligado e a ignição ligada.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearDtcs() }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun DtcCard(d: DtcInfo, pending: Boolean) {
    val c = d.severity.color()
    AppCard(Modifier.fillMaxWidth(), borderColor = c.copy(alpha = 0.7f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(d.severity.label.uppercase(), c)
                Spacer(Modifier.width(8.dp))
                if (pending) Pill("PENDENTE", MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(d.code, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(d.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(d.explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("O que fazer: " + d.severity.action, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            d.tip?.let { Text("💡 $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
