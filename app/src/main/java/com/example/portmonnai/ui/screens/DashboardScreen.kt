package com.example.portmonnai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.portmonnai.domain.model.AssetType
import com.example.portmonnai.domain.model.PortfolioAsset
import com.example.portmonnai.ui.theme.SoberBlue
import com.example.portmonnai.ui.theme.SoberSuccess
import com.example.portmonnai.ui.theme.SoberError
import androidx.compose.material.icons.filled.Sort

enum class AssetSortOrder {
    NAME, VALUE, PROFIT, PROFIT_PERCENTAGE, PROFIT_TODAY_PERCENTAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    totalValue: Double,
    totalProfit: Double,
    totalProfitPercentage: Double,
    totalProfitToday: Double,
    totalProfitTodayPercentage: Double,
    assets: List<PortfolioAsset>,
    isRefreshing: Boolean,
    importMessage: String?,
    onRefresh: () -> Unit,
    onAddTransaction: () -> Unit,
    onAssetClick: (PortfolioAsset) -> Unit,
    onDeleteAsset: (PortfolioAsset) -> Unit,
    onSettingsClick: () -> Unit,
    onImportMessageDismissed: () -> Unit
) {
    var assetToDelete by remember { mutableStateOf<PortfolioAsset?>(null) }
    var sortOrder by remember { mutableStateOf(AssetSortOrder.PROFIT_TODAY_PERCENTAGE) }
    var showSortMenu by remember { mutableStateOf(false) }
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    val snackbarHostState = remember { SnackbarHostState() }

    val categories = listOf("ETF", "CRYPTO", "MÉTAUX", "ACTIONS")

    val groupedAssets = remember(assets, sortOrder) {
        val grouped = assets.groupBy { 
            when (it.asset.type) {
                AssetType.ETF -> "ETF"
                AssetType.CRYPTO -> "CRYPTO"
                AssetType.GOLD_BAR, AssetType.GOLD_INGOT, AssetType.GOLD_COIN, AssetType.METAL -> "MÉTAUX"
                else -> "ACTIONS"
            }
        }
        grouped.mapValues { (_, list) ->
            when (sortOrder) {
                AssetSortOrder.NAME -> list.sortedBy { it.asset.name }
                AssetSortOrder.VALUE -> list.sortedByDescending { it.totalValue }
                AssetSortOrder.PROFIT -> list.sortedByDescending { it.totalProfit }
                AssetSortOrder.PROFIT_PERCENTAGE -> list.sortedByDescending { it.profitPercentage }
                AssetSortOrder.PROFIT_TODAY_PERCENTAGE -> list.sortedByDescending { it.profitTodayPercentage }
            }
        }
    }

    LaunchedEffect(importMessage) {
        if (importMessage != null) {
            snackbarHostState.showSnackbar(importMessage)
            onImportMessageDismissed()
        }
    }

    // Dialog confirmation suppression
    assetToDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { assetToDelete = null },
            title = { Text("Supprimer la position ?") },
            text = {
                Text(
                    "Supprimer ${asset.asset.name} supprimera aussi toutes ses transactions.\n\n" +
                    "Valeur actuelle : €${formatValue(asset.totalValue)}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteAsset(asset); assetToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = SoberError)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { assetToDelete = null }) { Text("Annuler") }
            }
        )
    }

    Scaffold(
        bottomBar = {
            // Barre basse : hamburger à gauche, + à droite
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Paramètres",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))

                    SmallFloatingActionButton(
                        onClick = onAddTransaction,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        if (pullState.isRefreshing) {
            LaunchedEffect(true) { onRefresh() }
        }
        if (!isRefreshing) {
            LaunchedEffect(isRefreshing) { pullState.endRefresh() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    PortfolioHeader(assets = assets)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mes Actifs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Trier", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Trier par Valeur") },
                                    onClick = { sortOrder = AssetSortOrder.VALUE; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Trier par Profit") },
                                    onClick = { sortOrder = AssetSortOrder.PROFIT; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Trier par Profit %") },
                                    onClick = { sortOrder = AssetSortOrder.PROFIT_PERCENTAGE; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Trier par Profit 24h %") },
                                    onClick = { sortOrder = AssetSortOrder.PROFIT_TODAY_PERCENTAGE; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Trier par Nom") },
                                    onClick = { sortOrder = AssetSortOrder.NAME; showSortMenu = false }
                                )
                            }
                        }
                    }
                }

                items(categories) { category ->
                    val categoryAssets = groupedAssets[category] ?: emptyList()
                    if (categoryAssets.isNotEmpty()) {
                        val isExpanded = expandedStates[category] == true
                        val totalCatValue = categoryAssets.sumOf { it.totalValue }
                        val totalCatProfit = categoryAssets.sumOf { it.totalProfit }
                        val totalCatAllTimeCost = categoryAssets.sumOf { it.totalAllTimeCost }
                        val totalCatProfitPct = if (totalCatAllTimeCost > 0) (totalCatProfit / totalCatAllTimeCost) * 100.0 else 0.0
                        val totalCatProfitToday = categoryAssets.sumOf { it.profitToday }
                        val totalCatProfitTodayPct = if (totalCatValue - totalCatProfitToday > 0) 
                            (totalCatProfitToday / (totalCatValue - totalCatProfitToday)) * 100.0 else 0.0

                        val profitColor = if (totalCatProfit >= 0) SoberSuccess else SoberError
                        val profitTodayColor = if (totalCatProfitToday >= 0) SoberSuccess else SoberError

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { expandedStates[category] = !isExpanded },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    // Vertical accent bar
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(4.dp)
                                            .height(24.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                            )
                                    )
                                    
                                    Column(modifier = Modifier.padding(16.dp).padding(start = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "€${formatValue(totalCatValue)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.rotate(if (isExpanded) 90f else 0f),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Profit Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            Row {
                                                Text(
                                                    "${if (totalCatProfit >= 0) "+" else ""}€${formatValue(totalCatProfit)}",
                                                    color = profitColor, fontSize = 13.sp, fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    " (${String.format("%.1f", totalCatProfitPct)}%)",
                                                    color = profitColor, fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Aujourd'hui", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            Row {
                                                Text(
                                                    "${if (totalCatProfitToday >= 0) "+" else ""}€${formatValue(totalCatProfitToday)}",
                                                    color = profitTodayColor, fontSize = 13.sp, fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    " (${String.format("%.1f", totalCatProfitTodayPct)}%)",
                                                    color = profitTodayColor, fontSize = 12.sp
                                                )
                                            }
                                        }
                                        }
                                    }
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    categoryAssets.forEach { asset ->
                                        AssetCard(
                                            portfolioAsset = asset,
                                            onClick = { onAssetClick(asset) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

fun formatValue(value: Double, decimals: Int = 2): String {
    return if (Math.abs(value) < 1.0 && value != 0.0) {
        String.format("%.6f", value).trimEnd('0').trimEnd('.')
    } else {
        String.format("%.$decimals" + "f", value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteAssetCard(
    portfolioAsset: PortfolioAsset,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteRequest()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    SoberError else Color.Transparent,
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) {
        AssetCard(portfolioAsset = portfolioAsset, onClick = onClick)
    }
}

@Composable
fun PortfolioHeader(assets: List<PortfolioAsset>) {
    val goldTypes = listOf(AssetType.GOLD_BAR, AssetType.GOLD_INGOT, AssetType.GOLD_COIN, AssetType.METAL)
    val goldAssets = assets.filter { it.asset.type in goldTypes }
    val otherAssets = assets.filter { it.asset.type !in goldTypes }

    val goldTotalValue = goldAssets.sumOf { it.totalValue }
    val goldTotalProfit = goldAssets.sumOf { it.totalProfit }
    val goldTotalAllTimeCost = goldAssets.sumOf { it.totalAllTimeCost }
    val goldTotalProfitPercentage = if (goldTotalAllTimeCost > 0) (goldTotalProfit / goldTotalAllTimeCost) * 100.0 else 0.0
    val goldProfitToday = goldAssets.sumOf { it.profitToday }
    val goldProfitTodayPercentage = if (goldTotalValue - goldProfitToday > 0) (goldProfitToday / (goldTotalValue - goldProfitToday)) * 100.0 else 0.0

    val otherTotalValue = otherAssets.sumOf { it.totalValue }
    val otherTotalProfit = otherAssets.sumOf { it.totalProfit }
    val otherTotalAllTimeCost = otherAssets.sumOf { it.totalAllTimeCost }
    val otherTotalProfitPercentage = if (otherTotalAllTimeCost > 0) (otherTotalProfit / otherTotalAllTimeCost) * 100.0 else 0.0
    val otherProfitToday = otherAssets.sumOf { it.profitToday }
    val otherProfitTodayPercentage = if (otherTotalValue - otherProfitToday > 0) (otherProfitToday / (otherTotalValue - otherProfitToday)) * 100.0 else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // First Column: Actifs
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatColumn(
                    title = "Actifs",
                    totalValue = otherTotalValue,
                    totalProfit = otherTotalProfit,
                    totalProfitPercentage = otherTotalProfitPercentage,
                    profitToday = otherProfitToday,
                    profitTodayPercentage = otherProfitTodayPercentage
                )
            }
            
            // Vertical Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(80.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )

            // Second Column: Or
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatColumn(
                    title = "Or Physique",
                    totalValue = goldTotalValue,
                    totalProfit = goldTotalProfit,
                    totalProfitPercentage = goldTotalProfitPercentage,
                    profitToday = goldProfitToday,
                    profitTodayPercentage = goldProfitTodayPercentage
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    title: String,
    totalValue: Double,
    totalProfit: Double,
    totalProfitPercentage: Double,
    profitToday: Double,
    profitTodayPercentage: Double
) {
    val profitColor = if (totalProfit >= 0) SoberSuccess else SoberError
    val profitTodayColor = if (profitToday >= 0) SoberSuccess else SoberError
    val trendIcon = if (totalProfit >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

    Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Text(
        text = "€${formatValue(totalValue)}",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 20.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(trendIcon, contentDescription = null, tint = profitColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${if (totalProfit >= 0) "+" else ""}€${formatValue(totalProfit)}",
            color = profitColor, fontWeight = FontWeight.Bold, fontSize = 14.sp
        )
    }
    Surface(shape = RoundedCornerShape(8.dp), color = profitColor.copy(alpha = 0.15f), modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "${if (totalProfitPercentage >= 0) "+" else ""}${String.format("%.2f", totalProfitPercentage)}%",
            color = profitColor, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text("Aujourd'hui", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("${if (profitToday >= 0) "+" else ""}€${formatValue(profitToday)}", color = profitTodayColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = profitTodayColor.copy(alpha = 0.15f), modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "${if (profitTodayPercentage >= 0) "+" else ""}${String.format("%.2f", profitTodayPercentage)}%",
            color = profitTodayColor, fontWeight = FontWeight.Bold, fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun AssetCard(portfolioAsset: PortfolioAsset, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp, 
            if (portfolioAsset.profitToday >= 0) SoberSuccess.copy(alpha = 0.6f) else SoberError.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        portfolioAsset.asset.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${formatValue(portfolioAsset.totalQuantity, 4)} ${portfolioAsset.asset.symbol}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("€${formatValue(portfolioAsset.totalValue)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "€${formatValue(portfolioAsset.asset.currentPrice ?: 0.0)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Row {
                        Text(
                            "${if (portfolioAsset.totalProfit >= 0) "+" else ""}€${formatValue(portfolioAsset.totalProfit)}",
                            color = if (portfolioAsset.totalProfit >= 0) SoberSuccess else SoberError, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(${String.format("%.1f", portfolioAsset.profitPercentage)}%)", color = if (portfolioAsset.totalProfit >= 0) SoberSuccess else SoberError, fontSize = 14.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Aujourd'hui", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Row {
                        Text(
                            "${if (portfolioAsset.profitToday >= 0) "+" else ""}€${formatValue(portfolioAsset.profitToday)}",
                            color = if (portfolioAsset.profitToday >= 0) SoberSuccess else SoberError, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(${String.format("%.1f", portfolioAsset.profitTodayPercentage)}%)", color = if (portfolioAsset.profitToday >= 0) SoberSuccess else SoberError, fontSize = 14.sp)
                }
            }
        }
    }
}
}
