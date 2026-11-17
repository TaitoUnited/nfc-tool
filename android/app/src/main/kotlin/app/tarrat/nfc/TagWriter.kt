package app.tarrat.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcA
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class TagWriter : ViewModel() {
    /** A button press waiting for a tag, with the passphrase as it was then. */
    private class Pending(val job: Job, val passphrase: String)

    data class Sheet(val prompt: String, val result: Outcome? = null)

    var passphrase by mutableStateOf("")
    var status by mutableStateOf("")
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
            perform(pending.job, pending.passphrase, transceiver, tag.id)
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
