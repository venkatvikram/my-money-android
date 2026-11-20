package com.example.bankanalyzer.data

import android.content.Context
import android.net.Uri
import kotlin.math.abs

object SmsReader {
    private const val DAYS_BACK = 730L

    /** Reads the SMS inbox on the phone (read-only) and returns parsed bank transactions, newest first. */
    fun load(context: Context): List<Txn> {
        val since = System.currentTimeMillis() - DAYS_BACK * 24 * 60 * 60 * 1000
        val out = ArrayList<Txn>()
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            "date > ?",
            arrayOf(since.toString()),
            "date DESC"
        ) ?: return emptyList()
        cursor.use { c ->
            if (c.getColumnIndex("address") < 0 || c.getColumnIndex("body") < 0 || c.getColumnIndex("date") < 0) return emptyList()
            val iA = c.getColumnIndex("address")
            val iB = c.getColumnIndex("body")
            val iD = c.getColumnIndex("date")
            while (c.moveToNext()) {
                val sender = c.getString(iA) ?: continue
                val body = c.getString(iB) ?: continue
                if (!SmsParser.looksLikeBankSender(sender)) continue
                // One unusual message must never stop the rest from loading.
                try { SmsParser.parse(sender, body, c.getLong(iD))?.let(out::add) } catch (_: Exception) { }
            }
        }
        return dedupe(out)
    }

    /** Banks often send the same alert twice (or SMS + UPI app); keep one per account/amount within 3 minutes. */
    private fun dedupe(list: List<Txn>): List<Txn> {
        val kept = ArrayList<Txn>()
        for (t in list.sortedByDescending { it.time }) {
            val dup = kept.asReversed().takeWhile { abs(it.time - t.time) < 3 * 60_000 }.any {
                it.credit == t.credit && abs(it.amount - t.amount) < 0.005 && it.account == t.account
            }
            if (!dup) kept.add(t)
        }
        return kept
    }
}
