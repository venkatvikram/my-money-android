package com.example.bankanalyzer

import com.example.bankanalyzer.data.Analytics
import com.example.bankanalyzer.data.SmsParser
import com.example.bankanalyzer.data.StatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    private val now = 1_760_000_000_000L

    @Test fun hdfcUpiSent() {
        val t = SmsParser.parse("AX-HDFCBK-S", "Sent Rs.250.00 From HDFC Bank A/C *4521 To SWIGGY On 05/11/26 Ref 123456789012 Not You? Call 18002586161", now)!!
        assertFalse(t.credit); assertEquals(250.0, t.amount, 0.001); assertEquals("HDFC ••4521", t.account)
        assertEquals("SWIGGY", t.desc); assertEquals("Food / Dining", t.category)
    }

    @Test fun sbiDebitWithBalance() {
        val t = SmsParser.parse("VM-SBIUPI", "Dear UPI user A/C X7788 debited by 1200.0 on date 04Nov26 trf to RAMESH KUMAR Refno 530812345678. If not u? call 1800111109. -SBI", now)!!
        assertFalse(t.credit); assertEquals(1200.0, t.amount, 0.001); assertEquals("SBI ••7788", t.account)
    }

    @Test fun creditWithAvlBal() {
        val t = SmsParser.parse("JD-ICICIT", "ICICI Bank Acct XX903 credited with Rs 45,000.00 on 01-Nov-26 from ACME PVT LTD. IMPS:530512. Avl Bal Rs 62,310.50", now)!!
        assertTrue(t.credit); assertEquals(45000.0, t.amount, 0.001); assertEquals(62310.50, t.balanceAfter!!, 0.001)
    }

    @Test fun axisDebitAmountNotBalance() {
        val t = SmsParser.parse("BZ-AXISBK", "INR 3,499.00 debited from A/c no. XX1122 on 02-11-26 at AMAZON. Avl Bal INR 10,001.00", now)!!
        assertEquals(3499.0, t.amount, 0.001); assertEquals(10001.0, t.balanceAfter!!, 0.001); assertEquals("Shopping", t.category)
    }

    @Test fun cardSpend() {
        val t = SmsParser.parse("VM-HDFCBK", "Rs.1,500 spent on HDFC Bank Credit Card xx9988 at DMART on 2026-11-03. Avl Lmt Rs 48,500", now)!!
        assertTrue(t.isCard); assertNull(t.balanceAfter); assertEquals("Groceries", t.category)
    }

    @Test fun ignoresOtpAndPromos() {
        assertNull(SmsParser.parse("AX-HDFCBK", "OTP 123456 for txn of Rs 500 at AMAZON. Do not share.", now))
        assertNull(SmsParser.parse("AX-HDFCBK", "Your EMI of Rs 2,000 will be debited on 10th Nov.", now))
        assertNull(SmsParser.parse("AX-HDFCBK", "Get a pre-approved loan of Rs 5,00,000. Apply now!", now))
        assertNull(SmsParser.parse("AX-HDFCBK", "John has requested money of Rs 100 on Google Pay", now))
    }

    @Test fun ignoresBillersAndEpfo() {
        assertNull(SmsParser.parse("VM-ACTGRP", "Dear Customer, payment of Rs.1,179.00 received for ACT account 1234567. Thank you.", now))
        assertNull(SmsParser.parse("AD-EPFOHO", "Dear Member, contribution of Rs 3,600 for due month 102026 has been received. Passbook Balance against XXXXXX8421 is Rs 1,20,000", now))
        assertNull(SmsParser.parse("JM-JIOPAY", "Payment of Rs 299 received for Jio number 98xxxxxx. Thank you.", now))
    }

    @Test fun phoneNumbersNotBankSenders() {
        assertFalse(SmsParser.looksLikeBankSender("+919876543210"))
        assertTrue(SmsParser.looksLikeBankSender("VM-SBIUPI"))
    }

    @Test fun csvStatementDebitCredit() {
        val csv = """
            HDFC BANK Statement of account
            Name: TEST
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/11/26,UPI-SWIGGY-swiggy@icici-ICIC0001,0001,01/11/26,250.00,,9750.00
            02/11/26,NEFT CR-ACME PVT LTD-SALARY,0002,02/11/26,,50000.00,59750.00
            03/11/26,ATM CASH WDL,0003,03/11/26,"2,000.00",,57750.00
            ,Closing balance,,,,,57750.00
        """.trimIndent()
        val tx = StatementParser.parse(csv.toByteArray())
        assertEquals(3, tx.size)
        val k = Analytics.compute(tx)
        assertEquals(50000.0, k.moneyIn, 0.01); assertEquals(2250.0, k.moneyOut, 0.01)
        assertEquals("ATM Cash", k.categories.first().first)
    }

    @Test fun csvSignedWithDrCr() {
        val csv = "Txn Date;Description;Amount;Dr/Cr;Balance\n05-Nov-2026;POS NETFLIX;649.00;DR;1000\n06-Nov-2026;INTEREST;12.5;CR;1012.5\n"
        val tx = StatementParser.parse(csv.toByteArray())
        assertEquals(2, tx.size); assertFalse(tx[0].credit); assertTrue(tx[1].credit)
    }

    @Test fun userRulesAndAccountTypes() {
        val shop = SmsParser.parse("AX-HDFCBK-S", "Sent Rs.120.00 From HDFC Bank A/C *1111 To RAJU STORES On 05/11/26 Ref 1", now)!!
        val shop2 = shop.copy(time = now + 86_400_000, amount = 80.0)
        val card = SmsParser.parse("BZ-AXISBK", "INR 999.00 debited from A/c no. XX2222 on 02-11-26 at AMAZON. Avl Bal INR 5,000.00", now)!!
        val out = com.example.bankanalyzer.data.Overrides.apply(
            listOf(shop, shop2, card),
            payeeRules = mapOf("raju stores" to "Shops / Local stores"),
            txnRules = emptyMap(),
            types = mapOf(card.account to "card")
        )
        assertEquals("Shops / Local stores", out[0].category)
        assertEquals("Shops / Local stores", out[1].category) // learned rule applies to the next payment too
        assertTrue(out[2].isCard); assertNull(out[2].balanceAfter)
        val hidden = com.example.bankanalyzer.data.Overrides.apply(listOf(card), emptyMap(), emptyMap(), mapOf(card.account to "hide"))
        assertTrue(hidden.isEmpty())
    }

    @Test fun goldWorth() {
        val g = com.example.bankanalyzer.data.Asset(1, "GOLD", "Coins", grams = 10.5, pricePerGram = 7000.0)
        assertEquals(73500.0, g.worth(), 0.01)
    }

    @Test fun balanceAppliesLaterSms() {
        val a = SmsParser.parse("JD-ICICIT", "ICICI Bank Acct XX903 credited with Rs 1,000.00 from X. Avl Bal Rs 5,000.00", now)!!
        val b = SmsParser.parse("JD-ICICIT", "ICICI Bank Acct XX903 debited for Rs 300.00 on 01-Nov-26; SHOP credited.", now + 60_000)!!
        val bal = Analytics.balances(listOf(b, a), emptyMap())
        assertNotNull(bal.first().balance); assertEquals(4700.0, bal.first().balance!!, 0.01)
    }
}
