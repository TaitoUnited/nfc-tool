package app.tagpen

// The NFC Forum URI record type: the first payload byte stands for one of these prefixes.
private val uriPrefixes = listOf(
    "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
    "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://",
    "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:",
    "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
    "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
)

private const val TLV_NULL = 0x00
private const val TLV_NDEF = 0x03
private const val TLV_TERMINATOR = 0xFE

private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF

object Ndef {
    /** An NDEF message of one well-known record, as the iOS app writes it. */
    fun message(content: Content, language: String): ByteArray = when (content) {
        is Content.Link -> {
            val uri = content.uri
            val code = uriPrefixes.indices.drop(1)
                .filter { uri.startsWith(uriPrefixes[it]) }
                .maxByOrNull { uriPrefixes[it].length } ?: 0
            record('U', byteArrayOf(code.toByte()) + uri.substring(uriPrefixes[code].length).toByteArray())
        }
        // The status byte holds the language code's length; bit 7 clear means UTF-8.
        is Content.Text -> {
            val lang = language.toByteArray(Charsets.US_ASCII)
            record('T', byteArrayOf(lang.size.toByte()) + lang + content.text.toByteArray())
        }
    }

    private fun record(type: Char, payload: ByteArray): ByteArray {
        val n = payload.size
        val header = if (n <= 0xFF) {
            byteArrayOf(0xD1.toByte(), 1, n.toByte())
        } else {
            byteArrayOf(0xC1.toByte(), 1, (n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
        }
        return header + byteArrayOf(type.code.toByte()) + payload
    }

    /** The content of the message's first record, or null when that is no well-known URI or text record. */
    fun firstContent(message: ByteArray): Content? {
        if (message.size < 3) return null
        val header = message.u8(0)
        val typeLength = message.u8(1)
        var i = 2
        val payloadLength = if (header and 0x10 != 0) {
            message.u8(i++)
        } else {
            if (i + 4 > message.size) return null
            ((message.u8(i) shl 24) or (message.u8(i + 1) shl 16) or (message.u8(i + 2) shl 8) or message.u8(i + 3))
                .also { i += 4 }
        }
        val idLength = if (header and 0x08 != 0) message.u8(i++) else 0
        if (header and 0x07 != 1 || typeLength != 1 || i >= message.size) return null
        val type = message[i].toInt().toChar()
        i += typeLength + idLength
        if (payloadLength < 1 || payloadLength > message.size - i) return null
        val first = message.u8(i)
        return when (type) {
            'U' -> uriPrefixes.getOrNull(first)?.let {
                Content.Link(it + String(message, i + 1, payloadLength - 1, Charsets.UTF_8))
            }
            'T' -> {
                val lang = first and 0x3F
                if (first and 0x80 != 0 || 1 + lang > payloadLength) return null
                Content.Text(String(message, i + 1 + lang, payloadLength - 1 - lang, Charsets.UTF_8))
            }
            else -> null
        }
    }

    /** The NDEF message TLV, its 1-byte or 3-byte length first. */
    fun tlv(message: ByteArray): ByteArray {
        val n = message.size
        val length = if (n < 0xFF) byteArrayOf(n.toByte()) else byteArrayOf(0xFF.toByte(), (n ushr 8).toByte(), n.toByte())
        return byteArrayOf(TLV_NDEF.toByte()) + length + message
    }

    fun tlvHeaderSize(message: Int) = if (message < 0xFF) 2 else 4

    /**
     * Where the NDEF message TLV sits in the data area of a Type 2 Tag, which starts at page 4.
     * [start] is the offset of its type byte, or of the terminator when the area holds no
     * message. Lock and memory control TLVs before it are skipped and stay where they are.
     */
    class Found(val start: Int, val message: ByteArray?)

    /** Null when [data] ends before the NDEF TLV or the terminator does. */
    fun find(data: ByteArray): Found? {
        var i = 0
        while (i < data.size) {
            when (val type = data.u8(i)) {
                TLV_NULL -> i++
                TLV_TERMINATOR -> return Found(i, null)
                else -> {
                    if (i + 1 >= data.size) return null
                    var length = data.u8(i + 1)
                    var body = i + 2
                    if (length == 0xFF) {
                        if (i + 3 >= data.size) return null
                        length = (data.u8(i + 2) shl 8) or data.u8(i + 3)
                        body = i + 4
                    }
                    if (body + length > data.size) return null
                    if (type == TLV_NDEF) return Found(i, data.copyOfRange(body, body + length))
                    i = body + length
                }
            }
        }
        return null
    }
}
