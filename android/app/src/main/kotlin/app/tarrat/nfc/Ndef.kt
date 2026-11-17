package app.tarrat.nfc

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
    /** An NDEF message of one well-known URI record, as the iOS app writes it. */
    fun uriMessage(url: String): ByteArray {
        val code = uriPrefixes.indices.drop(1)
            .filter { url.startsWith(uriPrefixes[it]) }
            .maxByOrNull { uriPrefixes[it].length } ?: 0
        val payload = byteArrayOf(code.toByte()) + url.substring(uriPrefixes[code].length).toByteArray()
        val type = byteArrayOf('U'.code.toByte())
        return if (payload.size <= 0xFF) {
            byteArrayOf(0xD1.toByte(), 1, payload.size.toByte()) + type + payload
        } else {
            val n = payload.size
            byteArrayOf(0xC1.toByte(), 1, (n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()) +
                type + payload
        }
    }

    /** The URI of the message's first record, or null when that is no well-known URI record. */
    fun firstUri(message: ByteArray): String? {
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
        if (i + typeLength > message.size) return null
        val isUri = header and 0x07 == 1 && typeLength == 1 && message[i] == 'U'.code.toByte()
        i += typeLength + idLength
        if (!isUri || payloadLength < 1 || payloadLength > message.size - i) return null
        val prefix = uriPrefixes.getOrNull(message.u8(i)) ?: return null
        return prefix + String(message, i + 1, payloadLength - 1, Charsets.UTF_8)
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
