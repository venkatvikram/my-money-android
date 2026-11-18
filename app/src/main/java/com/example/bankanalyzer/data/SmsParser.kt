package com.example.bankanalyzer.data

/**
 * Turns Indian bank / card / UPI transaction SMS into [Txn]s.
 * Works offline with plain pattern matching; nothing leaves the phone.
 */
object SmsParser {
    private val IC = RegexOption.IGNORE_CASE

    private val currencyAmount = Regex("(?:rs\\.?|inr|₹)\\s*:?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", IC)
    private val bareAmount = Regex("(?:debited|credited|deducted)\\s*(?:by|with|for|of)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", IC)
    private val balanceRe = Regex(
        "(?:avl\\.?\\s*bal(?:ance)?|avail(?:able)?\\.?\\s*bal(?:ance)?|a/c\\s*bal(?:ance)?|clr\\s*bal|\\bbal(?:ance)?)" +
            "\\s*(?:is|:|-|of)?\\s*(?:rs\\.?|inr|₹)?\\s*:?\\s*(-?[0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        IC
    )
    private val debitWord = Regex("\\b(debited|debit(?!\\s*card)|spent|withdrawn|paid|sent|purchase|deducted|dr\\.?)(?=\\W|$)", IC)
    private val creditWord = Regex("\\b(credited|received|deposited|added|refunded|refund|cr\\.?)(?=\\W|$)", IC)
    private val accountRe = Regex(
        "(?:a/?c|acct|account|card|\\bac)\\s*(?:no\\.?|number)?\\s*(?:ending(?:\\s*(?:with|in))?)?\\s*[:.]?\\s*[x*•.]*\\s*([0-9]{3,6})\\b",
        IC
    )
    private val maskedRe = Regex("[xX*]{2,}([0-9]{3,6})\\b")

    private val reject = Regex(
        "\\botp\\b|one time password|verification code|will be debited|to be debited|is due|due on|due date|" +
            "requested money|collect request|has requested|pre-?approved|\\boffer\\b|eligible|apply now|" +
            "\\bfailed\\b|\\bdeclined\\b|unsuccessful|insufficient|minimum amount due|total amount due",
        IC
    )
    private val cardRe = Regex("credit\\s*card|avl\\.?\\s*l(?:i)?m(?:i)?t|available\\s*limit|card\\s*(?:no\\.?\\s*)?[x*]+\\d+.*\\blimit", IC)

    private val nonBank = Regex("epfo|provident fund|\\buan\\b|passbook balance|\\bact(grp|fbr|net)?\\b|fibernet", IC)

    private val toRe =Regex("(?:\\bto\\b|\\bat\\b|towards|\\bfor\\b)\\s+(?:vpa\\s+)?([A-Za-z0-9@._&'\\- ]{3,40}?)(?=\\s+(?:on|ref|upi|via|avl|from|dated|thru|using|\\(|-)|[.,;:]|$)", IC)
    private val fromRe = Regex("(?:\\bfrom\\b|\\bby\\b)\\s+(?:vpa\\s+)?([A-Za-z0-9@._&'\\- ]{3,40}?)(?=\\s+(?:on|ref|upi|via|avl|to|dated|thru|\\(|-)|[.,;:]|$)", IC)
    private val infoRe = Regex("(?:info|remarks?)\\s*[:\\-]\\s*([^.;]{3,40})", IC)
    private val notAName = Regex("a/?c|acct|account|bank|card|\\bxx|\\*\\*|^\\d|your|\\byou\\b", IC)

    /** Returns null when the SMS is not a completed debit/credit. */
    fun parse(sender: String, body: String, time: Long): Txn? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        if (text.length < 15 || reject.containsMatchIn(text)) return null
        // Only real banks / cards / payment banks. Billers (ACT, Jio…) and EPFO also send
        // "payment received" / "credited" SMS that are not money in your bank account.
        if (nonBank.containsMatchIn(sender) || nonBank.containsMatchIn(text.take(80))) return null
        val knownBank = bankCodes.keys.any { sender.uppercase().contains(it) }
        if (!knownBank && !(Regex("\\bbank\\b", IC).containsMatchIn(text) && accountRe.containsMatchIn(text))) return null

        val dIdx = debitWord.find(text)?.range?.first ?: Int.MAX_VALUE
        val cIdx = creditWord.find(text)?.range?.first ?: Int.MAX_VALUE
        if (dIdx == Int.MAX_VALUE && cIdx == Int.MAX_VALUE) return null
        val credit = cIdx < dIdx

        val balMatch = balanceRe.find(text)
        val balance = balMatch?.groupValues?.get(1)?.let(::toNumber)

        val amountMatch = currencyAmount.findAll(text).firstOrNull { m ->
            balMatch == null || m.range.last < balMatch.range.first || m.range.first > balMatch.range.last
        } ?: bareAmount.find(text)
        val amount = amountMatch?.groupValues?.get(1)?.let(::toNumber) ?: return null
        if (amount <= 0.0) return null

        val digits = (accountRe.find(text)?.groupValues?.get(1) ?: maskedRe.find(text)?.groupValues?.get(1))
            ?.takeLast(4)
        val isCard = cardRe.containsMatchIn(text) && !credit
        val bank = bankName(sender)
        val account = buildString {
            append(bank)
            if (isCard) append(" Card")
            if (digits != null) append(" ••").append(digits)
        }

        val party = counterparty(text, credit)
        val category = Categorizer.categorize("$party $text", credit)

        return Txn(
            time = time,
            amount = amount,
            credit = credit,
            desc = party,
            category = category,
            account = account,
            isCard = isCard,
            balanceAfter = if (isCard) null else balance,
            source = Source.SMS,
            raw = text
        )
    }

    private fun counterparty(text: String, credit: Boolean): String {
        val order = if (credit) listOf(fromRe, infoRe, toRe) else listOf(toRe, infoRe, fromRe)
        for (re in order) {
            for (m in re.findAll(text)) {
                val v = m.groupValues[1].trim().trim('-', '.', ' ')
                if (v.length >= 3 && !notAName.containsMatchIn(v)) return v.take(32)
            }
        }
        return if (credit) "Money received" else "Payment"
    }

    fun toNumber(s: String): Double? =
        s.replace(",", "").trim().toDoubleOrNull()

    private val bankCodes = linkedMapOf(
        "HDFC" to "HDFC", "SBI" to "SBI", "ICICI" to "ICICI", "AXIS" to "Axis", "KOTAK" to "Kotak",
        "IDFC" to "IDFC First", "INDUS" to "IndusInd", "YES" to "Yes Bank", "PNB" to "PNB", "BOB" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda", "CANBNK" to "Canara", "CANARA" to "Canara", "UNION" to "Union Bank",
        "IOB" to "IOB", "INDBNK" to "Indian Bank", "FEDBNK" to "Federal", "FEDERAL" to "Federal", "RBL" to "RBL",
        "AUBANK" to "AU Bank", "PAYTM" to "Paytm", "AIRTEL" to "Airtel Payments", "CITI" to "Citi",
        "SCB" to "Std Chartered", "HSBC" to "HSBC", "KVB" to "KVB", "TMB" to "TMB", "CUB" to "City Union",
        "AMEX" to "Amex", "ONECRD" to "OneCard", "SLICE" to "Slice", "JUPITER" to "Jupiter", "FIBANK" to "Fi"
    )

    fun bankName(sender: String): String {
        val up = sender.uppercase()
        for ((code, name) in bankCodes) if (up.contains(code)) return name
        // "AX-ABCDEF-S" -> "ABCDEF"
        val parts = up.split('-').filter { it.length >= 4 }
        return parts.firstOrNull()?.take(8) ?: "Bank"
    }

    /** Bank SMS come from text sender IDs (e.g. "VM-HDFCBK"), never from plain phone numbers. */
    fun looksLikeBankSender(sender: String): Boolean =
        sender.isNotBlank() && !Regex("^\\+?[0-9 ]{7,}$").matches(sender)
}
