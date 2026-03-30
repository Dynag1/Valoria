package com.example.portmonnai.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.LocalContext
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.updateAll
import com.example.portmonnai.data.repository.PortfolioRepository
import com.example.portmonnai.ui.theme.SoberError
import com.example.portmonnai.ui.theme.SoberSuccess
import com.example.portmonnai.domain.model.AssetType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class PortfolioWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PortfolioWidgetEntryPoint {
        fun repository(): PortfolioRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PortfolioWidgetEntryPoint::class.java
        )
        val repository = entryPoint.repository()

        // Récupérer les données
        val assets = repository.getPortfolioAssetsOnce()
        val goldTypes = listOf(AssetType.GOLD_BAR, AssetType.GOLD_INGOT, AssetType.GOLD_COIN, AssetType.METAL)
        
        val goldAssets = assets.filter { it.asset.type in goldTypes }
        val otherAssets = assets.filter { it.asset.type !in goldTypes }

        val otherValue = otherAssets.sumOf { it.totalValue }
        val otherProfitToday = otherAssets.sumOf { it.profitToday }
        val otherProfitTodayPct = if (otherValue - otherProfitToday > 0)
            (otherProfitToday / (otherValue - otherProfitToday)) * 100.0 else 0.0

        val goldValue = goldAssets.sumOf { it.totalValue }
        val goldProfitToday = goldAssets.sumOf { it.profitToday }
        val goldProfitTodayPct = if (goldValue - goldProfitToday > 0)
            (goldProfitToday / (goldValue - goldProfitToday)) * 100.0 else 0.0

        provideContent {
            GlanceTheme {
                PortfolioWidgetContent(
                    otherValue, otherProfitToday, otherProfitTodayPct,
                    goldValue, goldProfitToday, goldProfitTodayPct
                )
            }
        }
    }

    @Composable
    private fun PortfolioWidgetContent(
        otherValue: Double,
        otherProfit: Double,
        otherProfitPct: Double,
        goldValue: Double,
        goldProfit: Double,
        goldProfitPct: Double
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp)
                .clickable(actionRunCallback<RefreshAction>())
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(20.dp)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Section Actifs
                StatSection(
                    title = "Actifs",
                    value = otherValue,
                    profit = otherProfit,
                    profitPct = otherProfitPct,
                    modifier = GlanceModifier.defaultWeight()
                )

                // Divider
                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp)
                        .background(ColorProvider(Color.Gray.copy(alpha = 0.2f)))
                ) {}

                // Section Or
                StatSection(
                    title = "Or Phys.",
                    value = goldValue,
                    profit = goldProfit,
                    profitPct = goldProfitPct,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    @Composable
    private fun StatSection(
        title: String,
        value: Double,
        profit: Double,
        profitPct: Double,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val profitColor = if (profit >= 0) SoberSuccess else SoberError
        val sign = if (profit >= 0) "+" else ""

        Column(
            modifier = modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "${formatValue(value)} €",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            
            Spacer(modifier = GlanceModifier.height(2.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$sign${String.format("%.1f", profitPct)}%",
                    style = TextStyle(
                        color = ColorProvider(profitColor),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    private fun formatValue(value: Double): String {
        return if (Math.abs(value) < 1.0 && value != 0.0) {
            String.format("%.4f", value).trimEnd('0').trimEnd('.')
        } else {
            // Utiliser une version simple de formatage sans dépendances complexes
            val longVal = value.toLong()
            if (longVal >= 1000000) {
                 String.format("%.2fM", value / 1000000.0)
            } else {
                 String.format("%,.0f", value).replace(",", " ")
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PortfolioWidget.PortfolioWidgetEntryPoint::class.java
        )
        val repository = entryPoint.repository()
        
        // Forcer le rafraîchissement des prix
        repository.fetchCurrentPrices(force = true)
        
        // Mettre à jour tous les widgets avec les nouvelles valeurs calculées via getPortfolioAssets()
        // Note: PortfolioViewModel observe déjà le flow, mais ici on force l'update du widget direct
        PortfolioWidget().updateAll(context)
    }
}

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()
}
