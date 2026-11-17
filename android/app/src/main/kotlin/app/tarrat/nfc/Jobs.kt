package app.tarrat.nfc

/** Runs [job] on an NTAG21x over one NfcA connection, step for step as the iOS app does. */
fun perform(job: Job, passphrase: String, tag: Transceiver, uid: ByteArray): Outcome {
    val ntag = Ntag.open(tag) ?: return fail(Say.NOT_NTAG)
    val cfg = ntag.readConfig() ?: return fail("Could not read the tag's settings.")
    val isProtected = (cfg[3].toInt() and 0xFF) <= ntag.lastConfigPage
    val key = Key(passphrase, uid)
    return when (job) {
        is Job.Write -> write(job.pack, ntag, if (isProtected) key else null, passphrase)
        is Job.Protect -> if (isProtected) fail(Say.ALREADY_PROTECTED) else protect(ntag, job.pack, key, cfg)
        Job.Unprotect -> if (!isProtected) fail(Say.NOT_PROTECTED) else unprotect(ntag, key, cfg)
    }
}

private fun fail(text: String) = Outcome(false, text)

private fun pagesOf(bytes: ByteArray): List<ByteArray> =
    bytes.copyOf((bytes.size + 3) / 4 * 4).toList().chunked(4) { it.toByteArray() }

private fun write(pack: Pack, ntag: Ntag, key: Key?, passphrase: String): Outcome {
    if (key != null) {
        if (passphrase.isEmpty()) return fail(Say.NEEDS_PASSPHRASE)
        if (!ntag.authenticate(key)) return fail(Say.WRONG_KEEPS_TAG)
    }
    val area = ntag.area() ?: return fail("This tag cannot hold a link.")
    if (!area.writable) return fail("This tag is locked for good.")
    val data = ntag.readData(area) ?: return fail("Could not read the tag.")
    val start = Ndef.find(data)?.start ?: 0
    val message = Ndef.uriMessage(pack.url)
    val header = Ndef.tlvHeaderSize(message.size)
    val room = area.size - start - header
    if (message.size > room) return fail(Say.tooLong(message.size, room))

    // The tag holds an empty message until the last write puts the length in, so a tag pulled
    // away midway holds no half link.
    val before = data.copyOfRange(0, start)
    val terminator = if (message.size < room) byteArrayOf(0xFE.toByte()) else ByteArray(0)
    val empty = pagesOf(before + byteArrayOf(0x03, 0x00, 0xFE.toByte())).withIndex().drop(start / 4)
    val full = pagesOf(before + Ndef.tlv(message) + terminator).withIndex().drop(start / 4)
    val headerPages = (start / 4)..((start + header - 1) / 4)
    val steps = empty + full.filter { it.index !in headerPages } + full.filter { it.index in headerPages }
    for ((index, page) in steps) {
        if (!ntag.write(4 + index, page)) return fail("Could not write the link.")
    }
    return Outcome(true, Say.wrote(pack))
}

// Protection goes only on a tag that holds the right link. AUTH0 goes last, as the data sheet
// advises, and AUTHLIM stays 0: with a limit, wrong guesses would freeze the tag for good.
private fun protect(ntag: Ntag, pack: Pack, key: Key, cfg: ByteArray): Outcome {
    val url = ntag.area()?.let(ntag::readData)?.let(Ndef::find)?.message?.let(Ndef::firstUri)
    if (url != pack.url) return fail(Say.notHolding(pack))
    val access = (cfg[4].toInt() and 0x58).toByte()
    if (!ntag.write(ntag.config + 2, key.pwd)) return fail("Could not set the password.")
    if (!ntag.write(ntag.config + 3, key.pack + byteArrayOf(0, 0))) return fail("Could not set the password.")
    if (!ntag.write(ntag.config + 1, byteArrayOf(access, cfg[5], cfg[6], cfg[7]))) {
        return fail("Could not set the protection.")
    }
    if (!ntag.write(ntag.config, byteArrayOf(cfg[0], cfg[1], cfg[2], 0x00))) {
        return fail("Could not turn the protection on.")
    }
    if (ntag.readConfig()?.get(3) != 0x00.toByte()) return fail("The protection did not take.")
    return Outcome(true, Say.protected(pack))
}

private fun unprotect(ntag: Ntag, key: Key, cfg: ByteArray): Outcome {
    if (!ntag.authenticate(key)) return fail(Say.WRONG_KEEPS_PROTECTION)
    if (!ntag.write(ntag.config, byteArrayOf(cfg[0], cfg[1], cfg[2], 0xFF.toByte()))) {
        return fail("Could not remove the protection.")
    }
    if (ntag.readConfig()?.get(3) != 0xFF.toByte()) return fail("The protection is still on.")
    return Outcome(true, Say.REMOVED)
}
