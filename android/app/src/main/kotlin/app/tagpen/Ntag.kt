package app.tagpen

import java.io.IOException

/** Sends one raw ISO 14443-3A command and returns the tag's answer. A NAK throws IOException. */
fun interface Transceiver {
    fun transceive(command: ByteArray): ByteArray
}

/** Thrown when the tag left the field, so the job stops and says so instead of a step's error. */
class TagGone : Exception()

// Commands and configuration pages from NXP's NTAG213/215/216 data sheet, rev. 3.2. Everything goes
// through one NfcA connection: switching Android to its Ndef class reconnects the tag on many
// phones, which drops the authentication the write needs.
class Ntag private constructor(private val tag: Transceiver, val config: Int) {
    val lastConfigPage get() = config + 3

    class Area(val size: Int, val writable: Boolean)

    fun readConfig() = read(config)

    fun read(page: Int): ByteArray? = send(tag, bytes(0x30, page))?.takeIf { it.size == 16 }

    fun write(page: Int, data: ByteArray): Boolean {
        val answer = send(tag, bytes(0xA2, page) + data) ?: return false
        return answer.isEmpty() || (answer.size == 1 && answer[0] == ACK)
    }

    fun authenticate(key: Key): Boolean =
        send(tag, bytes(0x1B) + key.pwd)?.contentEquals(key.pack) == true

    /** The NDEF area the capability container in page 3 describes, or null on an unformatted tag. */
    fun area(): Area? {
        val cc = read(3) ?: return null
        if (cc[0] != 0xE1.toByte()) return null
        return Area(size = (cc[2].toInt() and 0xFF) * 8, writable = cc[3].toInt() and 0x0F == 0)
    }

    /** The data area from page 4, read until the NDEF TLV or the terminator has ended. */
    fun readData(area: Area): ByteArray? {
        var data = ByteArray(0)
        var page = 4
        while (data.size < area.size) {
            data += read(page) ?: return null
            page += 4
            if (Ndef.find(data) != null) break
        }
        return data.copyOf(minOf(data.size, area.size))
    }

    companion object {
        private const val ACK: Byte = 0x0A
        private val configPage = mapOf(0x0F to 0x29, 0x11 to 0x83, 0x13 to 0xE3)

        fun open(tag: Transceiver): Ntag? {
            val version = send(tag, bytes(0x60)) ?: return null
            if (version.size != 8 || version[2] != 0x04.toByte()) return null
            val config = configPage[version[6].toInt() and 0xFF] ?: return null
            return Ntag(tag, config)
        }

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        private fun send(tag: Transceiver, command: ByteArray): ByteArray? = try {
            tag.transceive(command)
        } catch (_: IOException) {
            null
        }
    }
}
