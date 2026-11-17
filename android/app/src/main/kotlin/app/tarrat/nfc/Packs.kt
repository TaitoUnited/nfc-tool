package app.tarrat.nfc

data class Pack(val id: String, val url: String)

val packs = listOf(
    Pack(
        "Signal",
        "https://signal.art/addstickers/#pack_id=1b601d8c2945a9e8dc10874112903887&pack_key=53b95b66cb5d040123a385cfb74cfd4ca308621ccf7574d93a2a3050a2b8e750",
    ),
    Pack("Telegram", "https://t.me/addstickers/taitounited"),
)

sealed interface Job {
    data class Write(val pack: Pack) : Job
    data class Protect(val pack: Pack) : Job
    data object Unprotect : Job
}

data class Outcome(val ok: Boolean, val text: String)

object Say {
    fun hold(job: Job) = when (job) {
        is Job.Write -> "Hold the back of the phone on the ${job.pack.id} tag."
        is Job.Protect -> "Hold the back of the phone on the ${job.pack.id} tag to protect it."
        Job.Unprotect -> "Hold the back of the phone on the tag to remove its protection."
    }

    // Inside the app every tag goes to the app, so the link opens only once the app is left.
    fun wrote(pack: Pack) = "Wrote the ${pack.id} link. Test it outside the app before you protect the tag."
    fun protected(pack: Pack) = "Protected the ${pack.id} tag. Phones still read it."
    fun notHolding(pack: Pack) = "This tag does not hold the ${pack.id} link, so it stays unprotected."
    fun tooLong(message: Int, room: Int) = "The link takes $message bytes, the tag holds $room."
    const val REMOVED = "Removed the protection. Anyone can rewrite the tag now."
    const val NEEDS_PASSPHRASE = "This tag is protected. Type its passphrase first."
    const val WRONG_KEEPS_TAG = "Wrong passphrase. The tag stays as it was."
    const val WRONG_KEEPS_PROTECTION = "Wrong passphrase. The tag stays protected."
    const val ALREADY_PROTECTED = "This tag is protected already."
    const val NOT_PROTECTED = "This tag has no protection to remove."
    const val NOT_NTAG = "This is not an NTAG213, 215 or 216 tag."
    const val LOST = "The tag moved away. Hold the phone still on it and try again."
}
