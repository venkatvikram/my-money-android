@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bankanalyzer.data.Analytics
import com.example.bankanalyzer.data.Asset
import com.example.bankanalyzer.data.Overrides
import com.example.bankanalyzer.data.Txn

private fun numOnly(v: String) = v.filter { it.isDigit() || it == '.' }

/** Change a payment's category; optionally remember it for every payment to the same payee. */
@Composable
fun CategoryDialog(
    txn: Txn,
    options: List<String>,
    onSave: (category: String, forPayee: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(txn.category) }
    var custom by remember { mutableStateOf("") }
    val generic = Overrides.isGeneric(txn)
    var forPayee by remember { mutableStateOf(!generic) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set category") },
        text = {
            Column {
                Text("${txn.desc} · ${Analytics.rupee2(txn.amount)} · ${fmtDate(txn.time)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it.take(30) }, singleLine = true,
                    label = { Text("New category (e.g. Shop, Milk, Tuition)") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(options) { o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = custom.isBlank() && chosen == o, onClick = { chosen = o; custom = "" })
                            Text(o, fontSize = 14.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = forPayee, onCheckedChange = { forPayee = it }, enabled = !generic)
                    Text(
                        if (generic) "This payment has no payee name, so only this one changes."
                        else "Also use for all past and future payments to \"${txn.desc}\"",
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(custom.trim().ifBlank { chosen }, forPayee && !generic) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Account type (bank / credit card / hide) and the balance for a bank account. */
@Composable
fun AccountDialog(
    account: String,
    currentType: String,
    onSave: (type: String, balance: Double?, resetBalance: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(currentType) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(account) },
        text = {
            Column {
                Text("What is this account?", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == "bank", { type = "bank" }, { Text("Bank account") })
                    FilterChip(type == "card", { type = "card" }, { Text("Credit card") })
                    FilterChip(type == "hide", { type = "hide" }, { Text("Hide") })
                }
                Text(
                    when (type) {
                        "card" -> "Card spends count as spending but not against your bank balance."
                        "hide" -> "This account's SMS will be ignored everywhere."
                        else -> "Enter the balance shown in your bank app now (optional). New SMS are added on top."
                    }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (type == "bank") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { text = numOnly(it) }, label = { Text("Current balance (₹)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(type, text.toDoubleOrNull(), false) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (type == "bank") TextButton(onClick = { onSave(type, null, true) }) { Text("Reset balance") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Add / edit a mutual fund, gold or other asset. */
@Composable
fun AssetDialog(
    initial: Asset,
    isNew: Boolean,
    onSave: (Asset) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var value by remember { mutableStateOf(if (initial.value > 0) initial.value.toString() else "") }
    var grams by remember { mutableStateOf(if (initial.grams > 0) initial.grams.toString() else "") }
    var price by remember { mutableStateOf(if (initial.pricePerGram > 0) initial.pricePerGram.toString() else "") }
    val gold = initial.kind == "GOLD"
    val worth = if (gold) (grams.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0) else value.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (initial.kind) { "MF" -> "Mutual fund"; "GOLD" -> "Gold"; else -> "Other asset" }) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text(if (gold) "Name (e.g. Jewellery, Coins, SGB)" else if (initial.kind == "MF") "Fund / folio name" else "Name (e.g. FD, PPF, Cash)") }
                )
                Spacer(Modifier.height(6.dp))
                if (gold) {
                    OutlinedTextField(
                        value = grams, onValueChange = { grams = numOnly(it) }, singleLine = true, label = { Text("Weight (grams)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = price, onValueChange = { price = numOnly(it) }, singleLine = true, label = { Text("Today's price per gram (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Text("The app is offline, so update the price per gram yourself when it changes.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = value, onValueChange = { value = numOnly(it) }, singleLine = true, label = { Text("Current value (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Worth: ${Analytics.rupee2(worth)}", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name.trim().ifBlank { if (gold) "Gold" else if (initial.kind == "MF") "Mutual fund" else "Asset" },
                        value = value.toDoubleOrNull() ?: 0.0, grams = grams.toDoubleOrNull() ?: 0.0,
                        pricePerGram = price.toDoubleOrNull() ?: 0.0, updated = System.currentTimeMillis()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
