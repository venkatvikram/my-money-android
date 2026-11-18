package com.example.bankanalyzer.data

enum class Source { SMS, STATEMENT }

/** One money movement. amount is always positive; [credit] says which way it went. */
data class Txn(
    val time: Long,              // epoch millis
    val amount: Double,
    val credit: Boolean,         // true = money received, false = money spent
    val desc: String,            // counterparty / narration
    val category: String,
    val account: String,         // e.g. "HDFC ••1234", "Card ••5678", or "Statement"
    val isCard: Boolean = false, // credit-card spend (not part of bank balance)
    val balanceAfter: Double? = null, // "Avl Bal" reported with this txn, if any
    val source: Source,
    val raw: String = ""
)

data class AccountBalance(
    val account: String,
    val balance: Double?,        // null = no balance known yet
    val asOf: Long?,             // when the anchor balance was reported
    val fromManual: Boolean,
    val txnsSinceAnchor: Int
)
