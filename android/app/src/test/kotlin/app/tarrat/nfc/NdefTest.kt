package app.tarrat.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NdefTest {
    @Test
    fun telegramLinkIsOneShortUriRecord() {
        val message = Ndef.uriMessage("https://t.me/addstickers/taitounited")
        // Short record, well-known type "U", 29-byte payload, prefix code 04h for "https://".
        assertEquals("D1011D5504", hex(message.copyOfRange(0, 5)))
        assertEquals("t.me/addstickers/taitounited", String(message, 5, message.size - 5))
    }

    @Test
    fun packLinksRoundTrip() {
        for (pack in packs) {
            assertEquals(pack.url, Ndef.firstUri(Ndef.uriMessage(pack.url)))
        }
    }

    @Test
    fun signalLinkTakes146BytesWithTlvAndTerminator() {
        // The project notes: the Signal link takes 146 bytes as NDEF, an NTAG213 holds 144.
        val message = Ndef.uriMessage(packs.first { it.id == "Signal" }.url)
        assertEquals(146, Ndef.tlv(message).size + 1)
    }

    @Test
    fun longPrefixWins() {
        assertEquals(0x02, Ndef.uriMessage("https://www.example.com")[4].toInt())
        assertEquals("https://www.example.com", Ndef.firstUri(Ndef.uriMessage("https://www.example.com")))
    }

    @Test
    fun findsTheMessageAfterTheNtag213LockControlTlv() {
        // Pages 4 and 5 of an NTAG213 at delivery, data sheet table 5.
        val found = Ndef.find(hex("0103A00C340300FE"))!!
        assertEquals(5, found.start)
        assertEquals(0, found.message!!.size)
    }

    @Test
    fun findsTheTerminatorOfAnEmptyArea() {
        val found = Ndef.find(hex("0000FE00"))!!
        assertEquals(2, found.start)
        assertNull(found.message)
    }

    @Test
    fun asksForMoreWhenTheMessageRunsOn() {
        assertNull(Ndef.find(hex("03200000")))
    }

    @Test
    fun readsALongTlvLength() {
        val message = ByteArray(300) { 7 }
        val found = Ndef.find(Ndef.tlv(message) + byteArrayOf(0xFE.toByte()))!!
        assertEquals(300, found.message!!.size)
    }

    @Test
    fun ignoresOtherRecords() {
        val text = hex("D101025402") // a well-known text record
        assertNull(Ndef.firstUri(text))
        assertNull(Ndef.firstUri(hex("D1")))
    }
}
