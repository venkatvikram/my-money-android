package com.example.bankanalyzer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bankanalyzer.data.Analytics
import com.example.bankanalyzer.data.Asset
import com.example.bankanalyzer.data.Overrides
import com.example.bankanalyzer.data.SmsReader
import com.example.bankanalyzer.data.StatementParser
import com.example.bankanalyzer.data.Store
import com.example.bankanalyzer.data.Txn
import com.example.bankanalyzer.ui.AccountDialog
import com.example.bankanalyzer.ui.AnalysisScreen
import com.example.bankanalyzer.ui.AssetDialog
import com.example.bankanalyzer.ui.CategoryDialog
import com.example.bankanalyzer.ui.HomeScreen
import com.example.bankanalyzer.ui.StatementScreen
import com.example.bankanalyzer.ui.TransactionsScreen
import com.example.bankanalyzer.ui.theme.BankAnalyzerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // Bumped every time the app comes back to the foreground, so new SMS are picked up.
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BankAnalyzerTheme { MoneyApp(resumeTick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

private fun hasSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

private fun displayName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "statement"

@Composable
fun MoneyApp(resumeTick: Int) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(hasSms(ctx)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var showSettingsHint by remember { mutableStateOf(false) }
    var rawSms by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    // Everything the user has taught the app (all stored on the phone).
    var manual by remember { mutableStateOf(store.manualBalances()) }
    var types by remember { mutableStateOf(store.accountTypes()) }
    var payeeRules by remember { mutableStateOf(store.payeeRules()) }
    var txnRules by remember { mutableStateOf(store.txnRules()) }
    var assets by remember { mutableStateOf(store.assets()) }

    val saved = remember { store.loadStatement() }
    var stmtName by remember { mutableStateOf(saved?.first) }
    var stmtRaw by remember { mutableStateOf(saved?.second ?: emptyList()) }
    var stmtError by remember { mutableStateOf<String?>(null) }
    var stmtBusy by remember { mutableStateOf(false) }

    // Dialogs
    var crashReport by remember { mutableStateOf(CrashReporter.pending(ctx)) }
    var catTxn by remember { mutableStateOf<Txn?>(null) }
    var acctDialog by remember { mutableStateOf<String?>(null) }
    var assetEdit by remember { mutableStateOf<Pair<Asset, Boolean>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
        showSettingsHint = !ok && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            stmtBusy = true; stmtError = null
            try {
                val name = displayName(ctx, uri)
                val list = withContext(Dispatchers.IO) {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw StatementParser.StatementException("Couldn't open the file.")
                    StatementParser.parse(bytes).also { store.saveStatement(name, it) }
                }
                stmtName = name; stmtRaw = list
            } catch (e: StatementParser.StatementException) {
                stmtError = e.message
            } catch (e: Exception) {
                stmtError = "Couldn't read this file (${e.javaClass.simpleName}). Try exporting the statement as CSV."
            } finally {
                stmtBusy = false
            }
        }
    }

    LaunchedEffect(resumeTick) {
        granted = hasSms(ctx)
        if (granted) showSettingsHint = false
    }
    LaunchedEffect(granted, resumeTick, reload) {
        if (!granted) return@LaunchedEffect
        loading = true
        rawSms = withContext(Dispatchers.IO) { runCatching { SmsReader.load(ctx) }.getOrDefault(emptyList()) }
        loading = false
    }

    // Derived views with the user's rules applied
    val sms = remember(rawSms, payeeRules, txnRules, types) { Overrides.apply(rawSms, payeeRules, txnRules, types) }
    val stmt = remember(stmtRaw, payeeRules, txnRules) { Overrides.apply(stmtRaw, payeeRules, txnRules, emptyMap()) }
    val allAccounts = remember(rawSms, types) { (rawSms.map { it.account } + types.keys).distinct().sorted() }
    fun typeOf(acc: String) = types[acc] ?: Overrides.defaultType(rawSms, acc)
    val cards = allAccounts.filter { typeOf(it) == "card" }
    val hidden = allAccounts.filter { typeOf(it) == "hide" }
    val balances = remember(sms, manual, types) { Analytics.balances(sms, manual, (cards + hidden).toSet()) }
    val categoryOptions = remember(payeeRules, txnRules) { Overrides.categoryOptions(payeeRules, txnRules) }
    val onTxnClick: (Txn) -> Unit = { catTxn = it }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("🏠" to "Home", "📋" to "Transactions", "📊" to "Analysis", "📄" to "Statement").forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(icon) }, label = { Text(label) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(
                    granted = granted,
                    showSettingsHint = showSettingsHint && askedOnce,
                    loading = loading,
                    sms = sms,
                    balances = balances,
                    cardAccounts = cards,
                    hiddenAccounts = hidden,
                    assets = assets,
                    onAllow = { permLauncher.launch(Manifest.permission.READ_SMS) },
                    onOpenSettings = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    },
                    onRefresh = { reload++ },
                    onAccountClick = { acctDialog = it },
                    onAssetClick = { assetEdit = it to false },
                    onAddAsset = { kind -> assetEdit = Asset(id = System.currentTimeMillis(), kind = kind, name = "") to true },
                    onTxnClick = onTxnClick,
                )
                1 -> TransactionsScreen(sms, granted, onTxnClick)
                2 -> AnalysisScreen(sms, onTxnClick)
                else -> StatementScreen(
                    name = stmtName, txns = stmt, error = stmtError, busy = stmtBusy,
                    onImport = {
                        picker.launch(
                            arrayOf(
                                "text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel", "text/html", "application/octet-stream"
                            )
                        )
                    },
                    onClear = { store.clearStatement(); stmtName = null; stmtRaw = emptyList(); stmtError = null },
                    onTxnClick = onTxnClick,
                )
            }
        }
    }

    catTxn?.let { t ->
        CategoryDialog(
            txn = t, options = categoryOptions,
            onSave = { cat, forPayee ->
                if (forPayee) {
                    store.setPayeeRule(Overrides.payeeKey(t), cat)
                    store.setTxnRule(Overrides.txnKey(t), null)
                } else store.setTxnRule(Overrides.txnKey(t), cat)
                payeeRules = store.payeeRules(); txnRules = store.txnRules(); catTxn = null
            },
            onDismiss = { catTxn = null }
        )
    }

    acctDialog?.let { acc ->
        AccountDialog(
            account = acc, currentType = typeOf(acc),
            onSave = { type, bal, reset ->
                store.setAccountType(acc, type)
                if (type == "bank" && bal != null) store.setManualBalance(acc, bal)
                if (reset) store.setManualBalance(acc, null)
                types = store.accountTypes(); manual = store.manualBalances(); acctDialog = null
            },
            onDismiss = { acctDialog = null }
        )
    }

    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { CrashReporter.clear(ctx); crashReport = null },
            title = { Text("My Money closed unexpectedly") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Sorry about that. You can share this report with the developer so it can be fixed. " +
                            "It contains only technical details (phone model, Android version, error), no SMS or money data.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(report, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "My Money crash report")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    try { ctx.startActivity(Intent.createChooser(send, "Share crash report")) } catch (_: Exception) { }
                    CrashReporter.clear(ctx); crashReport = null
                }) { Text("Share report") }
            },
            dismissButton = { TextButton(onClick = { CrashReporter.clear(ctx); crashReport = null }) { Text("Close") } }
        )
    }

    assetEdit?.let { (a, isNew) ->
        AssetDialog(
            initial = a, isNew = isNew,
            onSave = { updated ->
                val list = assets.filter { it.id != updated.id } + updated
                store.saveAssets(list); assets = list; assetEdit = null
            },
            onDelete = {
                val list = assets.filter { it.id != a.id }
                store.saveAssets(list); assets = list; assetEdit = null
            },
            onDismiss = { assetEdit = null }
        )
    }
}
