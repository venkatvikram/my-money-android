package com.example.bankanalyzer.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.zip.ZipInputStream

/** Reads bank statements exported as CSV / TSV, Excel (.xlsx) or HTML-style ".xls" — fully offline. */
object StatementParser {

    class StatementException(msg: String) : Exception(msg)

    fun parse(bytes: ByteArray): List<Txn> {
        val rows = readRows(bytes).map { r -> r.map { it.replace(' ', ' ').trim() } }
            .filter { r -> r.any { it.isNotEmpty() } }
        if (rows.isEmpty()) throw StatementException("The file is empty.")
        return toTxns(rows)
    }

    // ---------- file formats ----------

    private fun readRows(b: ByteArray): List<List<String>> {
        if (b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()) return readXlsx(b)
        if (b.size >= 4 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte())
            throw StatementException("This is an old Excel 97-2003 (.xls) file. Open it in Excel or Google Sheets and save it as .xlsx or CSV, then import again.")
        if (b.size >= 4 && String(b, 0, 4) == "%PDF")
            throw StatementException("PDF statements can't be read offline. Download the statement as Excel or CSV from your bank's net banking instead.")
        var text = String(b, Charsets.UTF_8)
        if (text.startsWith("﻿")) text = text.substring(1)
        if (Regex("<t[rd][\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(text)) return readHtml(text)
        return readDelimited(text)
    }

    private fun readDelimited(text: String): List<List<String>> {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(30).joinToString("\n")
        val delim = listOf(',', '\t', ';', '|').maxByOrNull { d -> sample.count { it == d } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                delim -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    private fun readHtml(html: String): List<List<String>> {
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val tr = Regex("<tr[^>]*>(.*?)</tr>", opts)
        val td = Regex("<t[dh][^>]*>(.*?)</t[dh]>", opts)
        return tr.findAll(html).map { r ->
            td.findAll(r.groupValues[1]).map { c ->
                c.groupValues[1].replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&#39;", "'").replace("&quot;", "\"").replace(Regex("\\s+"), " ").trim()
            }.toList()
        }.toList()
    }

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (n == "xl/sharedStrings.xml" || n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" ||
                    (n.startsWith("xl/worksheets/") && n.endsWith(".xml"))
                ) files[n] = z.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings) ?: emptyList()
        val sheetPath = firstSheetPath(files) ?: throw StatementException("No worksheet found in this Excel file.")
        return sheetRows(files[sheetPath]!!, shared)
    }

    private fun parser(b: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(b), "UTF-8") }

    private fun sharedStrings(b: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = parser(b)
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.clear() }
                    "t" -> inT = inSi
                }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "si" -> { inSi = false; out.add(sb.toString()) }
                }
            }
        }
        return out
    }

    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        try {
            var firstRid: String? = null
            files["xl/workbook.xml"]?.let { b ->
                val p = parser(b)
                while (p.next() != XmlPullParser.END_DOCUMENT && firstRid == null) {
                    if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                        for (i in 0 until p.attributeCount) if (p.getAttributeName(i).endsWith("id") && p.getAttributeName(i) != "sheetId") firstRid = p.getAttributeValue(i)
                    }
                }
            }
            val rels = files["xl/_rels/workbook.xml.rels"]
            if (firstRid != null && rels != null) {
                val p = parser(rels)
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship" && p.getAttributeValue(null, "Id") == firstRid) {
                        val t = p.getAttributeValue(null, "Target").removePrefix("/").removePrefix("xl/")
                        val path = "xl/$t"
                        if (files.containsKey(path)) return path
                    }
                }
            }
        } catch (_: Exception) { }
        return files.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull()
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break }
        return n - 1
    }

    private fun sheetRows(b: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val p = parser(b)
        var row: ArrayList<String>? = null
        var col = 0
        var type: String? = null
        val v = StringBuilder()
        var inValue = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = ArrayList()
                    "c" -> {
                        val r = p.getAttributeValue(null, "r")
                        col = if (r != null) colIndex(r) else (row?.size ?: 0)
                        type = p.getAttributeValue(null, "t"); v.clear()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) v.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> row?.let { rr ->
                        val raw = v.toString()
                        val value = if (type == "s") raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: "" else raw
                        while (rr.size < col) rr.add("")
                        if (rr.size == col) rr.add(value) else rr[col] = value
                    }
                    "row" -> { row?.let(rows::add); row = null }
                }
            }
        }
        return rows
    }

    // ---------- columns & rows -> transactions ----------

    private val IC = RegexOption.IGNORE_CASE
    private val headerHint = Regex("date|desc|narrat|particular|debit|credit|withdraw|deposit|amount|balance|chq|cheque|ref|remark", IC)
    private val typeHdr = Regex("cr\\s*/\\s*dr|dr\\s*/\\s*cr|\\btype\\b|dr\\s*cr", IC)

    private fun toTxns(rows: List<List<String>>): List<Txn> {
        var hi = -1; var best = 1
        for (i in 0 until minOf(rows.size, 40)) {
            val sc = rows[i].count { it.length < 40 && headerHint.containsMatchIn(it) }
            if (sc > best) { best = sc; hi = i }
        }
        if (hi < 0) throw StatementException("Couldn't find the header row (Date, Narration, Debit, Credit…). Is this a bank statement?")
        val head = rows[hi]
        fun find(re: Regex, exclude: Regex? = null, skip: Set<Int> = emptySet()): Int =
            head.indices.firstOrNull { i -> i !in skip && re.containsMatchIn(head[i]) && (exclude == null || !exclude.containsMatchIn(head[i])) } ?: -1

        val cDate = find(Regex("(txn|tran|transaction|posting).*date", IC)).takeIf { it >= 0 } ?: find(Regex("date", IC), Regex("value", IC)).takeIf { it >= 0 } ?: find(Regex("date", IC))
        val cDesc = find(Regex("narrat|particular|desc|remark|detail", IC))
        val cType = find(typeHdr)
        val cDr = find(Regex("withdraw|debit|\\bdr\\b", IC), typeHdr)
        val cCr = find(Regex("deposit|credit|\\bcr\\b", IC), typeHdr)
        val cAmt = find(Regex("amount|\\bamt\\b", IC), Regex("withdraw|deposit|debit|credit|balance", IC))
        val cBal = find(Regex("balance|\\bbal\\b", IC))
        if (cDate < 0) throw StatementException("Couldn't find a Date column.")
        if (cDr < 0 && cCr < 0 && cAmt < 0) throw StatementException("Couldn't find Debit/Credit or Amount columns.")

        val out = ArrayList<Txn>()
        for (r in rows.drop(hi + 1)) {
            val t = parseDate(r.getOrNull(cDate) ?: "") ?: continue
            val desc = (if (cDesc >= 0) r.getOrNull(cDesc) else null)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            var inAmt = 0.0; var outAmt = 0.0
            if (cDr >= 0 || cCr >= 0) {
                outAmt = if (cDr >= 0) num(r.getOrNull(cDr)) ?: 0.0 else 0.0
                inAmt = if (cCr >= 0) num(r.getOrNull(cCr)) ?: 0.0 else 0.0
                outAmt = kotlin.math.abs(outAmt); inAmt = kotlin.math.abs(inAmt)
            } else {
                val cellText = r.getOrNull(cAmt).orEmpty()
                val v = num(cellText) ?: 0.0
                val typeTxt = (if (cType >= 0) r.getOrNull(cType) else null).orEmpty() + " " + cellText
                val isCr = Regex("\\bcr\\b|credit", IC).containsMatchIn(typeTxt)
                val isDr = Regex("\\bdr\\b|debit", IC).containsMatchIn(typeTxt)
                when {
                    isCr && !isDr -> inAmt = kotlin.math.abs(v)
                    isDr && !isCr -> outAmt = kotlin.math.abs(v)
                    v >= 0 -> inAmt = v
                    else -> outAmt = -v
                }
            }
            if (inAmt == 0.0 && outAmt == 0.0) continue
            val credit = inAmt > 0.0 && outAmt == 0.0
            val amount = if (credit) inAmt else outAmt
            out.add(
                Txn(
                    time = t, amount = amount, credit = credit,
                    desc = payee(desc), category = Categorizer.categorize(desc, credit),
                    account = "Statement",
                    balanceAfter = if (cBal >= 0) num(r.getOrNull(cBal)) else null,
                    source = Source.STATEMENT, raw = desc
                )
            )
        }
        if (out.isEmpty()) throw StatementException("No transactions found under the header row.")
        return out
    }

    fun num(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        var x = s.replace(Regex("(?i)rs\\.?|inr|₹|,|\\s"), "")
        x = x.replace(Regex("(?i)(cr|dr)$"), "")
        val neg = x.startsWith("(") && x.endsWith(")")
        x = x.trim('(', ')')
        val v = x.toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun parseDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        fun mk(y0: Int, m: Int, d: Int): Long? {
            val y = if (y0 < 100) 2000 + y0 else y0
            if (m !in 1..12 || d !in 1..31 || y !in 1990..2100) return null
            return Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis
        }
        // Excel serial number (days since 1899-12-30)
        s.toDoubleOrNull()?.let { n ->
            if (n in 20000.0..80000.0) {
                val c = Calendar.getInstance().apply { clear(); set(1899, Calendar.DECEMBER, 30, 12, 0, 0); add(Calendar.DAY_OF_MONTH, n.toInt()) }
                return c.timeInMillis
            }
            return null
        }
        Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").find(s)?.let { val g = it.groupValues; return mk(g[1].toInt(), g[2].toInt(), g[3].toInt()) }
        Regex("^(\\d{1,2})[-/. ](\\d{1,2})[-/. ](\\d{2,4})").find(s)?.let {
            val g = it.groupValues
            var d = g[1].toInt(); var m = g[2].toInt()
            if (m > 12 && d <= 12) { val t = d; d = m; m = t }
            return mk(g[3].toInt(), m, d)
        }
        Regex("^(\\d{1,2})[-/. ]*([A-Za-z]{3,9})[-/., ]*(\\d{2,4})").find(s)?.let {
            val g = it.groupValues
            val m = months.indexOf(g[2].take(3).lowercase()) + 1
            if (m > 0) return mk(g[3].toInt(), m, g[1].toInt())
        }
        Regex("^([A-Za-z]{3,9})[-/. ]*(\\d{1,2})[,]?[-/. ]*(\\d{2,4})").find(s)?.let {
            val g = it.groupValues
            val m = months.indexOf(g[1].take(3).lowercase()) + 1
            if (m > 0) return mk(g[3].toInt(), m, g[2].toInt())
        }
        return null
    }

    private val noise = Regex("bank|ltd|limited|upi|payment|ybl|okaxis|okhdfc|oksbi|okicici|paytm|ibl|axl|hdfc|icic|sbi|axis|neft|imps|rtgs|^\\d+$", IC)

    fun payee(d: String): String {
        val s = d.replace(Regex("^(UPI|IMPS|NEFT|RTGS|P2M|P2A|MMT|POS|ACH|BIL|INB)[/:\\- ]+", IC), "")
        for (part in s.split('/', ':', '-', '*')) {
            val w = part.trim()
            if (Regex("[A-Za-z]{3,}").containsMatchIn(w) && !noise.containsMatchIn(w)) return w.take(28)
        }
        return d.take(28).ifBlank { "—" }
    }
}
