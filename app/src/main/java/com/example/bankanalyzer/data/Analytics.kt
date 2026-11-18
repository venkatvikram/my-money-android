package com.example.bankanalyzer.data

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

data class MonthFlow(val label: String, val inAmt: Double, val outAmt: Double)

data class Kpis(
    val moneyIn: Double,
    val moneyOut: Double,
    val net: Double,
    val savingsRate: Double,
    val count: Int,
    val days: Int,
    val avgDailySpend: Double,
    val biggestExpense: Txn?,
    val biggestIncome: Txn?,
    val categories: List<Pair<String, Double>>,
    val payees: List<Pair<String, Double>>,
    val months: List<MonthFlow>,
    val insights: List<String>,
    val first: Long?,
    val last: Long?,
)

object Analytics {
    private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    fun rupee(v: Double): String = inr.format(v.roundToLong())
    fun rupee2(v: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(v)

    /** Card bill payments are excluded from spending when card spends are already counted (avoids double counting). */
    fun spendable(txns: List<Txn>): List<Txn> {
        val hasCardSpends = txns.any { it.isCard }
        return txns.filter { !(hasCardSpends && !it.credit && it.category == "Credit card bill") }
    }

    fun compute(all: List<Txn>): Kpis {
        val txns = spendable(all)
        val ins = txns.filter { it.credit }
        val outs = txns.filter { !it.credit }
        val tin = ins.sumOf { it.amount }
        val tout = outs.sumOf { it.amount }
        val net = tin - tout
        val rate = if (tin > 0) net / tin * 100 else 0.0
        val first = txns.minOfOrNull { it.time }
        val last = txns.maxOfOrNull { it.time }
        val days = if (first != null && last != null) max(1, ((last - first) / 86_400_000L).toInt() + 1) else 1

        val cats = outs.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }
        val payees = outs.groupBy { it.desc.trim().lowercase() }
            .map { (_, v) -> v.first().desc to v.sumOf { it.amount } }
            .sortedByDescending { it.second }.take(10)

        val cal = Calendar.getInstance()
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val byMonth = txns.groupBy { cal.timeInMillis = it.time; cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH) }
        val months = byMonth.keys.sorted().takeLast(12).map { k ->
            val v = byMonth.getValue(k)
            MonthFlow("${monthNames[k % 12]} ${(k / 12) % 100}", v.filter { it.credit }.sumOf { it.amount }, v.filter { !it.credit }.sumOf { it.amount })
        }

        val insights = ArrayList<String>()
        if (txns.isNotEmpty()) {
            cats.firstOrNull()?.let { (n, v) -> insights.add("$n is your biggest spend: ${rupee(v)} (${pct(v, tout)} of spending).") }
            val small = outs.filter { it.amount <= 200 }
            if (small.size >= 5) insights.add("${small.size} small payments (≤ ₹200) add up to ${rupee(small.sumOf { it.amount })}.")
            if (tin > 0) insights.add(
                if (rate < 10) "Savings rate is ${rate.toInt()}% — aim to keep at least 20% of income."
                else if (rate < 20) "Savings rate is ${rate.toInt()}% — close to the 20% target."
                else "Savings rate is ${rate.toInt()}% — good going."
            )
            val atm = cats.firstOrNull { it.first == "ATM Cash" }?.second ?: 0.0
            if (atm > 0) insights.add("Cash withdrawn: ${rupee(atm)}. Cash spending can't be tracked after withdrawal.")
            val emi = cats.firstOrNull { it.first == "Loan / EMI" }?.second ?: 0.0
            if (emi > 0 && tin > 0) insights.add("Loans / EMIs take ${pct(emi, tin)} of income${if (emi / tin > 0.4) " — above the 40% comfort limit." else "."}")
            if (months.size >= 2) {
                val a = months[months.size - 2].outAmt; val b = months.last().outAmt
                if (a > 0) {
                    val ch = (b - a) / a * 100
                    insights.add("Spending in ${months.last().label} is ${if (ch >= 0) "up" else "down"} ${kotlin.math.abs(ch).toInt()}% vs ${months[months.size - 2].label}.")
                }
            }
            val weekend = outs.filter { cal.timeInMillis = it.time; val d = cal.get(Calendar.DAY_OF_WEEK); d == Calendar.SATURDAY || d == Calendar.SUNDAY }.sumOf { it.amount }
            if (tout > 0 && weekend / tout > 0.4) insights.add("${pct(weekend, tout)} of your spending happens on weekends.")
        }

        return Kpis(
            moneyIn = tin, moneyOut = tout, net = net, savingsRate = rate, count = txns.size, days = days,
            avgDailySpend = tout / days, biggestExpense = outs.maxByOrNull { it.amount },
            biggestIncome = ins.maxByOrNull { it.amount }, categories = cats, payees = payees,
            months = months, insights = insights, first = first, last = last
        )
    }

    private fun pct(a: Double, b: Double) = if (b > 0) "${(a / b * 100).toInt()}%" else "0%"

    /**
     * Current balance per bank account: latest reported "Avl Bal" (or a balance you typed in),
     * then every debit/credit SMS after it is applied on top.
     */
    fun balances(sms: List<Txn>, manual: Map<String, Pair<Double, Long>>, exclude: Set<String> = emptySet()): List<AccountBalance> {
        val accounts = (sms.filter { !it.isCard }.map { it.account } + manual.keys).distinct().filter { it !in exclude }
        return accounts.map { acc ->
            val txns = sms.filter { it.account == acc && !it.isCard }
            val smsAnchor = txns.filter { it.balanceAfter != null }.maxByOrNull { it.time }
            val man = manual[acc]
            val useManual = man != null && (smsAnchor == null || man.second >= smsAnchor.time)
            val anchorTime = if (useManual) man!!.second else smsAnchor?.time
            val anchorVal = if (useManual) man!!.first else smsAnchor?.balanceAfter
            if (anchorTime == null || anchorVal == null) {
                AccountBalance(acc, null, null, false, txns.size)
            } else {
                val after = txns.filter { it.time > anchorTime }
                val bal = anchorVal + after.sumOf { if (it.credit) it.amount else -it.amount }
                AccountBalance(acc, bal, anchorTime, useManual, after.size)
            }
        }.sortedWith(compareBy({ it.balance == null }, { it.account }))
    }

    fun startOfMonth(offsetMonths: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MONTH, offsetMonths)
    }.timeInMillis

    fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfYear(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
