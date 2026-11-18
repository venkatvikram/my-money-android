# My Money — offline finance tracker for Android

Tracks your spending and bank balance from bank SMS, analyses bank statements, and shows your net worth. **Everything runs on your phone, and the app has no internet permission.**

## Privacy

The app asks for **one permission: Read SMS**. The build strips every other sensitive permission, so it has no internet, location, microphone, call, contacts or camera access, and it can't send SMS.

- Your data stays in the app's private storage on the phone. Phone backup is turned off for this app.
- Nothing about any particular user is built in. Accounts, balances, categories and assets all come from your own SMS and what you type.

## Features

**Home**
- **Net worth:** bank balance plus mutual funds, gold and other savings.
- **Current balance:** the latest "Avl Bal" from your bank SMS, plus every debit and credit since. You can also type a balance in yourself.
- **This month and today:** money spent, money received, and net.
- **Accounts:** mark each one as a bank account, a credit card, or hidden.
- **Gold:** enter the grams and today's price per gram.

**Transactions**
- Every debit and credit found in your bank, card and UPI SMS.
- Tap a payment to set its category, such as "Shop". The app remembers that payee for all past and future payments.

**Analysis**
- Choose a period: this month, last month, 3 months, this year, or all.
- KPIs: money in, money out, net savings, savings rate, average spend per day, biggest expense and top category.
- Spending by category: tap a category to see its payees and payments.
- Monthly in/out chart with a table of totals.
- Top payees and written insights.

**Statement**
- Import a statement exported from net banking as `.xlsx`, `.csv` or HTML-style `.xls`, and get the same KPIs and the closing balance.

It understands SMS formats from most Indian banks, including HDFC, SBI, ICICI, Axis, Kotak, Federal, IOB and others. It ignores OTPs, reminders, offers, failed transactions, biller receipts and EPFO messages.

## Install

1. Download the APK from **[Releases](../../releases)**.
2. Open it on your phone and allow "Install unknown apps" for your browser or file manager.
3. Open **My Money** and tap **Allow SMS access**.

> **If Android blocks the install or the SMS permission:** Android 13+ restricts sideloaded apps that ask for SMS access, and in India Google Play Protect may block such installs outright.
> - If SMS permission is greyed out: open **Settings → Apps → My Money → ⋮ → Allow restricted settings**, then grant SMS.
> - If Play Protect blocks the install: install over USB or Wi-Fi debugging with `adb install MyMoney.apk`.

## Build from source

You need Android Studio, which includes JDK 21+ and Android SDK 37.

```bash
./gradlew assembleRelease      # app/build/outputs/apk/release/
./gradlew testDebugUnitTest    # SMS & statement parser tests
```

- Code lives in `app/src/main/java/com/example/bankanalyzer/`. `data/` holds the parsers, analytics and storage; `ui/` holds the Compose screens.
- To add support for a new bank's SMS format, add a sample message to `ParserTest.kt` and adjust `SmsParser.kt`.

## Contributing

Issues and pull requests are welcome. When you share an SMS sample, **remove the account numbers, names and amounts first**.

## License

[MIT](LICENSE)
