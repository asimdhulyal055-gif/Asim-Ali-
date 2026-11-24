package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.domain.model.SalesChartPoint
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCurrency
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.CraveGoldContainer
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusEmerald

@Composable
fun SalesChartCard(
    points: List<SalesChartPoint>,
    isSampleData: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Intraday Revenue Curve",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val peak = points.maxByOrNull { it.revenue }
                    Text(
                        text = if (peak != null) "Peak window: ${peak.label} (${formatCurrency(peak.revenue)})" else "No sales intervals recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSampleData) {
                    StatusPill(
                        text = "SAMPLE",
                        containerColor = CraveGoldContainer,
                        contentColor = CraveGoldBright
                    )
                }
            }

            if (points.isEmpty()) {
                Text(
                    text = "Sales chart will populate as orders are billed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val maxRevenue = (points.maxOfOrNull { it.revenue } ?: 1.0).coerceAtLeast(1.0)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(top = 8.dp)
                ) {
                    val chartHeight = size.height - 16f
                    val barSlotWidth = size.width / points.size.coerceAtLeast(1)
                    val barWidth = (barSlotWidth * 0.44f).coerceAtLeast(14f)

                    // Horizontal subtle reference lines
                    for (step in 1..3) {
                        val y = chartHeight * (1f - step / 3f)
                        drawLine(
                            color = ObsidianBorder.copy(alpha = 0.65f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f
                        )
                    }

                    val linePath = Path()
                    points.forEachIndexed { index, point ->
                        val ratio = (point.revenue / maxRevenue).toFloat().coerceIn(0.05f, 1f)
                        val barHeight = chartHeight * ratio
                        val centerX = barSlotWidth * index + barSlotWidth / 2f
                        val topY = chartHeight - barHeight

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(CraveGoldBright, CraveGold.copy(alpha = 0.35f)),
                                startY = topY,
                                endY = chartHeight
                            ),
                            topLeft = Offset(centerX - barWidth / 2f, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(10f, 10f)
                        )

                        if (index == 0) {
                            linePath.moveTo(centerX, topY)
                        } else {
                            linePath.lineTo(centerX, topY)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = StatusEmerald,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )

                    points.forEachIndexed { index, point ->
                        val ratio = (point.revenue / maxRevenue).toFloat().coerceIn(0.05f, 1f)
                        val centerX = barSlotWidth * index + barSlotWidth / 2f
                        val topY = chartHeight * (1f - ratio)
                        drawCircle(
                            color = CraveGoldBright,
                            radius = 6f,
                            center = Offset(centerX, topY)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    points.forEach { point ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${point.orderCount} ord",
                                style = MaterialTheme.typography.labelSmall,
                                color = CraveGold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
