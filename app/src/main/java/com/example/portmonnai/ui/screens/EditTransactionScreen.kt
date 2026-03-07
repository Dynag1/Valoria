package com.example.portmonnai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.portmonnai.domain.model.Transaction
import com.example.portmonnai.domain.model.TransactionType
import com.example.portmonnai.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    transaction: Transaction,
    onSave: (Transaction) -> Unit,
    onCancel: () -> Unit
) {
    var quantity by remember { mutableStateOf(transaction.quantity.toString()) }
    var price by remember { mutableStateOf(transaction.priceAtDate.toString()) }
    var fees by remember { mutableStateOf(transaction.fees.toString()) }
    var transactionType by remember { mutableStateOf(transaction.type) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = transaction.date)
    var showDatePicker by remember { mutableStateOf(false) }
    val formattedDate = remember(datePickerState.selectedDateMillis) {
        val millis = datePickerState.selectedDateMillis ?: transaction.date
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(millis))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifier la Transaction") },
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
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val updated = transaction.copy(
                        type = transactionType,
                        quantity = quantity.replace(',', '.').toDoubleOrNull() ?: transaction.quantity,
                        priceAtDate = price.replace(',', '.').toDoubleOrNull() ?: transaction.priceAtDate,
                        fees = fees.replace(',', '.').toDoubleOrNull() ?: transaction.fees,
                        date = datePickerState.selectedDateMillis ?: transaction.date
                    )
                    onSave(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                enabled = quantity.isNotBlank() && price.isNotBlank()
            ) {
                Text("Enregistrer les modifications")
            }
        }
    }
}
