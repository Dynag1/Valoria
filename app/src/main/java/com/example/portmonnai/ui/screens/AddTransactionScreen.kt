package com.example.portmonnai.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.portmonnai.domain.model.*
import com.example.portmonnai.ui.theme.SoberBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    searchResults: List<Asset>,
    onSearch: (String) -> Unit,
    onSave: (Asset, Transaction) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AssetType.CRYPTO) }
    var transactionType by remember { mutableStateOf(TransactionType.BUY) }
    var selectedAssetId by remember { mutableStateOf("") }
    
    // Date state
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }
    val formattedDate = remember(datePickerState.selectedDateMillis) {
        val millis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(millis))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle Transaction") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Annuler") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Transaction Type Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = transactionType == TransactionType.BUY,
                    onClick = { transactionType = TransactionType.BUY },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Achat") }
                SegmentedButton(
                    selected = transactionType == TransactionType.SELL,
                    onClick = { transactionType = TransactionType.SELL },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Vente") }
            }

            // Name with Search
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        onSearch(it)
                    },
                    label = { Text("Nom de l'actif") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (searchResults.isNotEmpty() && name.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        searchResults.forEach { asset ->
                            TextButton(
                                onClick = {
                                    name = asset.name
                                    symbol = asset.symbol
                                    type = asset.type
                                    selectedAssetId = asset.id
                                    asset.currentPrice?.let { price = it.toString() }
                                    onSearch("") // clear results
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(asset.name)
                                    Text(asset.symbol, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it },
                label = { Text("Symbole") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantité") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Prix Unitaire (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = fees,
                onValueChange = { fees = it },
                label = { Text("Frais (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Date Picker Trigger
            OutlinedIconButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: $formattedDate")
                }
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("OK") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val asset = Asset(
                        id = if (selectedAssetId.isNotEmpty()) selectedAssetId else name.lowercase().replace(" ", "_"),
                        name = name,
                        symbol = symbol,
                        type = type
                    )
                    val transaction = Transaction(
                        assetId = asset.id,
                        type = transactionType,
                        quantity = quantity.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        priceAtDate = price.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        date = datePickerState.selectedDateMillis ?: System.currentTimeMillis(),
                        fees = fees.replace(',', '.').toDoubleOrNull() ?: 0.0
                    )
                    onSave(asset, transaction)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && quantity.isNotBlank() && price.isNotBlank()
            ) {
                Text("Enregistrer")
            }
        }
    }
}

@Composable
fun RowScope.AssetTypeButton(targetType: AssetType, label: String, currentType: AssetType, onClick: (AssetType) -> Unit) {
    FilterChip(
        selected = currentType == targetType,
        onClick = { onClick(targetType) },
        label = { Text(label) },
        modifier = Modifier.weight(1f)
    )
}
