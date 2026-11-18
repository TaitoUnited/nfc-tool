package fi.taitounited.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JobsTest {
    private val uid = hex("04A24B1A2C5D80")

    private fun FakeNtag.tap(job: Job, passphrase: String = ""): Outcome {
        reconnect()
        return perform(job, passphrase, this, uid)
    }

    @Test
    fun writesALinkOnAFreshTag() {
        for (type in listOf(215, 216)) {
            val tag = FakeNtag(type)
            assertEquals(Outcome(true, Say.WROTE), tag.tap(Job.Write(signal)))
            assertEquals(signal, tag.content)
        }
    }

    @Test
    fun keepsTheNtag213LockControlTlv() {
        val tag = FakeNtag(213)
        assertTrue(tag.tap(Job.Write(telegram)).ok)
        assertEquals("0103A00C34", hex(tag.data.copyOfRange(0, 5)))
        assertEquals(telegram, tag.content)
    }

    @Test
    fun signalLinkDoesNotFitAnNtag213() {
        val tag = FakeNtag(213)
        assertEquals(Outcome(false, Say.tooLong(143, 137)), tag.tap(Job.Write(signal)))
        assertNull(tag.content)
    }

    @Test
    fun writesAndProtectsText() {
        val note = Content.Text("Kumpulan Potentiaali, osasto 12")
        val tag = FakeNtag(213)
        assertEquals(Outcome(true, Say.WROTE), tag.tap(Job.Write(note)))
        assertEquals(note, tag.content)
        assertEquals(Outcome(false, Say.NOT_HOLDING), tag.tap(Job.Protect(Content.Text("other")), "kumpula26"))
        assertEquals(Outcome(true, Say.PROTECTED), tag.tap(Job.Protect(note), "kumpula26"))
    }

    @Test
    fun refusesOtherTags() {
        val ultralightEv1 = FakeNtag(215, productType = 0x03)
        assertEquals(Outcome(false, Say.NOT_NTAG), ultralightEv1.tap(Job.Write(signal)))
    }

    @Test
    fun protectRefusesATagWithoutTheTypedContent() {
        val tag = FakeNtag()
        assertEquals(Outcome(false, Say.NOT_HOLDING), tag.tap(Job.Protect(signal), "kumpula26"))
        tag.tap(Job.Write(telegram))
        assertEquals(Outcome(false, Say.NOT_HOLDING), tag.tap(Job.Protect(signal), "kumpula26"))
        assertEquals(0xFF, tag.auth0)
    }

    @Test
    fun protectSetsTheIosPasswordAndKeepsTheTagReadable() {
        val tag = FakeNtag()
        tag.tap(Job.Write(signal))
        assertEquals(Outcome(true, Say.PROTECTED), tag.tap(Job.Protect(signal), "kumpula26"))
        assertEquals("9734B605", tag.pwd)
        assertEquals("F0860000", tag.pack)
        assertEquals(0x00, tag.auth0)
        assertEquals(0x00, tag.access)
        assertEquals(signal, tag.content)
        assertEquals(Outcome(false, Say.ALREADY_PROTECTED), tag.tap(Job.Protect(signal), "kumpula26"))
    }

    @Test
    fun protectClearsProtAndAuthlimButKeepsTheCounterBits() {
        val tag = FakeNtag()
        tag.tap(Job.Write(signal))
        tag.access = 0x97 // PROT, NFC_CNT_EN and AUTHLIM 7
        assertTrue(tag.tap(Job.Protect(signal), "kumpula26").ok)
        assertEquals(0x10, tag.access)
    }

    @Test
    fun rewritingAProtectedTagNeedsItsPassphrase() {
        val tag = FakeNtag()
        tag.tap(Job.Write(signal))
        tag.tap(Job.Protect(signal), "kumpula26")
        assertEquals(Outcome(false, Say.NEEDS_PASSPHRASE), tag.tap(Job.Write(telegram)))
        assertEquals(Outcome(false, Say.WRONG_KEEPS_TAG), tag.tap(Job.Write(telegram), "kumpula25"))
        assertEquals(signal, tag.content)
        assertEquals(Outcome(true, Say.WROTE), tag.tap(Job.Write(telegram), "kumpula26"))
        assertEquals(telegram, tag.content)
        assertEquals(0x00, tag.auth0)
    }

    @Test
    fun removesTheProtectionWithTheRightPassphraseOnly() {
        val tag = FakeNtag()
        assertEquals(Outcome(false, Say.NOT_PROTECTED), tag.tap(Job.Unprotect, "kumpula26"))
        tag.tap(Job.Write(signal))
        tag.tap(Job.Protect(signal), "kumpula26")
        assertEquals(Outcome(false, Say.WRONG_KEEPS_PROTECTION), tag.tap(Job.Unprotect, "Kumpula26"))
        assertEquals(0x00, tag.auth0)
        assertEquals(Outcome(true, Say.REMOVED), tag.tap(Job.Unprotect, "kumpula26"))
        assertEquals(0xFF, tag.auth0)
        assertEquals(Outcome(true, Say.WROTE), tag.tap(Job.Write(telegram)))
    }

    @Test
    fun aTagProtectedOnTheIphoneUnlocksHere() {
        // The configuration the iOS app writes for UID 04A24B1A2C5D80 and "kumpula26", see KeyTest.
        val tag = FakeNtag()
        tag.tap(Job.Write(signal))
        tag.configure(pwd = "9734B605", pack = "F086", auth0 = 0x00)
        assertEquals(Outcome(true, Say.REMOVED), tag.tap(Job.Unprotect, "kumpula26"))
    }

    @Test
    fun aTagPulledAwayMidwayHoldsTheOldLinkOrNone() {
        for ((type, old, new) in listOf(Triple(215, telegram, signal), Triple(213, telegram, telegram))) {
            for (writes in 0..60) {
                val tag = FakeNtag(type)
                tag.tap(Job.Write(old))
                tag.reconnect()
                tag.writesLeft = writes
                val finished = try {
                    perform(Job.Write(new), "", tag, uid).ok
                } catch (_: TagGone) {
                    false
                }
                val held = tag.content
                when {
                    finished -> assertEquals(new, held)
                    writes == 0 -> assertEquals(old, held)
                    else -> assertTrue("NTAG$type after $writes writes holds $held", held == null)
                }
                if (finished) break
                if (writes == 60) fail("The write never finished")
            }
        }
    }

    @Test
    fun demoTagFollowsTheSameRules() {
        val demo = DemoTag()
        assertEquals(Say.NOT_HOLDING, demo.tap(Job.Protect(signal), "x").text)
        assertTrue(demo.tap(Job.Write(signal), "").ok)
        assertTrue(demo.tap(Job.Protect(signal), "x").ok)
        assertEquals(Say.NEEDS_PASSPHRASE, demo.tap(Job.Write(telegram), "").text)
        assertEquals(Say.WRONG_KEEPS_PROTECTION, demo.tap(Job.Unprotect, "y").text)
        assertFalse(demo.tap(Job.Protect(signal), "x").ok)
        assertEquals(Say.REMOVED, demo.tap(Job.Unprotect, "x").text)
    }
}
