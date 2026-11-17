package app.tagpen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NdefTest {
    @Test
    fun telegramLinkIsOneShortUriRecord() {
        val message = Ndef.message(telegram, "en")
        // Short record, well-known type "U", 29-byte payload, prefix code 04h for "https://".
        assertEquals("D1011D5504", hex(message.copyOfRange(0, 5)))
        assertEquals("t.me/addstickers/taitounited", String(message, 5, message.size - 5))
    }

    @Test
    fun linksRoundTrip() {
        for (link in listOf(signal, telegram, Content.Link("tel:+358401234567"), Content.Link("mailto:a@b.fi"))) {
            assertEquals(link, Ndef.firstContent(Ndef.message(link, "en")))
        }
    }

    @Test
    fun textIsOneShortTextRecord() {
        val message = Ndef.message(Content.Text("Hei ä"), "fi")
        // Short record, well-known type "T", status 02h for UTF-8 and a 2-byte language code.
        assertEquals("D101095402", hex(message.copyOfRange(0, 5)))
        assertEquals("fiHei ä", String(message, 5, message.size - 5))
        assertEquals(Content.Text("Hei ä"), Ndef.firstContent(message))
    }

    @Test
    fun longTextTakesALongRecord() {
        val text = Content.Text("x".repeat(300))
        val message = Ndef.message(text, "en")
        assertEquals(0xC1, message[0].toInt() and 0xFF)
        assertEquals(text, Ndef.firstContent(message))
    }

    @Test
    fun signalLinkTakes146BytesWithTlvAndTerminator() {
        // The project notes: the Signal link takes 146 bytes as NDEF, an NTAG213 holds 144.
        val message = Ndef.message(signal, "en")
        assertEquals(146, Ndef.tlv(message).size + 1)
    }

    @Test
    fun longPrefixWins() {
        val link = Content.Link("https://www.example.com")
        assertEquals(0x02, Ndef.message(link, "en")[4].toInt())
        assertEquals(link, Ndef.firstContent(Ndef.message(link, "en")))
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
        assertNull(Ndef.firstContent(hex("D101015300"))) // a well-known record of type "S"
        assertNull(Ndef.firstContent(hex("D1")))
    }
}
