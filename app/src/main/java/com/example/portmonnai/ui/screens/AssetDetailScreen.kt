package com.example.portmonnai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.portmonnai.domain.model.PortfolioAsset
import com.example.portmonnai.domain.model.Transaction
import com.example.portmonnai.domain.model.TransactionType
import com.example.portmonnai.ui.theme.SoberBlue
import com.example.portmonnai.ui.theme.SoberSuccess
import com.example.portmonnai.ui.theme.SoberError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.portmonnai.ui.viewmodel.ChartFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    portfolioAsset: PortfolioAsset,
    transactions: List<Transaction>,
    historicalChartData: List<Pair<Long, Double>>?,
    selectedFilter: ChartFilter,
    onFilterSelected: (ChartFilter) -> Unit,
    onBack: () -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onDeleteAsset: (PortfolioAsset) -> Unit
) {
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var showDeleteAssetDialog by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Supprimer la transaction ?") },
            text = {
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(tx.date))
                Text(
                    "Êtes-vous sûr de vouloir supprimer cette transaction du $dateStr ?\n\n" +
                    "${if (tx.type == TransactionType.BUY) "Achat" else "Vente"} de ${formatValue(tx.quantity, 4)} @ €${formatValue(tx.priceAtDate)}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTransaction(tx)
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SoberError)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Asset delete confirmation dialog
    if (showDeleteAssetDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAssetDialog = false },
            title = { Text("Supprimer l'actif ?") },
            text = {
                Text("Voulez-vous vraiment supprimer ${portfolioAsset.asset.name} et toutes ses transactions ? Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAsset(portfolioAsset)
                        showDeleteAssetDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SoberError)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAssetDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(portfolioAsset.asset.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = portfolioAsset.asset.symbol,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteAssetDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer l'actif", tint = SoberError)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary card
            item {
                AssetSummaryCard(portfolioAsset)
            }

            // Performance Chart
            item {
                if (historicalChartData != null) {
                    if (historicalChartData.isNotEmpty()) {
                        PerformanceChartCard(
                            data = historicalChartData, 
                            transactions = transactions,
                            selectedFilter = selectedFilter,
                            onFilterSelected = onFilterSelected
                        )
                    } else {
                        // Graphique non disponible ou vide
                        PerformanceChartCard(
                            data = emptyList(), 
                            transactions = transactions,
                            selectedFilter = selectedFilter,
                            onFilterSelected = onFilterSelected
                        )
                    }
                } else {
                    // Chargement du graphique
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                Text(
                    text = "Transactions (${transactions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aucune transaction",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                items(transactions.sortedByDescending { it.date }) { tx ->
                    TransactionItem(
                        transaction = tx,
                        onEdit = { onEditTransaction(tx) },
                        onDelete = { transactionToDelete = tx }
                    )
                }
            }
        }
    }
}

@Composable
fun AssetSummaryCard(portfolioAsset: PortfolioAsset) {
    val profitColor = if (portfolioAsset.totalProfit >= 0) SoberSuccess else SoberError

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, profitColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Valeur actuelle", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("€${formatValue(portfolioAsset.totalValue)}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Quantité", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("${formatValue(portfolioAsset.totalQuantity, 4)} ${portfolioAsset.asset.symbol}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Prix moyen d'achat", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("€${formatValue(portfolioAsset.averageBuyPrice)}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Gain/Perte total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${if (portfolioAsset.totalProfit >= 0) "+" else ""}€${formatValue(portfolioAsset.totalProfit)}",
                            color = profitColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Surface(shape = RoundedCornerShape(6.dp), color = profitColor.copy(alpha = 0.15f)) {
                            Text(
                                text = "${if (portfolioAsset.profitPercentage >= 0) "+" else ""}${String.format("%.2f", portfolioAsset.profitPercentage)}%",
                                color = profitColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isBuy = transaction.type == TransactionType.BUY
    val typeColor = if (isBuy) SoberSuccess else SoberError
    val typeLabel = if (isBuy) "Achat" else "Vente"
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(transaction.date))
    val total = transaction.quantity * transaction.priceAtDate

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: type badge + info
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = typeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = typeLabel,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Column {
                    Text(dateStr, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${formatValue(transaction.quantity, 4)} @ €${formatValue(transaction.priceAtDate)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (transaction.fees > 0) {
                        Text(
                            text = "Frais: €${formatValue(transaction.fees)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Right: total + actions
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isBuy) "-" else "+"}€${formatValue(total)}",
                    color = if (isBuy) SoberError else SoberSuccess,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            tint = SoberError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceChartCard(
    data: List<Pair<Long, Double>>,
    transactions: List<Transaction>,
    selectedFilter: ChartFilter,
    onFilterSelected: (ChartFilter) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            var touchX by remember { mutableStateOf<Float?>(null) }
            var boxWidth by remember { mutableStateOf(0) }

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = when(selectedFilter) {
                        ChartFilter.H24 -> "Dernières 24h"
                        ChartFilter.D3 -> "3 derniers jours"
                        ChartFilter.D7 -> "7 derniers jours"
                        ChartFilter.D15 -> "15 derniers jours"
                        ChartFilter.M1 -> "Dernier mois"
                        ChartFilter.M6 -> "6 derniers mois"
                        ChartFilter.Y1 -> "Dernière année"
                        ChartFilter.Y5 -> "5 dernières années"
                        ChartFilter.ALL -> "Tout (depuis 2015)"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (data.size >= 2 && touchX != null && boxWidth > 0) {
                    val minTime = data.first().first
                    val maxTime = data.last().first
                    val timeRange = (maxTime - minTime).takeIf { it > 0 } ?: 1L
                    val xRatio = (touchX!! / boxWidth).coerceIn(0f, 1f)
                    val targetTime = minTime + (xRatio * timeRange).toLong()
                    val hoverPoint = data.minByOrNull { kotlin.math.abs(it.first - targetTime) }

                    if (hoverPoint != null) {
                        val originalPrice = data.first().second
                        val currentHoverPrice = hoverPoint.second
                        val isGain = currentHoverPrice >= originalPrice
                        val pct = if (originalPrice > 0) ((currentHoverPrice - originalPrice) / originalPrice) * 100 else 0.0
                        val valColor = if (isGain) SoberSuccess else SoberError
                        val sign = if (isGain) "+" else ""

                        val priceStr = "€${formatValue(currentHoverPrice)}"
                        val pctStr = "$sign${formatValue(pct)}%"
                        val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(hoverPoint.first))

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(priceStr, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(pctStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valColor)
                            }
                            Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (data.size < 2) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Données insuffisantes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            } else {
                val minPrice = data.minOf { it.second }
                val maxPrice = data.maxOf { it.second }
                val minTime = data.first().first
                val maxTime = data.last().first

                val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0
                val timeRange = (maxTime - minTime).takeIf { it > 0 } ?: 1L

                val isPositive = data.last().second >= data.first().second
                val lineColor = if (isPositive) SoberSuccess else SoberError

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { boxWidth = it.width }
                        .pointerInput(data) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                touchX = down.position.x
                                do {
                                    val event = awaitPointerEvent()
                                    val move = event.changes.firstOrNull()
                                    if (move != null) {
                                        if (move.pressed) {
                                            touchX = move.position.x
                                            move.consume()
                                        } else {
                                            touchX = null
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                touchX = null
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    val path = Path()

                    data.forEachIndexed { index, point ->
                        val x = ((point.first - minTime).toFloat() / timeRange.toFloat()) * width
                        val y = height - (((point.second - minPrice).toFloat() / range.toFloat()) * height)

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent)
                        )
                    )

                    // Draw transaction dots (BUY = Green, SELL = Red)
                    transactions.forEach { tx ->
                        if (tx.date in minTime..maxTime) {
                            // Find closest price in historical data to keep dot on the curve
                            val closestPoint = data.minByOrNull { kotlin.math.abs(it.first - tx.date) }
                            if (closestPoint != null) {
                                val txX = ((closestPoint.first - minTime).toFloat() / timeRange.toFloat()) * width
                                val txY = height - (((closestPoint.second - minPrice).toFloat() / range.toFloat()) * height)
                                
                                val dotColor = if (tx.type == TransactionType.BUY) SoberSuccess else SoberError
                                
                                // Shadow/Outter circle for visibility
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    radius = 5.dp.toPx(),
                                    center = Offset(txX, txY)
                                )
                                drawCircle(
                                    color = dotColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(txX, txY)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 1.5.dp.toPx(),
                                    center = Offset(txX, txY)
                                )
                            }
                        }
                    }

                    // Optional Indicator line
                    if (touchX != null && boxWidth > 0) {
                        val xRatio = (touchX!! / boxWidth).coerceIn(0f, 1f)
                        val targetTime = minTime + (xRatio * timeRange).toLong()
                        val hoverPoint = data.minByOrNull { kotlin.math.abs(it.first - targetTime) }
                        
                        if (hoverPoint != null) {
                            val px = ((hoverPoint.first - minTime).toFloat() / timeRange.toFloat()) * width
                            val py = height - (((hoverPoint.second - minPrice).toFloat() / range.toFloat()) * height)

                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(px, 0f),
                                end = Offset(px, height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 6.dp.toPx(),
                                center = Offset(px, py)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Period Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartFilter.values().forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Surface(
                        onClick = { onFilterSelected(filter) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.height(32.dp).weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
