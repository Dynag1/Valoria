package com.example.portmonnai.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.portmonnai.data.repository.PortfolioRepository
import com.example.portmonnai.domain.model.Asset
import com.example.portmonnai.domain.model.AssetType
import com.example.portmonnai.domain.model.PortfolioAsset
import com.example.portmonnai.domain.model.Transaction
import com.example.portmonnai.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.portmonnai.ui.widget.PortfolioWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

private const val BACKUP_FILENAME = "Valoria.val"
 
enum class ChartFilter(val label: String) {
    H24("24h"),
    D7("7j"),
    M1("1m"),
    Y1("1an"),
    Y5("5ans"),
    ALL("Tout")
}

private const val PREFS_NAME = "price_alerts_prefs"
private const val ALERTS_ENABLED_KEY = "alerts_enabled"

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    // URI du dossier de synchronisation (optionnel)
    private var syncFolderUri: Uri? = null

    private var portfolioJob: Job? = null

    init {
        loadPortfolio()
        loadSettings()
    }

    private fun loadSettings() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _uiState.update { it.copy(notificationsEnabled = prefs.getBoolean(ALERTS_ENABLED_KEY, true)) }
    }

    fun toggleNotifications(enabled: Boolean) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ALERTS_ENABLED_KEY, enabled).apply()
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    /** Appelé depuis MainActivity avec l'URI persisté en SharedPreferences */
    fun setSyncFolderUri(uriString: String?) {
        syncFolderUri = uriString?.let { Uri.parse(it) }
    }

    /** Charge le portefeuille depuis la base locale + auto-import depuis dossier sync au premier démarrage */
    private fun loadPortfolio() {
        portfolioJob?.cancel()
        portfolioJob = repository.getPortfolioAssets()
            .onStart { _uiState.update { it.copy(isLoading = true) } }
            .onEach { assets ->
                val totalValue = assets.sumOf { it.totalValue }
                val totalProfit = assets.sumOf { it.totalProfit }
                val totalAllTimeCost = assets.sumOf { it.totalAllTimeCost }
                val totalProfitPct = if (totalAllTimeCost > 0) (totalProfit / totalAllTimeCost) * 100.0 else 0.0
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        portfolioAssets = assets,
                        totalValue = totalValue,
                        totalProfit = totalProfit,
                        totalProfitPercentage = totalProfitPct,
                        totalProfitToday = assets.sumOf { a -> a.profitToday },
                        totalProfitTodayPercentage = if (totalValue - assets.sumOf { a -> a.profitToday } > 0)
                            (assets.sumOf { a -> a.profitToday } / (totalValue - assets.sumOf { a -> a.profitToday })) * 100.0 else 0.0
                    )
                }
                // Mettre à jour le widget
                viewModelScope.launch {
                    PortfolioWidget().updateAll(appContext)
                }
            }
            .catch { e ->
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        repository.requestRefresh()
        // Forcer aussi le rechargement depuis le fichier cloud si configuré
        autoLoadFromFolder(silent = false)
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

    fun loadTransactionsForAsset(portfolioAsset: PortfolioAsset) {
        val assetId = portfolioAsset.asset.id
        _uiState.update { it.copy(selectedAssetId = assetId, selectedAssetChartData = null, selectedChartFilter = ChartFilter.ALL) }
        // Lancer le chargement du graphique immédiatement avec l'asset déjà connu
        loadChartData(portfolioAsset.asset.id, portfolioAsset.asset.symbol, portfolioAsset.asset.type, ChartFilter.ALL)
        repository.getTransactionsForAsset(assetId)
            .onEach { txList ->
                _uiState.update { it.copy(selectedAssetTransactions = txList) }
            }
            .launchIn(viewModelScope)
    }

    fun changeChartFilter(filter: ChartFilter) {
        if (_uiState.value.selectedChartFilter == filter) return
        _uiState.update { it.copy(selectedChartFilter = filter, selectedAssetChartData = null) }
        val assetId = _uiState.value.selectedAssetId ?: return
        val asset = _uiState.value.portfolioAssets.find { it.asset.id == assetId }?.asset
        if (asset != null) {
            loadChartData(asset.id, asset.symbol, asset.type, filter)
        }
    }

    private fun loadChartData(assetId: String, symbol: String, type: AssetType, filter: ChartFilter) {
        viewModelScope.launch {
            val MS_IN_DAY = 86400000L
            val now = System.currentTimeMillis()
            val startDateMs = when (filter) {
                ChartFilter.H24 -> now - MS_IN_DAY
                ChartFilter.D7 -> now - (7 * MS_IN_DAY)
                ChartFilter.M1 -> now - (30 * MS_IN_DAY)
                ChartFilter.Y1 -> now - (365 * MS_IN_DAY)
                ChartFilter.Y5 -> now - (5 * 365 * MS_IN_DAY)
                ChartFilter.ALL -> 0L
            }
            
            val data = repository.getAssetHistoricalPrices(assetId, symbol, type, startDateMs)
            _uiState.update { it.copy(selectedAssetChartData = data) }
        }
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

            // Recherche robuste du fichier existant (évite les doublons créés par Android)
            var file = folder.findFile(BACKUP_FILENAME)
            
            // Si non trouvé par findFile, on balaye les fichiers (parfois nécessaire avec SAF)
            if (file == null) {
                file = folder.listFiles().find { it.name == BACKUP_FILENAME }
            }

            // Création si vraiment inexistant
            if (file == null) {
                file = folder.createFile("application/octet-stream", BACKUP_FILENAME)
            }
            
            val finalFile = file ?: return

            appContext.contentResolver.openOutputStream(finalFile.uri, "wt")?.use { stream ->
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
     * @param silent Si vrai, ne pas afficher de message d'erreur/succès (utilisé pour le sync auto)
     */
    fun autoLoadFromFolder(silent: Boolean = true) {
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
                    val result = repository.importFromJson(json, clearExisting = true)
                    if (!silent) {
                        val msg = when (result) {
                            is PortfolioRepository.ImportResult.Success -> "✅ Sync réussie (fichier cloud)"
                            is PortfolioRepository.ImportResult.Error -> "❌ Échec sync : ${result.message}"
                        }
                        _uiState.update { it.copy(importMessage = msg) }
                    }
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
    val totalProfitTodayPercentage: Double = 0.0,
    val selectedAssetTransactions: List<Transaction> = emptyList(),
    val selectedAssetChartData: List<Pair<Long, Double>>? = null,
    val selectedChartFilter: ChartFilter = ChartFilter.ALL,
    val selectedAssetId: String? = null,
    val importMessage: String? = null,
    val notificationsEnabled: Boolean = true,
    val error: String? = null
)
