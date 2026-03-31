package com.example.portmonnai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.portmonnai.ui.theme.SoberBlue
import com.example.portmonnai.ui.viewmodel.ChartFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    goldChartData: List<Pair<Long, Double>>?,
    otherChartData: List<Pair<Long, Double>>?,
    goldTransactions: List<Transaction>,
    otherTransactions: List<Transaction>,
    selectedFilter: ChartFilter,
    onFilterSelected: (ChartFilter) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tendances du Portefeuille", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    "Période d'analyse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                TrendsFilterSection(selectedFilter, onFilterSelected)
            }

            item {
                PortfolioTrendCard(
                    title = "Actifs Financiers",
                    data = otherChartData,
                    transactions = otherTransactions,
                    subtitle = "Actions, ETFs, Cryptos..."
                )
            }

            item {
                PortfolioTrendCard(
                    title = "Or & Métaux Physiques",
                    data = goldChartData,
                    transactions = goldTransactions,
                    subtitle = "Pièces, Lingots, Lingotins"
                )
            }
        }
    }
}

@Composable
fun TrendsFilterSection(
    selectedFilter: ChartFilter,
    onFilterSelected: (ChartFilter) -> Unit
) {
    // On regroupe par lignes de 4 ou 5
    val filters = ChartFilter.values()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.toList().chunked(5).forEach { rowFilters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowFilters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Surface(
                        onClick = { onFilterSelected(filter) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f).height(40.dp),
                        tonalElevation = if (isSelected) 4.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = filter.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                // Fill with empty boxes if needed to keep grid alignment
                repeat(5 - rowFilters.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PortfolioTrendCard(
    title: String,
    data: List<Pair<Long, Double>>?,
    transactions: List<Transaction>,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (data == null) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (data.size < 2) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("Données insuffisantes sur cette période", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                // On réutilise PerformanceChartCard mais simplifiée pour l'affichage total
                // Note: On pourrait extraire PerformanceChart comme composant commun.
                // Pour l'instant, je vais l'implémenter ici de façon compacte.
                Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    SimpleTrendChart(data, transactions)
                }
            }
        }
    }
}

@Composable
fun SimpleTrendChart(data: List<Pair<Long, Double>>, transactions: List<Transaction>) {
    // On réutilise la logique de AssetDetailScreen ici mais adaptée au contexte "Trends"
    // On affiche désormais les points de transaction agrégés sur la courbe
    PerformanceChartCard(
        data = data,
        transactions = transactions, 
        selectedFilter = ChartFilter.H24, // Non utilisé ici pour le titre
        onFilterSelected = {} 
    )
}
