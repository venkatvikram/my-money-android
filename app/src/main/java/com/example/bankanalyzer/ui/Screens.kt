@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankanalyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bankanalyzer.data.AccountBalance
import com.example.bankanalyzer.data.Analytics
import com.example.bankanalyzer.data.Asset
import com.example.bankanalyzer.data.Txn

@Composable
fun Header(title: String, sub: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountLine(title: String, sub: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("  ›", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
    }
    HorizontalDivider()
}

// ---------------- Home ----------------

@Composable
fun HomeScreen(
    granted: Boolean,
    showSettingsHint: Boolean,
    loading: Boolean,
    sms: List<Txn>,
    balances: List<AccountBalance>,
    cardAccounts: List<String>,
    hiddenAccounts: List<String>,
    assets: List<Asset>,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onAccountClick: (String) -> Unit,
    onAssetClick: (Asset) -> Unit,
    onAddAsset: (String) -> Unit,
    onTxnClick: (Txn) -> Unit,
) {
    val monthStart = Analytics.startOfMonth()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("My Money", "Offline · reads bank SMS on this phone only") }
        if (!granted) item {
            Section("Allow reading bank SMS") {
                Text(
                    "To track spending and update your balance automatically, the app reads bank and UPI SMS in your inbox. " +
                        "It has no internet access, so nothing ever leaves this phone. It never sends SMS, and needs no location, microphone or call access.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (showSettingsHint) {
                    Text("Permission was denied. Open Settings → Permissions → SMS → Allow.", fontSize = 13.sp, color = outColor())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text("Open app settings") }
                } else Button(onClick = onAllow) { Text("Allow SMS access") }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }

        val known = balances.filter { it.balance != null }
        val bankTotal = known.sumOf { it.balance!! }
        val assetTotal = assets.sumOf { it.worth() }
        item {
            Section("") {
                Text("NET WORTH", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Analytics.rupee2(bankTotal + assetTotal), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row { Text("Bank balance", Modifier.weight(1f), fontSize = 13.sp); Text(if (known.isEmpty()) "—" else Analytics.rupee2(bankTotal), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Row { Text("Investments & gold", Modifier.weight(1f), fontSize = 13.sp); Text(Analytics.rupee2(assetTotal), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                if (granted && known.isEmpty()) Text("No balance found in SMS yet — tap an account below to enter it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (granted) {
            val today = Analytics.startOfDay()
            val month = Analytics.spendable(sms.filter { it.time >= monthStart })
            val spent = month.filter { !it.credit }.sumOf { it.amount }
            val recv = month.filter { it.credit }.sumOf { it.amount }
            val todaySpent = sms.filter { it.time >= today && !it.credit }.sumOf { it.amount }
            item {
                KpiGrid(
                    listOf(
                        Kpi("Spent this month", Analytics.rupee(spent), outColor()),
                        Kpi("Received this month", Analytics.rupee(recv), inColor()),
                        Kpi("Net this month", Analytics.rupee(recv - spent), if (recv >= spent) inColor() else outColor()),
                        Kpi("Spent today", Analytics.rupee(todaySpent), outColor()),
                    )
                )
            }

            item {
                Section("Bank accounts") {
                    Text("Tap an account to set its balance or mark it as a credit card.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (balances.isEmpty()) Text("No bank SMS found yet.", fontSize = 13.sp)
                    balances.forEach { b ->
                        AccountLine(
                            b.account,
                            when {
                                b.balance == null -> "Balance unknown — tap to enter"
                                b.fromManual -> "Set by you ${fmtDate(b.asOf!!)} + ${b.txnsSinceAnchor} SMS since"
                                else -> "From SMS ${fmtDateTime(b.asOf!!)}" + if (b.txnsSinceAnchor > 0) " + ${b.txnsSinceAnchor} since" else ""
                            },
                            b.balance?.let { Analytics.rupee2(it) } ?: "—"
                        ) { onAccountClick(b.account) }
                    }
                }
            }

            if (cardAccounts.isNotEmpty()) item {
                Section("Credit cards") {
                    cardAccounts.forEach { c ->
                        val m = sms.filter { it.account == c && !it.credit && it.time >= monthStart }.sumOf { it.amount }
                        AccountLine(c, "Spent this month", Analytics.rupee(m)) { onAccountClick(c) }
                    }
                }
            }
        }

        item {
            Section("Investments & gold") {
                if (assets.isEmpty()) Text("Add your mutual funds, gold and other savings to see your full net worth.", fontSize = 13.sp)
                assets.forEach { a ->
                    AccountLine(
                        a.name,
                        when (a.kind) {
                            "GOLD" -> "Gold · ${trimNum(a.grams)} g × ${Analytics.rupee(a.pricePerGram)}/g · updated ${fmtDate(a.updated)}"
                            "MF" -> "Mutual fund · updated ${fmtDate(a.updated)}"
                            else -> "Updated ${fmtDate(a.updated)}"
                        },
                        Analytics.rupee(a.worth())
                    ) { onAssetClick(a) }
                }
                Spacer(Modifier.height(8.dp))
                // Equal widths so all three fit on small phones too.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pad = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    OutlinedButton(onClick = { onAddAsset("MF") }, Modifier.weight(1f), contentPadding = pad) { Text("+ Fund", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(onClick = { onAddAsset("GOLD") }, Modifier.weight(1f), contentPadding = pad) { Text("+ Gold", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(onClick = { onAddAsset("OTHER") }, Modifier.weight(1f), contentPadding = pad) { Text("+ Other", fontSize = 12.sp, maxLines = 1) }
                }
            }
        }

        if (granted) {
            item {
                Section("Recent") {
                    if (sms.isEmpty()) Text("No transactions found in your SMS.", fontSize = 13.sp)
                    sms.take(6).forEach { TxnRow(it, onClick = onTxnClick) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRefresh) { Text("Refresh from SMS") }
                }
            }
            if (hiddenAccounts.isNotEmpty()) item {
                Section("Hidden accounts") {
                    hiddenAccounts.forEach { h -> AccountLine(h, "Ignored — tap to show again", "") { onAccountClick(h) } }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.3f".format(d).trimEnd('0').trimEnd('.')

// ---------------- Transactions ----------------

@Composable
fun TransactionsScreen(sms: List<Txn>, granted: Boolean, onTxnClick: (Txn) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 spent, 2 received
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    val accounts = remember(sms) { sms.map { it.account }.distinct().sorted() }
    val list = sms.filter {
        (filter == 0 || (filter == 1 && !it.credit) || (filter == 2 && it.credit)) && (account == null || it.account == account)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Transactions", "${list.size} from bank SMS · tap one to set its category") }
        item {
            LazyRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == 0, { filter = 0 }, { Text("All") }) }
                item { FilterChip(filter == 1, { filter = 1 }, { Text("Spent") }) }
                item { FilterChip(filter == 2, { filter = 2 }, { Text("Received") }) }
                items(accounts) { a -> FilterChip(account == a, { account = if (account == a) null else a }, { Text(a) }) }
            }
        }
        if (!granted) item { Section("") { Text("Allow SMS access on the Home tab to see transactions.", fontSize = 13.sp) } }
        items(list) { t -> Column(Modifier.padding(horizontal = 16.dp)) { TxnRow(t, onClick = onTxnClick) } }
    }
}

// ---------------- Analysis (SMS) ----------------

@Composable
fun AnalysisScreen(sms: List<Txn>, onTxnClick: (Txn) -> Unit) {
    var period by rememberSaveable { mutableStateOf(0) }
    val labels = listOf("This month", "Last month", "3 months", "This year", "All")
    val from = when (period) {
        0 -> Analytics.startOfMonth()
        1 -> Analytics.startOfMonth(-1)
        2 -> Analytics.startOfMonth(-2)
        3 -> Analytics.startOfYear()
        else -> 0L
    }
    val to = if (period == 1) Analytics.startOfMonth() else Long.MAX_VALUE
    val data = sms.filter { it.time in from until to }
    val k = remember(data) { Analytics.compute(data) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Spending analysis", "From your bank SMS") }
        item {
            LazyRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(labels.size) { i -> FilterChip(period == i, { period = i }, { Text(labels[i]) }) }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
        item { Dashboard(k, data, onTxnClick) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

// ---------------- Statement ----------------

@Composable
fun StatementScreen(
    name: String?,
    txns: List<Txn>,
    error: String?,
    busy: Boolean,
    onImport: () -> Unit,
    onClear: () -> Unit,
    onTxnClick: (Txn) -> Unit,
) {
    val k = remember(txns) { Analytics.compute(txns) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Bank statement", "Import CSV or Excel exported from net banking") }
        item {
            Section("") {
                Text(
                    name?.let { "Loaded: $it (${txns.size} transactions)" }
                        ?: "Download your statement as Excel (.xlsx) or CSV from your bank's app / net banking, then import it here. It's read on this phone only.",
                    fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport, enabled = !busy) { Text(if (name == null) "Import statement" else "Import another") }
                    if (name != null) OutlinedButton(onClick = onClear) { Text("Remove") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = outColor(), fontSize = 13.sp) }
            }
        }
        if (txns.isNotEmpty()) {
            item { Dashboard(k, txns, onTxnClick) }
            val closing = txns.filter { it.balanceAfter != null }.maxByOrNull { it.time }?.balanceAfter
            if (closing != null) item {
                Section("Closing balance in statement") { Text(Analytics.rupee2(closing), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
            item {
                Section("All transactions (${txns.size})") {
                    val sorted = txns.sortedByDescending { it.time }
                    (if (showAll) sorted else sorted.take(30)).forEach { TxnRow(it, showAccount = false, onClick = onTxnClick) }
                    if (!showAll && sorted.size > 30) TextButton(onClick = { showAll = true }) { Text("Show all ${sorted.size}") }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
