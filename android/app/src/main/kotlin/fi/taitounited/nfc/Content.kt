package fi.taitounited.nfc

/** What a tag holds: the one record a phone acts on when it touches the tag. */
sealed interface Content {
    /** A URI record: a web address, or a mailto:, tel:, sms: or geo: one. */
    data class Link(val uri: String) : Content

    /** A text record. Its language code is not part of it here, so a tag compares by text alone. */
    data class Text(val text: String) : Content
}

private val linkSchemes = setOf("mailto", "tel", "sms", "geo")
private val schemeRegex = Regex("^([A-Za-z][A-Za-z0-9+.-]*):(//)?(\\S+)$")
private val domainRegex = Regex("^([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(:[0-9]+)?([/?#]\\S*)?$")
private val emailRegex = Regex("^[^@\\s/:]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$")
private val phoneRegex = Regex("^[+0][0-9 ()-]+$")

/**
 * Reads what the user typed as a tag's content. A web address, with or without https://, an
 * email address and a phone number become a link a phone opens; anything else is text.
 */
fun contentOf(typed: String): Content? {
    val text = typed.trim()
    if (text.isEmpty()) return null
    schemeRegex.matchEntire(text)?.let { match ->
        val scheme = match.groupValues[1].lowercase()
        val slashes = match.groupValues[2].isNotEmpty()
        if (scheme == "http" || scheme == "https") {
            if (slashes && '.' in hostOf(match.groupValues[3])) return Content.Link(text)
        } else if (slashes || scheme in linkSchemes) {
            return Content.Link(text)
        }
    }
    if (domainRegex.matches(text)) return Content.Link("https://$text")
    if (emailRegex.matches(text)) return Content.Link("mailto:$text")
    if (phoneRegex.matches(text) && text.count(Char::isDigit) >= 7) {
        return Content.Link("tel:" + text.filter { it.isDigit() || it == '+' })
    }
    return Content.Text(text)
}

private fun hostOf(rest: String) =
    rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':')

/** What a phone does with [content], in a sentence for the screen. */
fun describe(content: Content): Words = when (content) {
    is Content.Text -> Words(R.string.writes_text)
    is Content.Link -> {
        val uri = content.uri
        when {
            uri.startsWith("mailto:") -> Words(R.string.offers_email, uri.removePrefix("mailto:"))
            uri.startsWith("tel:") -> Words(R.string.offers_call, uri.removePrefix("tel:"))
            else -> Words(R.string.opens, uri)
        }
    }
}

/** The smallest NTAG21x that takes an NDEF message of [size] bytes, or null when none does. */
fun smallestTag(size: Int): Int? = when {
    // The data areas hold 144, 496 and 872 bytes. An NTAG213 keeps a 5-byte lock control TLV
    // before the message, and a message of 255 bytes or more takes a 4-byte TLV header, not 2.
    size <= 144 - 5 - 2 -> 213
    size <= 496 - 4 -> 215
    size <= 872 - 4 -> 216
    else -> null
}

fun sizeNote(size: Int): Words = when (smallestTag(size)) {
    213 -> Words(R.string.size_fits_213, size)
    215 -> Words(R.string.size_needs_215, size)
    216 -> Words(R.string.size_needs_216, size)
    else -> Words(R.string.size_too_long, size)
}

sealed interface Job {
    data class Write(val content: Content) : Job
    data class Protect(val content: Content) : Job
    data object Unprotect : Job
}

/** A sentence for the screen: a string resource and its format arguments. */
class Words(val id: Int, vararg val args: Any) {
    override fun equals(other: Any?) = other is Words && id == other.id && args.contentEquals(other.args)
    override fun hashCode() = 31 * id + args.contentHashCode()
}

data class Outcome(val ok: Boolean, val text: Words)

object Say {
    fun hold(job: Job) = when (job) {
        is Job.Write -> Words(R.string.hold)
        is Job.Protect -> Words(R.string.hold_to_protect)
        Job.Unprotect -> Words(R.string.hold_to_unprotect)
    }

    // Inside the app every tag goes to the app, so the tag can be tested only once the app is left.
    val WROTE = Words(R.string.wrote)
    val PROTECTED = Words(R.string.protected_tag)
    val NOT_HOLDING = Words(R.string.not_holding)
    fun tooLong(message: Int, room: Int) = Words(R.string.too_long, message, room)
    val NOT_FORMATTED = Words(R.string.not_formatted)
    val REMOVED = Words(R.string.removed)
    val NEEDS_PASSPHRASE = Words(R.string.needs_passphrase)
    val WRONG_KEEPS_TAG = Words(R.string.wrong_keeps_tag)
    val WRONG_KEEPS_PROTECTION = Words(R.string.wrong_keeps_protection)
    val ALREADY_PROTECTED = Words(R.string.already_protected)
    val NOT_PROTECTED = Words(R.string.not_protected)
    val NOT_NTAG = Words(R.string.not_ntag)
    val LOST = Words(R.string.lost)
    val CANNOT_READ_SETTINGS = Words(R.string.cannot_read_settings)
    val CANNOT_READ = Words(R.string.cannot_read)
    val CANNOT_WRITE = Words(R.string.cannot_write)
    val LOCKED_FOR_GOOD = Words(R.string.locked_for_good)
    val CANNOT_SET_PASSWORD = Words(R.string.cannot_set_password)
    val CANNOT_SET_PROTECTION = Words(R.string.cannot_set_protection)
    val CANNOT_TURN_ON = Words(R.string.cannot_turn_on)
    val DID_NOT_TAKE = Words(R.string.did_not_take)
    val CANNOT_REMOVE = Words(R.string.cannot_remove)
    val STILL_ON = Words(R.string.still_on)
}
