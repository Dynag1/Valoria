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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.portmonnai.domain.model.PortfolioAsset
import com.example.portmonnai.ui.theme.Gold
import com.example.portmonnai.ui.theme.GreenHedge
import com.example.portmonnai.ui.theme.RedHedge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    totalValue: Double,
    totalProfit: Double,
    totalProfitPercentage: Double,
    totalProfitToday: Double,
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
    val snackbarHostState = remember { SnackbarHostState() }

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
                    colors = ButtonDefaults.textButtonColors(contentColor = RedHedge)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { assetToDelete = null }) { Text("Annuler") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = Gold,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        bottomBar = {
            // Barre basse : hamburger à gauche
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Paramètres",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    // Espace pour ne pas chevaucher le FAB
                    Spacer(modifier = Modifier.weight(1f))
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
                    PortfolioHeader(totalValue, totalProfit, totalProfitPercentage, totalProfitToday)
                }

                item {
                    Text(
                        text = "Mes Actifs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(assets, key = { it.asset.id }) { asset ->
                    SwipeToDeleteAssetCard(
                        portfolioAsset = asset,
                        onClick = { onAssetClick(asset) },
                        onDeleteRequest = { assetToDelete = asset }
                    )
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
                    RedHedge else Color.Transparent,
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
fun PortfolioHeader(
    totalValue: Double,
    totalProfit: Double,
    totalProfitPercentage: Double,
    totalProfitToday: Double
) {
    val profitColor = if (totalProfit >= 0) GreenHedge else RedHedge
    val profitTodayColor = if (totalProfitToday >= 0) GreenHedge else RedHedge
    val trendIcon = if (totalProfit >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(Gold.copy(alpha = 0.1f), Color.Transparent)))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Valeur Totale", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
                Text(
                    text = "€${formatValue(totalValue)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Gold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(trendIcon, contentDescription = null, tint = profitColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${if (totalProfit >= 0) "+" else ""}€${formatValue(totalProfit)}",
                        color = profitColor, fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = profitColor.copy(alpha = 0.15f)) {
                        Text(
                            text = "${if (totalProfitPercentage >= 0) "+" else ""}${String.format("%.2f", totalProfitPercentage)}%",
                            color = profitColor, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("${if (totalProfit >= 0) "+" else ""}€${formatValue(totalProfit)}", color = profitColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${if (totalProfitPercentage >= 0) "+" else ""}${String.format("%.2f", totalProfitPercentage)}%", color = profitColor, fontSize = 13.sp)
                    }
                    HorizontalDivider(modifier = Modifier.height(48.dp).width(1.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aujourd'hui", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("${if (totalProfitToday >= 0) "+" else ""}€${formatValue(totalProfitToday)}", color = profitTodayColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AssetCard(portfolioAsset: PortfolioAsset, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(portfolioAsset.asset.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${formatValue(portfolioAsset.totalQuantity, 4)} ${portfolioAsset.asset.symbol} • €${formatValue(portfolioAsset.asset.currentPrice ?: 0.0)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                }
                Text("€${formatValue(portfolioAsset.totalValue)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                            color = if (portfolioAsset.totalProfit >= 0) GreenHedge else RedHedge, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(${String.format("%.1f", portfolioAsset.profitPercentage)}%)", color = if (portfolioAsset.totalProfit >= 0) GreenHedge else RedHedge, fontSize = 14.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Aujourd'hui", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Row {
                        Text(
                            "${if (portfolioAsset.profitToday >= 0) "+" else ""}€${formatValue(portfolioAsset.profitToday)}",
                            color = if (portfolioAsset.profitToday >= 0) GreenHedge else RedHedge, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(${String.format("%.1f", portfolioAsset.profitTodayPercentage)}%)", color = if (portfolioAsset.profitToday >= 0) GreenHedge else RedHedge, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
