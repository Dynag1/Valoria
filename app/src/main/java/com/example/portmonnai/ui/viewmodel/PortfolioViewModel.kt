package com.example.portmonnai.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.portmonnai.data.repository.PortfolioRepository
import com.example.portmonnai.domain.model.Asset
import com.example.portmonnai.domain.model.PortfolioAsset
import com.example.portmonnai.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val BACKUP_FILENAME = "valoria_backup.json"

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    // URI du dossier de synchronisation (optionnel)
    private var syncFolderUri: Uri? = null

    init {
        loadPortfolio()
    }

    /** Appelé depuis MainActivity avec l'URI persisté en SharedPreferences */
    fun setSyncFolderUri(uriString: String?) {
        syncFolderUri = uriString?.let { Uri.parse(it) }
    }

    /** Charge le portefeuille depuis la base locale + auto-import depuis dossier sync au premier démarrage */
    private fun loadPortfolio() {
        repository.getPortfolioAssets()
            .onStart { _uiState.update { it.copy(isLoading = true) } }
            .onEach { assets ->
                val totalValue = assets.sumOf { it.totalValue }
                val totalInvested = assets.sumOf { it.totalQuantity * it.averageBuyPrice }
                val totalProfit = assets.sumOf { it.totalProfit }
                val totalProfitPct = if (totalInvested > 0) (totalProfit / totalInvested) * 100.0 else 0.0
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        portfolioAssets = assets,
                        totalValue = totalValue,
                        totalProfit = totalProfit,
                        totalProfitPercentage = totalProfitPct,
                        totalProfitToday = assets.sumOf { a -> a.profitToday }
                    )
                }
            }
            .catch { e ->
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadPortfolio()
    }

    // ── CRUD transactions/actifs ─────────────────────────────────

    fun addTransaction(asset: Asset, transaction: Transaction) {
        viewModelScope.launch {
            repository.ensureAssetExists(asset)
            repository.addTransaction(transaction)
            autoSaveToFolder()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            autoSaveToFolder()
        }
    }

    fun deleteTransaction(transactionId: Long, assetId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId, assetId)
            autoSaveToFolder()
        }
    }

    fun deleteAsset(assetId: String) {
        viewModelScope.launch {
            repository.deleteAsset(assetId)
            autoSaveToFolder()
        }
    }

    fun loadTransactionsForAsset(assetId: String) {
        _uiState.update { it.copy(selectedAssetChartData = null) } // Réinitialiser le chart
        repository.getTransactionsForAsset(assetId)
            .onEach { txList ->
                _uiState.update { it.copy(selectedAssetTransactions = txList) }
                
                // Charger le graphique en arrière-plan
                viewModelScope.launch {
                    val asset = _uiState.value.portfolioAssets.find { it.asset.id == assetId }?.asset
                    if (asset != null && txList.isNotEmpty()) {
                        val firstBuy = txList.filter { it.type == com.example.portmonnai.domain.model.TransactionType.BUY }.minOfOrNull { it.date }
                        if (firstBuy != null) {
                            val chart = repository.getAssetHistoricalPrices(asset.id, asset.symbol, asset.type, firstBuy)
                            _uiState.update { it.copy(selectedAssetChartData = chart) }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun clearSelectedAssetTransactions() {
        _uiState.update { it.copy(selectedAssetTransactions = emptyList(), selectedAssetChartData = null) }
    }

    fun searchAssets(query: String) {
        viewModelScope.launch {
            val results = repository.searchAssets(query)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    // ── Export / Import JSON ─────────────────────────────────────

    suspend fun exportToJson(): String = repository.exportToJson()

    fun importFromJson(json: String) {
        viewModelScope.launch {
            val result = repository.importFromJson(json)
            val msg = when (result) {
                is PortfolioRepository.ImportResult.Success ->
                    "✅ Import réussi : ${result.assets} actifs, ${result.transactions} transactions"
                is PortfolioRepository.ImportResult.Error ->
                    "❌ Erreur d'import : ${result.message}"
            }
            _uiState.update { it.copy(importMessage = msg) }
            autoSaveToFolder() // Sync après import
        }
    }

    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }

    fun notifyExportSuccess() {
        _uiState.update { it.copy(importMessage = "✅ Export réussi !") }
    }

    fun notifyExportError(message: String) {
        _uiState.update { it.copy(importMessage = "❌ Erreur : $message") }
    }

    // ── Synchronisation automatique ──────────────────────────────

    /**
     * Sauvegarde automatiquement le JSON dans le dossier de sync choisi.
     * Silencieux en cas d'erreur (le dossier est optionnel).
     */
    private suspend fun autoSaveToFolder() {
        val folderUri = syncFolderUri ?: return
        try {
            val json = repository.exportToJson()
            val folder = DocumentFile.fromTreeUri(appContext, folderUri) ?: return

            // Cherche le fichier existant ou le crée
            val file = folder.findFile(BACKUP_FILENAME)
                ?: folder.createFile("application/json", BACKUP_FILENAME)
                ?: return

            appContext.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
                stream.write(json.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace() // Silencieux — pas d'alerte pour l'auto-save
        }
    }

    /** Version publique pour déclencher une sauvegarde depuis l'extérieur (ex: après choix de dossier) */
    fun forceSaveToFolder() {
        viewModelScope.launch { autoSaveToFolder() }
    }

    /**
     * Charge le JSON depuis le dossier de sync si disponible.
     * Appelé au démarrage depuis MainActivity après avoir défini le dossier.
     */
    fun autoLoadFromFolder() {
        val folderUri = syncFolderUri ?: return
        viewModelScope.launch {
            try {
                val folder = DocumentFile.fromTreeUri(appContext, folderUri) ?: return@launch
                val file = folder.findFile(BACKUP_FILENAME) ?: return@launch
                if (!file.exists() || !file.canRead()) return@launch

                val json = appContext.contentResolver.openInputStream(file.uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: return@launch

                // Import en synchronisation au démarrage : on remplace le contenu local par le JSON (qui est le master)
                if (json.contains("\"transactions\"")) {
                    repository.importFromJson(json, clearExisting = true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

data class PortfolioUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val portfolioAssets: List<PortfolioAsset> = emptyList(),
    val searchResults: List<Asset> = emptyList(),
    val totalValue: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalProfitPercentage: Double = 0.0,
    val totalProfitToday: Double = 0.0,
    val selectedAssetTransactions: List<Transaction> = emptyList(),
    val selectedAssetChartData: List<Pair<Long, Double>>? = null,
    val importMessage: String? = null,
    val error: String? = null
)
