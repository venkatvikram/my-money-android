package com.example.bankanalyzer.data

import kotlin.math.roundToLong

/** Applies what the user has taught the app: account types and category rules. */
object Overrides {
    private val generic = setOf("payment", "money received", "—", "")

    fun payeeKey(t: Txn): String = t.desc.trim().lowercase()
    fun txnKey(t: Txn): String = "${t.time}_${(t.amount * 100).roundToLong()}_${t.credit}"

    /** True when the payee name is a placeholder, so a rule for it would hit unrelated payments. */
    fun isGeneric(t: Txn): Boolean = payeeKey(t) in generic

    /** Detected type for an account when the user hasn't chosen one. */
    fun defaultType(txns: List<Txn>, account: String): String =
        if (txns.any { it.account == account && it.isCard }) "card" else "bank"

    fun apply(
        list: List<Txn>,
        payeeRules: Map<String, String>,
        txnRules: Map<String, String>,
        types: Map<String, String>,
    ): List<Txn> = list.mapNotNull { t ->
        val type = types[t.account]
        if (type == "hide") return@mapNotNull null
        val cat = txnRules[txnKey(t)]
            ?: (if (!isGeneric(t)) payeeRules[payeeKey(t)] else null)
            ?: t.category
        val card = when (type) { "card" -> true; "bank" -> false; else -> t.isCard }
        t.copy(category = cat, isCard = card, balanceAfter = if (card) null else t.balanceAfter)
    }

    fun categoryOptions(payeeRules: Map<String, String>, txnRules: Map<String, String>): List<String> =
        (Categorizer.names + listOf("Shops / Local stores", "Personal", "Family", "Business")
            + payeeRules.values + txnRules.values).distinct()
}
