package app.tarrat.nfc

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    var protectTarget by remember { mutableStateOf<Pack?>(null) }
    val available = nfc == Nfc.Ready || nfc == Nfc.Demo
    val passphrase = writer.passphrase
    // Without this the passphrase field takes the focus back when the sheet closes, and its
    // keyboard covers the buttons.
    val focus = LocalFocusManager.current
    val start = { job: Job ->
        focus.clearFocus()
        writer.start(job)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Tarrat NFC") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (nfc) {
                Nfc.Demo -> item { Group { Text("Demo: tag taps are simulated, no real tag.", color = demoOrange) } }
                Nfc.Missing -> item {
                    Group { Text("This device cannot write NFC tags.", color = MaterialTheme.colorScheme.error) }
                }
                Nfc.Off -> item {
                    Group {
                        Text("NFC is off. Turn it on to write tags.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = openNfcSettings) { Text("Open the NFC settings") }
                    }
                }
                Nfc.Ready -> {}
            }
            item {
                Group(
                    footer = "Protecting a tag, removing its protection and rewriting a protected tag need it. " +
                        "Phones read a protected tag as usual. The app does not save it.",
                ) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { writer.passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (writer.status.isNotEmpty()) {
                item { Group("Last result") { Text(writer.status) } }
            }
            items(packs) { pack ->
                Group(pack.id) {
                    Text(
                        pack.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { start(Job.Write(pack)) }, enabled = available) {
                        Text("Write the ${pack.id} link")
                    }
                    TextButton(onClick = {
                        focus.clearFocus()
                        protectTarget = pack
                    }, enabled = available && passphrase.isNotEmpty()) {
                        Text("Protect the ${pack.id} tag")
                    }
                }
            }
            item {
                Group("Any tag") {
                    TextButton(onClick = { start(Job.Unprotect) }, enabled = available && passphrase.isNotEmpty()) {
                        Text("Remove the protection")
                    }
                }
            }
        }
    }

    protectTarget?.let { pack ->
        AlertDialog(
            onDismissRequest = { protectTarget = null },
            text = { Text("Remember the passphrase \"$passphrase\". Without it, nobody can change the tag again, you included.") },
            confirmButton = {
                TextButton(onClick = {
                    protectTarget = null
                    start(Job.Protect(pack))
                }) { Text("Protect the ${pack.id} tag") }
            },
            dismissButton = { TextButton(onClick = { protectTarget = null }) { Text("Cancel") } },
        )
    }

    writer.sheet?.let { sheet -> TapSheet(sheet, writer.isDemo, onCancel = writer::cancel, onDone = writer::dismiss) }
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
            if (demo) Text("Simulated tag tap", style = MaterialTheme.typography.titleMedium, color = demoOrange)
            val (icon, tint) = when {
                result == null -> R.drawable.ic_tap to MaterialTheme.colorScheme.primary
                result.ok -> R.drawable.ic_done to Color(0xFF2E9D4F)
                else -> R.drawable.ic_failed to MaterialTheme.colorScheme.error
            }
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
            Text(result?.text ?: sheet.prompt, textAlign = TextAlign.Center)
            if (result == null) {
                TextButton(onClick = onCancel) { Text("Cancel") }
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
