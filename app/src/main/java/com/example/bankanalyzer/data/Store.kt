package com.example.bankanalyzer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Something you own that isn't in a bank account. Values are typed in by the user. */
data class Asset(
    val id: Long,
    val kind: String,            // "MF" mutual fund, "GOLD", "OTHER"
    val name: String,
    val value: Double = 0.0,     // MF / OTHER: current value in ₹
    val grams: Double = 0.0,     // GOLD
    val pricePerGram: Double = 0.0, // GOLD: today's price per gram in ₹
    val updated: Long = System.currentTimeMillis(),
) {
    fun worth(): Double = if (kind == "GOLD") grams * pricePerGram else value
}

/**
 * All user data lives in the app's private storage on the phone.
 * Nothing is hard-coded for a particular person — every account, rule and asset comes from the
 * user's own SMS or what they type in, so the same app works for anyone.
 */
class Store(private val context: Context) {
    private val stmtFile get() = File(context.filesDir, "statement.json")
    private val prefs get() = context.getSharedPreferences("money", Context.MODE_PRIVATE)

    // ---------- statement ----------

    fun saveStatement(name: String, txns: List<Txn>) {
        val arr = JSONArray()
        for (t in txns) arr.put(JSONObject().apply {
            put("t", t.time); put("a", t.amount); put("c", t.credit); put("d", t.desc)
            put("g", t.category); put("r", t.raw); t.balanceAfter?.let { put("b", it) }
        })
        stmtFile.writeText(JSONObject().put("name", name).put("txns", arr).toString())
    }

    fun loadStatement(): Pair<String, List<Txn>>? {
        if (!stmtFile.exists()) return null
        return try {
            val o = JSONObject(stmtFile.readText())
            val arr = o.getJSONArray("txns")
            val list = (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                Txn(
                    time = j.getLong("t"), amount = j.getDouble("a"), credit = j.getBoolean("c"),
                    desc = j.optString("d"), category = j.optString("g"), account = "Statement",
                    balanceAfter = if (j.has("b")) j.getDouble("b") else null,
                    source = Source.STATEMENT, raw = j.optString("r")
                )
            }
            o.optString("name", "Statement") to list
        } catch (_: Exception) { null }
    }

    fun clearStatement() { stmtFile.delete() }

    // ---------- balances typed in by the user ----------

    fun manualBalances(): Map<String, Pair<Double, Long>> {
        val o = obj("manual")
        return o.keys().asSequence().associateWith { k ->
            val j = o.getJSONObject(k); j.getDouble("v") to j.getLong("t")
        }
    }

    fun setManualBalance(account: String, value: Double?) {
        val o = obj("manual")
        if (value == null) o.remove(account)
        else o.put(account, JSONObject().put("v", value).put("t", System.currentTimeMillis()))
        prefs.edit().putString("manual", o.toString()).apply()
    }

    // ---------- simple string maps ----------

    /** account -> "bank" | "card" | "hide" */
    fun accountTypes(): Map<String, String> = map("acctType")
    fun setAccountType(account: String, type: String?) = putMap("acctType", account, type)

    /** payee (lower-case) -> category, learned when the user re-categorises a payment */
    fun payeeRules(): Map<String, String> = map("payeeRules")
    fun setPayeeRule(payeeKey: String, category: String?) = putMap("payeeRules", payeeKey, category)

    /** single transaction -> category (for payments without a usable payee name) */
    fun txnRules(): Map<String, String> = map("txnRules")
    fun setTxnRule(txnKey: String, category: String?) = putMap("txnRules", txnKey, category)

    // ---------- assets ----------

    fun assets(): List<Asset> = try {
        val arr = JSONArray(prefs.getString("assets", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            Asset(
                id = j.getLong("id"), kind = j.getString("k"), name = j.optString("n"),
                value = j.optDouble("v", 0.0), grams = j.optDouble("g", 0.0),
                pricePerGram = j.optDouble("p", 0.0), updated = j.optLong("u", 0L)
            )
        }
    } catch (_: Exception) { emptyList() }

    fun saveAssets(list: List<Asset>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject().put("id", a.id).put("k", a.kind).put("n", a.name).put("v", a.value)
                    .put("g", a.grams).put("p", a.pricePerGram).put("u", a.updated)
            )
        }
        prefs.edit().putString("assets", arr.toString()).apply()
    }

    // ---------- helpers ----------

    private fun obj(name: String): JSONObject = try { JSONObject(prefs.getString(name, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    private fun map(name: String): Map<String, String> {
        val o = obj(name)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    private fun putMap(name: String, key: String, value: String?) {
        val o = obj(name)
        if (value == null) o.remove(key) else o.put(key, value)
        prefs.edit().putString(name, o.toString()).apply()
    }
}
