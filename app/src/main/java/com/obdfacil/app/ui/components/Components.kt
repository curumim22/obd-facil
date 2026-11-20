package com.obdfacil.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.obdfacil.app.analysis.Alert
import com.obdfacil.app.analysis.Level
import com.obdfacil.app.ui.theme.Palette
import com.obdfacil.app.ui.theme.color

fun Level.icon(): ImageVector = when (this) {
    Level.OK -> Icons.Rounded.CheckCircle
    Level.INFO -> Icons.Rounded.Info
    Level.WARNING -> Icons.Rounded.Warning
    Level.CRITICAL -> Icons.Rounded.Error
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun AppCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.then(if (borderColor != null) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(22.dp)) else Modifier),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) { content() }
}

/** Cartão grande do topo: o "semáforo" do carro. */
@Composable
fun StatusHero(level: Level, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c by animateColorAsState(level.color(), tween(500), label = "hero")
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(c.copy(alpha = 0.16f))
            .border(1.5.dp, c.copy(alpha = 0.6f), RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                Icon(level.icon(), null, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AlertCard(a: Alert, modifier: Modifier = Modifier) {
    val c = a.level.color()
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(c.copy(alpha = if (a.level == Level.CRITICAL) 0.22f else 0.12f))
            .border(if (a.level == Level.CRITICAL) 2.dp else 1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row {
            Icon(a.level.icon(), null, tint = c, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(a.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(a.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (a.action.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("O que fazer: " + a.action, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * Mostrador em arco. As faixas coloridas mostram o que é normal (verde),
 * atenção (amarelo) e perigo (vermelho).
 */
@Composable
fun ArcGauge(
    value: Float?,
    min: Float,
    max: Float,
    label: String,
    valueText: String,
    unit: String,
    level: Level,
    bands: List<Pair<ClosedFloatingPointRange<Float>, Color>>,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val frac by animateFloatAsState(((value ?: min) - min) / (max - min), tween(600), label = "g")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val col = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else level.color()
    AppCard(modifier, borderColor = if (level == Level.CRITICAL) Palette.Bad else null) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f).padding(8.dp)) {
                    val stroke = size.width * 0.075f
                    val d = minOf(size.width, size.height * 1.6f) - stroke
                    val topLeft = Offset((size.width - d) / 2, stroke / 2 + 4f)
                    val arcSize = Size(d, d)
                    val start = 160f; val sweep = 220f
                    drawArc(track, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    bands.forEach { (r, c) ->
                        val s = ((r.start - min) / (max - min)).coerceIn(0f, 1f)
                        val e = ((r.endInclusive - min) / (max - min)).coerceIn(0f, 1f)
                        drawArc(c.copy(alpha = 0.35f), start + sweep * s, sweep * (e - s), false,
                            Offset(topLeft.x + stroke * 0.8f, topLeft.y + stroke * 0.8f),
                            Size(d - stroke * 1.6f, d - stroke * 1.6f), style = Stroke(stroke * 0.25f))
                    }
                    if (value != null) drawArc(col, start, sweep * frac.coerceIn(0f, 1f), false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(valueText, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = col, textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Número grande com explicação curta embaixo. */
@Composable
fun BigNumber(label: String, value: String, unit: String, hint: String, modifier: Modifier = Modifier, hintColor: Color? = null) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text(unit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = hintColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Linha de detalhe: nome, explicação para leigo, valor e uma bolinha de status. */
@Composable
fun DetailRow(name: String, explain: String, value: String, level: Level?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(explain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(10.dp).clip(CircleShape).background(level?.color() ?: MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Gráfico de linha simples. */
@Composable
fun LineChart(values: List<Float>, color: Color, modifier: Modifier = Modifier, minY: Float? = null, maxY: Float? = null) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = minY ?: values.min(); val hi = (maxY ?: values.max()).let { if (it - lo < 1f) lo + 1f else it }
        for (i in 0..3) { val y = size.height * i / 3f; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f) }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1 - ((v - lo) / (hi - lo)).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round))
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun Spaced(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}
