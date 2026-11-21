package com.obdfacil.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.obdfacil.app.analysis.Alert
import com.obdfacil.app.analysis.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Mantém a leitura do carro funcionando com a tela apagada ou com outro app aberto.
 * Mostra uma notificação fixa com o resumo (temperatura, bateria, consumo).
 */
class ObdService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = (application as ObdApp).session
        if (intent?.action == ACTION_STOP) { session.disconnect(); stopSelf(); return START_NOT_STICKY }
        val first = build(session.ui.value)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ONGOING_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else startForeground(ONGOING_ID, first)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "obdfacil:leitura")
                .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        }
        scope.launch {
            session.ui.map { Summary.of(it) }.distinctUntilChanged().collectLatest { sum ->
                getSystemService(NotificationManager::class.java).notify(ONGOING_ID, build(session.ui.value, sum))
                delay(2000) // no máximo uma atualização a cada 2 s
            }
        }
        return START_NOT_STICKY
    }

    private data class Summary(val title: String, val text: String) {
        companion object {
            fun of(s: UiState): Summary {
                val d = s.live
                val title = when (s.overall) {
                    Level.CRITICAL -> "⚠ " + (s.alerts.firstOrNull()?.title ?: "Problema grave")
                    Level.WARNING -> "Atenção: " + (s.alerts.firstOrNull { it.level >= Level.WARNING }?.title ?: "")
                    else -> if (s.conn == ConnState.CONNECTED) "Conectado ao carro · tudo certo" else "Conectando…"
                }
                val parts = listOfNotNull(
                    d.coolantC?.let { "Motor $it °C" },
                    d.voltage?.let { "Bateria ${"%.1f".format(it).replace('.', ',')} V" },
                    s.fuel.instantKmL?.let { "${"%.1f".format(it).replace('.', ',')} km/l" },
                    "Viagem ${"%.1f".format(s.tripKm).replace('.', ',')} km",
                )
                return Summary(title, parts.joinToString(" · "))
            }
        }
    }

    private fun build(s: UiState, sum: Summary = Summary.of(s)): Notification =
        NotificationCompat.Builder(this, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(sum.title)
            .setContentText(sum.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(this))
            .addAction(0, "Desconectar", PendingIntent.getService(this, 1,
                Intent(this, ObdService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .build()

    override fun onDestroy() {
        scope.cancel()
        runCatching { wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        const val CH_ONGOING = "conexao"
        const val CH_ALERTS = "alertas"
        const val ONGOING_ID = 1
        const val ACTION_STOP = "parar"

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Conexão com o carro",
                NotificationManager.IMPORTANCE_LOW).apply { description = "Resumo enquanto o app lê o carro" })
            nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Alertas graves",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Superaquecimento, bateria sem carga e falhas graves"
                enableVibration(true); vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
            })
        }

        fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, ObdService::class.java)) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, ObdService::class.java)) }
        }

        /** Alerta grave em destaque quando o app não está na tela. */
        fun notifyCritical(ctx: Context, a: Alert) {
            createChannels(ctx)
            val n = NotificationCompat.Builder(ctx, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_car)
                .setContentTitle(a.title)
                .setContentText(a.action)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.message + "\n\nO que fazer: " + a.action))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(0xFFFF5A5F.toInt())
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx))
                .build()
            runCatching { ctx.getSystemService(NotificationManager::class.java).notify(a.key.hashCode(), n) }
        }
    }
}
