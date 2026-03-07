package com.example.portmonnai

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.portmonnai.domain.model.PortfolioAsset
import com.example.portmonnai.domain.model.Transaction
import com.example.portmonnai.ui.screens.*
import com.example.portmonnai.ui.theme.PortMonnaiTheme
import com.example.portmonnai.ui.viewmodel.PortfolioViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("portmonnai_prefs", Context.MODE_PRIVATE)

        setContent {
            PortMonnaiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val viewModel: PortfolioViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsState()
                    val scope = rememberCoroutineScope()

                    // État navigation
                    var selectedAsset by remember { mutableStateOf<PortfolioAsset?>(null) }
                    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }

                    // Dossier de sauvegarde (persisté en prefs)
                    var dataFolderUri by remember {
                        mutableStateOf(prefs.getString("data_folder_uri", null))
                    }

                    // Initialisation du sync au 1er démarrage
                    LaunchedEffect(Unit) {
                        viewModel.setSyncFolderUri(dataFolderUri)
                        viewModel.autoLoadFromFolder()
                    }

                    // Interception d'un fichier .val ouvert depuis l'explorateur de fichiers Android
                    LaunchedEffect(intent) {
                        if (intent?.action == Intent.ACTION_VIEW) {
                            intent.data?.let { uri ->
                                try {
                                    val json = contentResolver.openInputStream(uri)?.use { stream ->
                                        stream.readBytes().toString(Charsets.UTF_8)
                                    }
                                    if (json != null) {
                                        viewModel.importFromJson(json)
                                        // On réinitialise l'intent pour ne pas recharger à chaque rotation d'écran
                                        setIntent(Intent())
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    // ── ActivityResult Launchers ───────────────────────────

                    // 1. Sélecteur de dossier (OpenDocumentTree)
                    val folderPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                            val uriStr = uri.toString()
                            prefs.edit().putString("data_folder_uri", uriStr).apply()
                            dataFolderUri = uriStr
                            // Notifier le ViewModel et faire une sauvegarde immédiate
                            viewModel.setSyncFolderUri(uriStr)
                            viewModel.forceSaveToFolder()
                        }
                    }

                    // 2. Export : CreateDocument dans le dossier choisi ou via sélecteur
                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
                    ) { uri: Uri? ->
                        if (uri != null) {
                            scope.launch {
                                try {
                                    val json = viewModel.exportToJson()
                                    contentResolver.openOutputStream(uri)?.use { stream ->
                                        stream.write(json.toByteArray())
                                    }
                                    viewModel.notifyExportSuccess()
                                } catch (e: Exception) {
                                    viewModel.notifyExportError(e.message ?: "Erreur")
                                }
                            }
                        }
                    }

                    // 3. Import : OpenDocument
                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            scope.launch {
                                try {
                                    val json = contentResolver.openInputStream(uri)?.use { stream ->
                                        stream.readBytes().toString(Charsets.UTF_8)
                                    } ?: return@launch
                                    viewModel.importFromJson(json)
                                } catch (e: Exception) {
                                    viewModel.notifyExportError("Erreur de lecture : ${e.message}")
                                }
                            }
                        }
                    }

                    // Nom du fichier export avec date
                    fun exportFileName(): String {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        return "valoria_backup_$date.val"
                    }

                    // ── Navigation ─────────────────────────────────────────

                    NavHost(navController = navController, startDestination = "dashboard") {

                        composable("dashboard") {
                            DashboardScreen(
                                totalValue = uiState.totalValue,
                                totalProfit = uiState.totalProfit,
                                totalProfitPercentage = uiState.totalProfitPercentage,
                                totalProfitToday = uiState.totalProfitToday,
                                assets = uiState.portfolioAssets,
                                isRefreshing = uiState.isRefreshing,
                                importMessage = uiState.importMessage,
                                onRefresh = { viewModel.refresh() },
                                onAddTransaction = { navController.navigate("add_transaction") },
                                onAssetClick = { asset ->
                                    selectedAsset = asset
                                    viewModel.loadTransactionsForAsset(asset.asset.id)
                                    navController.navigate("asset_detail")
                                },
                                onDeleteAsset = { asset -> viewModel.deleteAsset(asset.asset.id) },
                                onSettingsClick = { navController.navigate("settings") },
                                onImportMessageDismissed = { viewModel.clearImportMessage() }
                            )
                        }

                        composable("add_transaction") {
                            AddTransactionScreen(
                                searchResults = uiState.searchResults,
                                onSearch = { viewModel.searchAssets(it) },
                                onSave = { asset, transaction ->
                                    viewModel.addTransaction(asset, transaction)
                                    navController.popBackStack()
                                },
                                onCancel = { navController.popBackStack() }
                            )
                        }

                        composable("asset_detail") {
                            val asset = selectedAsset
                            if (asset != null) {
                                AssetDetailScreen(
                                    portfolioAsset = asset,
                                    transactions = uiState.selectedAssetTransactions,
                                    historicalChartData = uiState.selectedAssetChartData,
                                    onBack = {
                                        viewModel.clearSelectedAssetTransactions()
                                        navController.popBackStack()
                                    },
                                    onEditTransaction = { tx ->
                                        transactionToEdit = tx
                                        navController.navigate("edit_transaction")
                                    },
                                    onDeleteTransaction = { tx ->
                                        viewModel.deleteTransaction(tx.id, tx.assetId)
                                    }
                                )
                            }
                        }

                        composable("edit_transaction") {
                            val tx = transactionToEdit
                            if (tx != null) {
                                EditTransactionScreen(
                                    transaction = tx,
                                    onSave = { updatedTx ->
                                        viewModel.updateTransaction(updatedTx)
                                        navController.popBackStack()
                                    },
                                    onCancel = { navController.popBackStack() }
                                )
                            }
                        }

                        composable("settings") {
                            SettingsScreen(
                                dataFolderPath = dataFolderUri?.let { uri ->
                                    // Affichage du chemin lisible
                                    try {
                                        Uri.decode(uri)
                                            .substringAfter("primary:")
                                            .ifBlank { uri }
                                    } catch (e: Exception) { uri }
                                },
                                onBack = { navController.popBackStack() },
                                onExport = { exportLauncher.launch(exportFileName()) },
                                onImport = { importLauncher.launch(arrayOf("*/*")) },
                                onChooseFolder = { folderPickerLauncher.launch(null) }
                            )
                        }
                    }
                }
            }
        }
    }
}
