package app.tagpen

import android.app.Application
import android.content.Context
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcA
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class TagWriter(app: Application) : AndroidViewModel(app) {
    /** A button press waiting for a tag, with the passphrase as it was then. */
    private class Pending(val job: Job, val passphrase: String)

    data class Sheet(val prompt: Words, val result: Outcome? = null)

    private val prefs = app.getSharedPreferences("tagpen", Context.MODE_PRIVATE)

    // What the tag gets and, while Remember is on, the passphrase are kept on this phone so they
    // are there next time. They guard company tags at a stand, not a bank, so having them ready is worth more
    // than hiding them from a phone its owner has unlocked.
    var typed by mutableStateOf(prefs.getString("link", "").orEmpty())
        private set
    var remember by mutableStateOf(prefs.getBoolean("remember", true))
        private set
    var passphrase by mutableStateOf(if (prefs.getBoolean("remember", true)) prefs.getString("passphrase", "").orEmpty() else "")
        private set

    var status by mutableStateOf<Words?>(null)
        private set
    var sheet by mutableStateOf<Sheet?>(null)
        private set
    var isDemo = false

    /** True while a button waits for a tag: the activity keeps reader mode on just then. */
    val listening get() = !isDemo && sheet?.let { it.result == null } == true

    private val main = Handler(Looper.getMainLooper())
    private val demoTag = DemoTag()
    private var current: Pending? = null
    private val waiting = AtomicReference<Pending?>(null)

    fun updateTyped(value: String) {
        typed = value
        prefs.edit().putString("link", value).apply()
    }

    fun updatePassphrase(value: String) {
        passphrase = value
        if (remember) prefs.edit().putString("passphrase", value).apply()
    }

    fun updateRemember(value: Boolean) {
        remember = value
        prefs.edit().apply {
            putBoolean("remember", value)
            if (value) putString("passphrase", passphrase) else remove("passphrase")
            apply()
        }
    }

    fun start(job: Job) {
        val pending = Pending(job, passphrase)
        current = pending
        sheet = Sheet(Say.hold(job))
        if (isDemo) {
            main.postDelayed({ if (current === pending) finish(pending, demoTag.tap(job, pending.passphrase)) }, 2500)
        } else {
            waiting.set(pending)
        }
    }

    fun cancel() {
        waiting.set(null)
        current = null
        sheet = null
    }

    fun dismiss() {
        sheet = null
    }

    /** Reader mode calls this on its own thread. Only the first tag after a button press counts. */
    fun onTag(tag: Tag) {
        val pending = waiting.getAndSet(null) ?: return
        val outcome = run(pending, tag)
        main.post { finish(pending, outcome) }
    }

    private fun finish(pending: Pending, outcome: Outcome) {
        status = outcome.text
        if (current !== pending) return
        current = null
        sheet = sheet?.copy(result = outcome)
    }

    private fun run(pending: Pending, tag: Tag): Outcome {
        val nfcA = NfcA.get(tag) ?: return Outcome(false, Say.NOT_NTAG)
        return try {
            nfcA.connect()
            val transceiver = Transceiver { command ->
                try {
                    nfcA.transceive(command)
                } catch (_: TagLostException) {
                    throw TagGone()
                }
            }
            perform(pending.job, pending.passphrase, transceiver, tag.id, Locale.getDefault().language)
        } catch (_: TagGone) {
            Outcome(false, Say.LOST)
        } catch (_: IOException) {
            Outcome(false, Say.LOST)
        } catch (_: SecurityException) {
            Outcome(false, Say.LOST)
        } finally {
            try {
                nfcA.close()
            } catch (_: IOException) {
            }
        }
    }
}
