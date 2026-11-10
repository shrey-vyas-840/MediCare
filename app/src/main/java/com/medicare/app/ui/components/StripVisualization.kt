package com.medicare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StripVisualization(
    rows: Int,
    columns: Int,
    initialQuantity: Int,
    currentInventory: Int,
    modifier: Modifier = Modifier,
    manualAdjustments: List<Long> = emptyList()
) {
    val totalSlots = if (rows > 0 && columns > 0) rows * columns else maxOf(initialQuantity, 18)
    val actRows = if (rows > 0) rows else 2
    val actCols = if (columns > 0) columns else (totalSlots + actRows - 1) / actRows

    val consumedCount = (initialQuantity - currentInventory).coerceAtLeast(0)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pill Strip (${actRows}x${actCols} = $totalSlots)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tablets left: $currentInventory / $initialQuantity",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        for (r in 0 until actRows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (c in 0 until actCols) {
                    val index = r * actCols + c
                    val cellColor = when {
                        index >= totalSlots -> Color.Transparent
                        index < consumedCount -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f) // Grey/consumed
                        index < initialQuantity -> MaterialTheme.colorScheme.primary // Remaining/available
                        else -> Color.Transparent
                    }

                    val textColor = when {
                        index >= totalSlots -> Color.Transparent
                        index < consumedCount -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onPrimary
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cellColor)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index < totalSlots && index < initialQuantity) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 9.sp,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }

        if (manualAdjustments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "⚠️ Manual adjustments recorded (${manualAdjustments.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
