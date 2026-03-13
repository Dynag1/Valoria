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
        val totalValue = assets.sumOf { it.totalValue }
        val totalProfitToday = assets.sumOf { it.profitToday }
        val totalProfitTodayPercentage = if (totalValue - totalProfitToday > 0)
            (totalProfitToday / (totalValue - totalProfitToday)) * 100.0 else 0.0

        provideContent {
            GlanceTheme {
                PortfolioWidgetContent(totalValue, totalProfitToday, totalProfitTodayPercentage)
            }
        }
    }

    @Composable
    private fun PortfolioWidgetContent(
        totalValue: Double,
        profitToday: Double,
        profitTodayPct: Double
    ) {
        val profitColor = if (profitToday >= 0) SoberSuccess else SoberError
        val sign = if (profitToday >= 0) "+" else ""

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp)
                .clickable(actionRunCallback<RefreshAction>())
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(20.dp)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Valeur Totale",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = "${formatValue(totalValue)} €",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = GlanceModifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val formattedGain = "${formatValue(Math.abs(profitToday))} €"
                    val displayGain = if (profitToday >= 0) "+ $formattedGain" else "- $formattedGain"
                    
                    Text(
                        text = displayGain,
                        style = TextStyle(
                            color = ColorProvider(profitColor),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "$sign${String.format("%.2f", profitTodayPct)}%",
                        style = TextStyle(
                            color = ColorProvider(profitColor),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
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
