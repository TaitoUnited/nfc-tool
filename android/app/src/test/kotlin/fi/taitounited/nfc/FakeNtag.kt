package fi.taitounited.nfc

import java.io.IOException

/**
 * An NTAG213, 215 or 216 as NXP's data sheet, rev. 3.2, describes it: the memory at delivery,
 * READ with roll-over, WRITE with the OR'ed capability container, PWD_AUTH, and AUTH0 and PROT
 * guarding pages. A NAK throws IOException, as Android's NfcA.transceive does.
 */
class FakeNtag(type: Int = 215, private val productType: Int = 0x04) : Transceiver {
    private val storage = mapOf(213 to 0x0F, 215 to 0x11, 216 to 0x13).getValue(type)
    val config = mapOf(213 to 0x29, 215 to 0x83, 216 to 0xE3).getValue(type)
    private val pages = Array(config + 4) { ByteArray(4) }
    private var authenticated = false
    private var halted = false

    /** Writes the tag still takes before it leaves the field; null for no limit. */
    var writesLeft: Int? = null

    init {
        pages[3] = hex(mapOf(213 to "E1101200", 215 to "E1103E00", 216 to "E1106D00").getValue(type))
        if (type == 213) {
            pages[4] = hex("0103A00C")
            pages[5] = hex("340300FE")
        } else {
            pages[4] = hex("0300FE00")
        }
        pages[config] = hex("040000FF")
        pages[config + 2] = hex("FFFFFFFF")
    }

    val auth0 get() = pages[config][3].toInt() and 0xFF
    var access: Int
        get() = pages[config + 1][0].toInt() and 0xFF
        set(value) {
            pages[config + 1][0] = value.toByte()
        }
    val pwd get() = hex(pages[config + 2])
    val pack get() = hex(pages[config + 3])
    val data get() = (4 until 4 + (pages[3][2].toInt() and 0xFF) * 2).flatMap { pages[it].toList() }.toByteArray()
    val content get() = Ndef.find(data)?.message?.let(Ndef::firstContent)

    /** A new tap: the tag starts over in the ACTIVE state. */
    fun reconnect() {
        authenticated = false
        halted = false
        writesLeft = null
    }

    /** Sets the configuration as another phone left it. */
    fun configure(pwd: String, pack: String, auth0: Int) {
        pages[config + 2] = hex(pwd)
        pages[config + 3] = hex(pack + "0000")
        pages[config][3] = auth0.toByte()
    }

    private fun locked(page: Int) = page >= auth0 && !authenticated
    private val readProtected get() = access and 0x80 != 0

    override fun transceive(command: ByteArray): ByteArray {
        if (halted) throw IOException("No answer")
        val page = command.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
        return when (command[0].toInt() and 0xFF) {
            0x60 -> hex("0004") + byteArrayOf(productType.toByte()) + hex("020100") + byteArrayOf(storage.toByte(), 3)
            0x30 -> {
                if (page >= pages.size || (readProtected && locked(page))) throw nak()
                val end = if (readProtected && !authenticated) minOf(auth0, pages.size) else pages.size
                (0 until 4).flatMap { n ->
                    val p = (page + n) % end
                    if (p == config + 2 || p == config + 3) List(4) { 0.toByte() } else pages[p].toList()
                }.toByteArray()
            }
            0xA2 -> {
                if (command.size != 6 || page < 2 || page >= pages.size || locked(page)) throw nak()
                writesLeft?.let {
                    if (it == 0) throw TagGone()
                    writesLeft = it - 1
                }
                val bytes = command.copyOfRange(2, 6)
                pages[page] = if (page == 3) ByteArray(4) { (pages[3][it].toInt() or bytes[it].toInt()).toByte() } else bytes
                byteArrayOf(0x0A)
            }
            0x1B -> {
                if (command.copyOfRange(1, 5).contentEquals(pages[config + 2])) {
                    authenticated = true
                    pages[config + 3].copyOfRange(0, 2)
                } else {
                    throw nak()
                }
            }
            else -> throw nak()
        }
    }

    private fun nak(): IOException {
        halted = true
        return IOException("NAK")
    }
}
