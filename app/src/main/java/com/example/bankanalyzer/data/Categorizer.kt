package com.example.bankanalyzer.data

object Categorizer {
    private val rules: List<Pair<String, Regex>> = listOf(
        "Salary / Income" to Regex("salary|slry|stipend|interest|int\\.pd|refund|cashback|dividend|reversal", RegexOption.IGNORE_CASE),
        "Credit card bill" to Regex("credit\\s*card\\s*(bill|payment)|cc\\s*payment|card\\s*payment|\\bcred\\b", RegexOption.IGNORE_CASE),
        "Rent" to Regex("\\brent\\b", RegexOption.IGNORE_CASE),
        "Loan / EMI" to Regex("\\bemi\\b|loan|finance|lending|nbfc|bajaj", RegexOption.IGNORE_CASE),
        "Fuel / Transport" to Regex("fuel|petrol|diesel|hpcl|iocl|bpcl|\\btoll|fastag|uber|\\bola\\b|rapido|irctc|redbus|metro|travel", RegexOption.IGNORE_CASE),
        "Groceries" to Regex("super\\s*market|\\bmart\\b|grocer|kirana|bazaar|dmart|blinkit|zepto|bigbasket|instamart|reliance fresh|vegetable|fruit", RegexOption.IGNORE_CASE),
        "Food / Dining" to Regex("restauran|hotel|cafe|\\btea\\b|bakery|sweets|swiggy|zomato|food|snack|tiffin|biryani|pizza|kfc|mcdonald|domino", RegexOption.IGNORE_CASE),
        "Shopping" to Regex("amazon|flipkart|myntra|ajio|meesho|nykaa|shopping|retail|store", RegexOption.IGNORE_CASE),
        "Bills / Recharge" to Regex("electric|\\beb\\b|tneb|bescom|water|\\bgas\\b|broadband|wifi|recharge|\\bjio\\b|airtel|\\bvi\\b|vodafone|\\bdth\\b|postpaid|\\bbill", RegexOption.IGNORE_CASE),
        "Entertainment" to Regex("netflix|prime video|hotstar|spotify|bookmyshow|pvr|inox|movie|youtube", RegexOption.IGNORE_CASE),
        "Medical" to Regex("pharma|medical|hospital|clinic|apollo|medplus|chemist|diagnos", RegexOption.IGNORE_CASE),
        "Education" to Regex("school|college|university|course|tuition|\\bexam|\\bfees?\\b", RegexOption.IGNORE_CASE),
        "Investments" to Regex("zerodha|groww|upstox|indmoney|mutual|\\bsip\\b|demat|\\bnps\\b|\\bppf\\b|insurance|\\blic\\b", RegexOption.IGNORE_CASE),
        "ATM Cash" to Regex("\\batm\\b|cash\\s*wdl|cwdr|cash withdrawal|withdrawn at", RegexOption.IGNORE_CASE),
        "Transfers" to Regex("\\bupi\\b|imps|neft|rtgs|p2a|p2p|transfer|\\btrf\\b|vpa", RegexOption.IGNORE_CASE),
    )

    val names: List<String> get() = rules.map { it.first } + listOf("Received (transfer)", "Other income", "Other")

    fun categorize(text: String, credit: Boolean): String {
        for ((name, re) in rules) {
            if (re.containsMatchIn(text)) {
                // An incoming UPI/NEFT is income from someone, not a "transfer" spend.
                if (credit && name == "Transfers") return "Received (transfer)"
                return name
            }
        }
        return if (credit) "Other income" else "Other"
    }
}
