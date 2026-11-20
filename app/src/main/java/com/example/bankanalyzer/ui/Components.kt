package com.example.bankanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bankanalyzer.data.Analytics
import com.example.bankanalyzer.data.Kpis
import com.example.bankanalyzer.data.MonthFlow
import com.example.bankanalyzer.data.Txn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun inColor(): Color = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF15803D)
@Composable fun outColor(): Color = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFB91C1C)

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val dateTimeFmt = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
fun fmtDate(t: Long): String = dateFmt.format(Date(t))
fun fmtDateTime(t: Long): String = dateTimeFmt.format(Date(t))

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title.isNotEmpty()) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

data class Kpi(val label: String, val value: String, val color: Color? = null)

@Composable
fun KpiGrid(items: List<Kpi>) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        for (row in items.chunked(2)) {
            Row(Modifier.fillMaxWidth()) {
                for (k in row) {
                    Card(
                        modifier = Modifier.weight(1f).padding(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(k.label.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                k.value, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = k.color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun BarList(
    items: List<Pair<String, Double>>,
    total: Double,
    color: Color = MaterialTheme.colorScheme.primary,
    selected: String? = null,
    onClick: ((String) -> Unit)? = null,
) {
    val maxV = items.maxOfOrNull { it.second } ?: 1.0
    for ((name, v) in items) {
        val sel = name == selected
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick(name) } else Modifier)
                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                .padding(vertical = 5.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, Modifier.weight(0.38f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.Bold else null)
            Box(Modifier.weight(0.30f).padding(horizontal = 6.dp)) {
                Box(
                    Modifier.fillMaxWidth(safeFraction(v, maxV)).height(12.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
            Text(Analytics.rupee(v), Modifier.weight(0.24f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.End)
            if (total > 0) Text("${(v / total * 100).toInt()}%", Modifier.width(38.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
    }
}

/** Bars plus a table with the actual ₹ totals for every month. */
@Composable
fun MonthChart(months: List<MonthFlow>) {
    if (months.isEmpty()) { Text("No data yet.", fontSize = 13.sp); return }
    val cin = inColor(); val cout = outColor()
    val maxV = months.maxOf { maxOf(it.inAmt, it.outAmt) }.takeIf { it.isFinite() }?.coerceAtLeast(1.0) ?: 1.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(cin)); Text(" In   ", fontSize = 12.sp)
        Box(Modifier.size(10.dp).background(cout)); Text(" Out", fontSize = 12.sp)
    }
    Spacer(Modifier.height(6.dp))
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val slot = size.width / months.size
        val bw = slot * 0.32f
        months.forEachIndexed { i, m ->
            val x = i * slot + slot * 0.14f
            val hi = (m.inAmt / maxV * size.height).toFloat()
            val ho = (m.outAmt / maxV * size.height).toFloat()
            drawRect(cin, Offset(x, size.height - hi), Size(bw, hi))
            drawRect(cout, Offset(x + bw + slot * 0.04f, size.height - ho), Size(bw, ho))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        months.forEach { Text(it.label, Modifier.weight(1f), fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Spacer(Modifier.height(10.dp))
    // Totals table
    @Composable fun cell(text: String, w: Float, bold: Boolean = false, color: Color? = null, start: Boolean = false) {
        Text(text, Modifier.padding(vertical = 3.dp).then(Modifier), fontSize = 12.sp, maxLines = 1,
            fontWeight = if (bold) FontWeight.Bold else null, color = color ?: MaterialTheme.colorScheme.onSurface,
            textAlign = if (start) TextAlign.Start else TextAlign.End)
    }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(0.22f)) { cell("Month", 0f, bold = true, start = true) }
        Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell("In", 0f, bold = true) }
        Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell("Out", 0f, bold = true) }
        Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell("Net", 0f, bold = true) }
    }
    HorizontalDivider()
    for (m in months.reversed()) {
        val net = m.inAmt - m.outAmt
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(0.22f)) { cell(m.label, 0f, start = true) }
            Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell(Analytics.rupee(m.inAmt), 0f, color = cin) }
            Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell(Analytics.rupee(m.outAmt), 0f, color = cout) }
            Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell(Analytics.rupee(net), 0f, bold = true, color = if (net >= 0) cin else cout) }
        }
    }
    HorizontalDivider()
    val tin = months.sumOf { it.inAmt }; val tout = months.sumOf { it.outAmt }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(0.22f)) { cell("Total", 0f, bold = true, start = true) }
        Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell(Analytics.rupee(tin), 0f, bold = true, color = cin) }
        Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell(Analytics.rupee(tout), 0f, bold = true, color = cout) }
        Box(Modifier.weight(0.26f), contentAlignment = Alignment.CenterEnd) { cell(Analytics.rupee(tin - tout), 0f, bold = true, color = if (tin >= tout) cin else cout) }
    }
}

@Composable
fun TxnRow(t: Txn, showAccount: Boolean = true, onClick: ((Txn) -> Unit)? = null) = Column(
    if (onClick != null) Modifier.clickable { onClick(t) } else Modifier
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.desc, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(fmtDateTime(t.time), t.category, if (showAccount) t.account else null).joinToString(" · "),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            (if (t.credit) "+ " else "− ") + Analytics.rupee2(t.amount),
            color = if (t.credit) inColor() else outColor(), fontWeight = FontWeight.Bold, fontSize = 14.sp
        )
    }
    HorizontalDivider()
}

/** Full KPI dashboard used for both SMS data and imported statements. */
@Composable
fun Dashboard(k: Kpis, txns: List<Txn>, onTxnClick: (Txn) -> Unit) = Column {
    val cin = inColor(); val cout = outColor()
    var selCat by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllCats by rememberSaveable { mutableStateOf(false) }
    KpiGrid(
        listOf(
            Kpi("Money in", Analytics.rupee(k.moneyIn), cin),
            Kpi("Money out", Analytics.rupee(k.moneyOut), cout),
            Kpi("Net savings", Analytics.rupee(k.net), if (k.net >= 0) cin else cout),
            Kpi("Savings rate", "${k.savingsRate.toInt()}%"),
            Kpi("Avg spend / day", Analytics.rupee(k.avgDailySpend)),
            Kpi("Transactions", k.count.toString()),
            Kpi("Biggest expense", k.biggestExpense?.let { Analytics.rupee(it.amount) } ?: "—"),
            Kpi("Top category", k.categories.firstOrNull()?.first ?: "—"),
        )
    )
    if (k.first != null && k.last != null) {
        Text(
            "${fmtDate(k.first)} – ${fmtDate(k.last)}  (${k.days} days)",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (k.insights.isNotEmpty()) Section("Insights") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { k.insights.forEach { Text("• $it", fontSize = 13.sp) } }
    }
    Section("Spending by category") {
        if (k.categories.isEmpty()) Text("No spending found.", fontSize = 13.sp)
        else {
            Text("Tap a category to see its payments. Tap a payment to change its category.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            BarList(
                if (showAllCats) k.categories else k.categories.take(10), k.moneyOut,
                selected = selCat, onClick = { selCat = if (selCat == it) null else it }
            )
            if (!showAllCats && k.categories.size > 10) TextButton(onClick = { showAllCats = true }) { Text("Show all ${k.categories.size} categories") }
            selCat?.let { c ->
                val list = txns.filter { !it.credit && it.category == c }.sortedByDescending { it.amount }
                Spacer(Modifier.height(8.dp))
                Text("$c — ${list.size} payments", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val byPayee = list.groupBy { it.desc }.map { (p, v) -> p to v.sumOf { it.amount } }.sortedByDescending { it.second }.take(8)
                if (byPayee.size > 1) BarList(byPayee, list.sumOf { it.amount }, MaterialTheme.colorScheme.tertiary)
                list.take(40).forEach { TxnRow(it, onClick = onTxnClick) }
                if (list.size > 40) Text("+ ${list.size - 40} more", fontSize = 12.sp)
            }
        }
    }
    Section("Money in vs out by month") { MonthChart(k.months) }
    Section("Top payees") { if (k.payees.isEmpty()) Text("—", fontSize = 13.sp) else BarList(k.payees, 0.0, MaterialTheme.colorScheme.tertiary) }
    k.biggestExpense?.let { b -> Section("Largest single expense") { TxnRow(b, onClick = onTxnClick) } }
}

/** Bar length in 0.02..1, never NaN/infinite (which would crash layout on some data). */
private fun safeFraction(v: Double, max: Double): Float {
    val f = if (max > 0 && v.isFinite()) (v / max).toFloat() else 0f
    return if (f.isNaN()) 0.02f else f.coerceIn(0.02f, 1f)
}
