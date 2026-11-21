package com.obdfacil.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obdfacil.app.analysis.Alert
import com.obdfacil.app.ui.screens.DashboardScreen
import com.obdfacil.app.ui.screens.DiagnosticsScreen
import com.obdfacil.app.ui.screens.HistoryScreen
import com.obdfacil.app.ui.screens.SettingsScreen
import com.obdfacil.app.ui.theme.ObdTheme
import com.obdfacil.app.ui.theme.Palette

class MainActivity : ComponentActivity() {
    private val vm: CarSession by lazy { (application as ObdApp).session }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by vm.ui.collectAsStateWithLifecycle()
            val dark = when (s.themeMode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }

            // Tela acesa enquanto conectado (opcional: o app também funciona com a tela apagada)
            LaunchedEffect(s.conn, s.keepScreenOn) {
                if (s.conn == ConnState.CONNECTED && s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            ObdTheme(dark) { App(s, vm) }
        }
    }

    override fun onStart() { super.onStart(); vm.appVisible = true }
    override fun onStop() { vm.appVisible = false; super.onStop() }
}

@Composable
private fun App(s: UiState, vm: CarSession) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        // A notificação é opcional; só o Bluetooth é obrigatório
        if (granted[Manifest.permission.BLUETOOTH_CONNECT] != false) vm.loadPairedDevices()
        else vm.showError("Sem a permissão de Bluetooth o app não consegue falar com o adaptador.")
    }
    val requestBluetooth = {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray()) else vm.loadPairedDevices()
    }

    LaunchedEffect(s.toast) { s.toast?.let { snack.showSnackbar(it); vm.consumeToast() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            // Faixa fixa: impossível confundir dados simulados com o carro de verdade
            if (s.isDemo && s.conn != ConnState.DISCONNECTED) {
                Row(Modifier.fillMaxWidth().background(Palette.Info).statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("MODO DEMONSTRAÇÃO · dados simulados", color = Color(0xFF06223F),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.disconnect() }) { Text("Sair", color = Color(0xFF06223F), fontWeight = FontWeight.Bold) }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Rounded.Speed, null) }, label = { Text("Painel") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = {
                    val n = s.stored.size + s.pending.size
                    BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) { Icon(Icons.Rounded.Build, null) }
                }, label = { Text("Diagnóstico") })
                NavigationBarItem(tab == 2, { tab = 2; vm.refreshHistory() }, icon = { Icon(Icons.Rounded.History, null) }, label = { Text("Histórico") })
                NavigationBarItem(tab == 3, { tab = 3 }, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("Ajustes") })
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> DashboardScreen(s, vm, requestBluetooth)
                1 -> DiagnosticsScreen(s, vm)
                2 -> HistoryScreen(s, vm)
                else -> SettingsScreen(s, vm)
            }
        }
    }

    s.popup?.let { CriticalPopup(it, onDismiss = vm::dismissPopup) }
}

/** Aviso em tela cheia para problemas graves. */
@Composable
private fun CriticalPopup(a: Alert, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF7A0E12)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.Error, null, tint = Color.White, modifier = Modifier.size(96.dp))
                Text(a.title, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White, textAlign = TextAlign.Center)
                Text(a.message, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center)
                Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), MaterialTheme.shapes.large).padding(16.dp)) {
                    Text("O que fazer: ${a.action}", style = MaterialTheme.typography.titleMedium, color = Color.White,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Palette.Bad)) {
                    Text("Entendi", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

