package com.obdfacil.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
fun StatusHero(level: Level, title: String, subtitle: String, modifier: Modifier = Modifier, extra: String? = null) {
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
                extra?.let { Spacer(Modifier.height(6.dp)); Pill(it, Palette.Warn) }
            }
        }
    }
}

/**
 * Aviso compacto: título e uma linha. Toque para ver a explicação completa.
 * Avisos graves já aparecem abertos.
 */
@Composable
fun AlertCard(a: Alert, modifier: Modifier = Modifier) {
    val c = a.level.color()
    var expanded by rememberSaveable(a.key) { mutableStateOf(a.level == Level.CRITICAL) }
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(c.copy(alpha = if (a.level == Level.CRITICAL) 0.22f else 0.12f))
            .border(if (a.level == Level.CRITICAL) 2.dp else 1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(a.level.icon(), null, tint = c, modifier = Modifier.size(24.dp).padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (!expanded) {
                    Text(a.message.lineSequence().first(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(a.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (a.action.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("O que fazer: " + a.action, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Fechar" else "Ver mais",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Aviso informativo discreto (ex.: motor ainda frio): uma linha só. */
@Composable
fun InfoLine(a: Alert, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Info, null, tint = Palette.Info, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("${a.title} · ${a.action}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
            Spacer(Modifier.height(6.dp))
            // Arco de 240°: o número fica no centro do círculo, sem encostar no arco
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                val w = maxWidth
                val h = maxWidth / 1.3f
                val strokeDp = w * 0.085f
                val r = minOf((w - strokeDp) / 2, (h - strokeDp) / 1.5f)
                val cy = strokeDp / 2 + r
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = strokeDp.toPx()
                    val radius = r.toPx()
                    val topLeft = Offset(size.width / 2 - radius, stroke / 2)
                    val arcSize = Size(radius * 2, radius * 2)
                    val start = 150f; val sweep = 240f
                    val style = Stroke(stroke, cap = StrokeCap.Butt)
                    drawArc(track, start, sweep, false, topLeft, arcSize, style = style)
                    // Faixas: verde = normal, amarelo = atenção, vermelho = perigo
                    bands.forEach { (rg, c) ->
                        val s0 = ((rg.start - min) / (max - min)).coerceIn(0f, 1f)
                        val e0 = ((rg.endInclusive - min) / (max - min)).coerceIn(0f, 1f)
                        drawArc(c.copy(alpha = 0.30f), start + sweep * s0, sweep * (e0 - s0), false, topLeft, arcSize, style = style)
                    }
                    if (value != null) {
                        drawArc(col, start, (sweep * frac.coerceIn(0f, 1f)).coerceAtLeast(2f), false, topLeft, arcSize,
                            style = Stroke(stroke * 0.55f, cap = StrokeCap.Round))
                    }
                }
                Column(Modifier.align(Alignment.TopCenter).offset(y = cy - 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(valueText, style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp, lineHeight = 38.sp),
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = col, textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Box(Modifier.size(10.dp).clip(CircleShape).background(level?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
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
