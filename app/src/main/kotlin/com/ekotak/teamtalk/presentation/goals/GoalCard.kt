package com.ekotak.teamtalk.presentation.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekotak.teamtalk.domain.model.Goal
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Karta celu — mobilny odpowiednik kafla z panelu: pierścień z procentem,
 * wartość bieżąca na tle docelowej, pasek z kreską TEMPA i status.
 *
 * Kreska tempa to sedno modułu: 60% w połowie kwartału to inna wiadomość niż
 * 60% na trzy dni przed końcem. Liczbę przysyła serwer, tu ją tylko rysujemy.
 */
@Composable
fun GoalCard(
    goal: Goal,
    onClose: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onCheckin: (() -> Unit)? = null,
) {
    val color = if (goal.isClosed) MaterialTheme.colorScheme.outline else goalStatusColor(goal.status)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressRing(pct = goal.pct, color = color)

            Column(Modifier.weight(1f)) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${goalValueText(goal.value, goal.unit)} / ${goalValueText(goal.target, goal.unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PaceBar(
                    pct = goal.pct,
                    pacePct = goal.pacePct,
                    color = color,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(goal = goal, color = color)
                    Text(
                        text = when {
                            goal.isManual && goal.lastCheckinAt != null ->
                                "wpis ręczny · ${goal.lastCheckinAt.take(10)}"
                            goal.isManual -> "wpis ręczny · brak"
                            else -> "${goal.source} · auto"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                goal.ruleLabel?.let { reward ->
                    Text(
                        text = "Nagroda: $reward",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (onCheckin != null || onClose != null || onDelete != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        onCheckin?.let { TextButton(onClick = it) { Text("Wpis") } }
                        onClose?.let { TextButton(onClick = it) { Text("Zamknij okres") } }
                        onDelete?.let { TextButton(onClick = it) { Text("Usuń") } }
                    }
                }
            }
        }
    }
}

/** Plakietka statusu; cel z kolejki mówi wprost, że czeka na wysyłkę. */
@Composable
private fun StatusPill(goal: Goal, color: Color) {
    val (label, tint) = when {
        goal.pending -> "W kolejce" to SyncBlue
        goal.isClosed -> "okres zamknięty" to MaterialTheme.colorScheme.outline
        else -> goal.status.label to color
    }
    Surface(shape = RoundedCornerShape(999.dp), color = tint.copy(alpha = 0.16f)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Pierścień postępu — rysowany, nie składany z komponentów, bo to jeden wskaźnik. */
@Composable
private fun ProgressRing(pct: Double, color: Color) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val sweep = (pct.coerceIn(0.0, 100.0) / 100.0 * 360.0).toFloat()
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "${Math.round(pct)}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

/**
 * Pasek realizacji z pionową kreską tempa. Kreskę rysujemy tylko w trakcie
 * okresu: po jego końcu „powinno być 100%" niczego już nie mówi.
 */
@Composable
fun PaceBar(pct: Double, pacePct: Double, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth((pct.coerceIn(0.0, 100.0) / 100.0).toFloat())
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        if (pacePct > 0 && pacePct < 100) {
            Canvas(Modifier.fillMaxSize()) {
                val x = (size.width * (pacePct / 100.0)).toFloat()
                drawRect(
                    color = marker,
                    topLeft = Offset(x, 0f),
                    size = Size(2.dp.toPx(), size.height),
                )
            }
        }
    }
}
