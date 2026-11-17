package app.tagpen

// The demo intent extra plays each tap on a pretend tag, so the app can be shown on an emulator,
// which has no NFC. The pretend tag follows the same rules and says the same things.
class DemoTag {
    private var content: Content? = null
    private var passphrase: String? = null

    fun tap(job: Job, typed: String): Outcome = when (job) {
        is Job.Write -> {
            val key = passphrase
            when {
                key != null && typed.isEmpty() -> Outcome(false, Say.NEEDS_PASSPHRASE)
                key != null && typed != key -> Outcome(false, Say.WRONG_KEEPS_TAG)
                else -> {
                    content = job.content
                    Outcome(true, Say.WROTE)
                }
            }
        }
        is Job.Protect -> when {
            passphrase != null -> Outcome(false, Say.ALREADY_PROTECTED)
            content != job.content -> Outcome(false, Say.NOT_HOLDING)
            else -> {
                passphrase = typed
                Outcome(true, Say.PROTECTED)
            }
        }
        Job.Unprotect -> when (passphrase) {
            null -> Outcome(false, Say.NOT_PROTECTED)
            typed -> {
                passphrase = null
                Outcome(true, Say.REMOVED)
            }
            else -> Outcome(false, Say.WRONG_KEEPS_PROTECTION)
        }
    }
}
