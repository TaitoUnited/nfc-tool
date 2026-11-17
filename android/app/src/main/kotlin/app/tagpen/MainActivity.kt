package app.tagpen

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class Nfc { Ready, Off, Missing, Demo }

private val demoOrange = Color(0xFFF57C00)

class MainActivity : ComponentActivity() {
    private val writer: TagWriter by viewModels()
    private var adapter: NfcAdapter? = null
    private var nfcOn by mutableStateOf(false)
    private var resumed = false
    private var readerOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adapter = NfcAdapter.getDefaultAdapter(this)
        writer.isDemo = intent.getBooleanExtra("demo", false)
        setContent {
            AppTheme {
                val nfc = when {
                    writer.isDemo -> Nfc.Demo
                    adapter == null -> Nfc.Missing
                    !nfcOn -> Nfc.Off
                    else -> Nfc.Ready
                }
                Screen(writer, nfc) { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            }
            val listening = writer.listening
            LaunchedEffect(listening) { updateReader() }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        nfcOn = adapter?.isEnabled == true
        // Every tag goes to the app while it is in front, so a tag left on the phone after a job
        // does not open its link in the middle of the work.
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val self = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        adapter?.enableForegroundDispatch(this, PendingIntent.getActivity(this, 0, self, mutable), null, null)
        updateReader()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        adapter?.disableForegroundDispatch(this)
        updateReader()
    }

    private fun updateReader() {
        val adapter = adapter ?: return
        val wanted = resumed && writer.listening
        if (wanted == readerOn) return
        readerOn = wanted
        if (wanted) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000) }
            adapter.enableReaderMode(this, writer::onTag, flags, extras)
        } else {
            adapter.disableReaderMode(this)
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(writer: TagWriter, nfc: Nfc, openNfcSettings: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val available = nfc == Nfc.Ready || nfc == Nfc.Demo
    val passphrase = writer.passphrase
    val content = contentOf(writer.typed)
    // Without this the fields take the focus back when the sheet closes, and the keyboard covers
    // the buttons.
    val focus = LocalFocusManager.current
    val start = { job: Job ->
        focus.clearFocus()
        writer.start(job)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { content?.let { start(Job.Write(it)) } },
                    enabled = available && content != null,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
                ) { Text(stringResource(R.string.write)) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (nfc) {
                Nfc.Demo -> item { Group { Text(stringResource(R.string.demo), color = demoOrange) } }
                Nfc.Missing -> item {
                    Group { Text(stringResource(R.string.no_nfc), color = MaterialTheme.colorScheme.error) }
                }
                Nfc.Off -> item {
                    Group {
                        Text(stringResource(R.string.nfc_off), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = openNfcSettings) { Text(stringResource(R.string.open_nfc_settings)) }
                    }
                }
                Nfc.Ready -> {}
            }
            item {
                Group(stringResource(R.string.content), footer = contentFooter(content)) {
                    OutlinedTextField(
                        value = writer.typed,
                        onValueChange = writer::updateTyped,
                        placeholder = { Text(stringResource(R.string.content_placeholder)) },
                        maxLines = 5,
                        enabled = available,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val status = writer.status
            if (status != null) {
                item { Group(stringResource(R.string.last_result)) { Text(say(status)) } }
            }
            item {
                Group(
                    stringResource(R.string.passphrase),
                    footer = stringResource(if (writer.remember) R.string.passphrase_saved else R.string.passphrase_not_saved),
                ) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = writer::updatePassphrase,
                        label = { Text(stringResource(R.string.passphrase)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.remember), modifier = Modifier.weight(1f))
                        Switch(checked = writer.remember, onCheckedChange = writer::updateRemember)
                    }
                }
            }
            item {
                Group(
                    stringResource(R.string.protect_header),
                    footer = stringResource(R.string.protect_footer),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                focus.clearFocus()
                                confirming = true
                            },
                            enabled = available && content != null && passphrase.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.protect)) }
                        OutlinedButton(
                            onClick = { start(Job.Unprotect) },
                            enabled = available && passphrase.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.unprotect)) }
                    }
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(stringResource(R.string.confirm_protect, passphrase)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    content?.let { start(Job.Protect(it)) }
                }) { Text(stringResource(R.string.protect)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    writer.sheet?.let { sheet -> TapSheet(sheet, writer.isDemo, onCancel = writer::cancel, onDone = writer::dismiss) }
}

@Composable
private fun say(words: Words) = stringResource(words.id, *words.args)

@Composable
private fun contentFooter(content: Content?): String = if (content == null) {
    stringResource(R.string.content_footer)
} else {
    val language = LocalConfiguration.current.locales[0].language
    say(describe(content)) + " " + say(sizeNote(Ndef.message(content, language).size))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TapSheet(sheet: TagWriter.Sheet, demo: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    val result = sheet.result
    LaunchedEffect(result) {
        if (result != null) {
            delay(2500)
            onDone()
        }
    }
    ModalBottomSheet(onDismissRequest = { if (result == null) onCancel() else onDone() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (demo) Text(stringResource(R.string.simulated_tap), style = MaterialTheme.typography.titleMedium, color = demoOrange)
            val (icon, tint) = when {
                result == null -> R.drawable.ic_tap to MaterialTheme.colorScheme.primary
                result.ok -> R.drawable.ic_done to Color(0xFF2E9D4F)
                else -> R.drawable.ic_failed to MaterialTheme.colorScheme.error
            }
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
            Text(say(result?.text ?: sheet.prompt), textAlign = TextAlign.Center)
            if (result == null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun Group(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
            )
        }
    }
}
