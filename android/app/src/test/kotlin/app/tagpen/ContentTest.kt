package app.tagpen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentTest {
    @Test
    fun readsWhatIsTyped() {
        val cases = mapOf(
            "taitounited.fi" to Content.Link("https://taitounited.fi"),
            " t.me/addstickers/taitounited " to Content.Link("https://t.me/addstickers/taitounited"),
            "taitounited.fi:8080/a?b=c" to Content.Link("https://taitounited.fi:8080/a?b=c"),
            signal.uri to signal,
            "ftp://files.example.com/x" to Content.Link("ftp://files.example.com/x"),
            "mailto:akira@example.fi" to Content.Link("mailto:akira@example.fi"),
            "akira@example.fi" to Content.Link("mailto:akira@example.fi"),
            "+358 40 123 4567" to Content.Link("tel:+358401234567"),
            "040-123 4567" to Content.Link("tel:0401234567"),
            "sms:+358401234567" to Content.Link("sms:+358401234567"),
            "Hello world" to Content.Text("Hello world"),
            "Note: hello" to Content.Text("Note: hello"),
            "klo:15" to Content.Text("klo:15"),
            "https://localhost" to Content.Text("https://localhost"),
            "v1.2" to Content.Text("v1.2"),
            "123456" to Content.Text("123456"),
            "Rivi 1\nRivi 2" to Content.Text("Rivi 1\nRivi 2"),
        )
        for ((typed, expected) in cases) assertEquals(typed, expected, contentOf(typed))
        assertNull(contentOf("  "))
    }

    @Test
    fun namesTheSmallestTagThatFits() {
        assertEquals(213, smallestTag(137))
        assertEquals(215, smallestTag(138))
        assertEquals(215, smallestTag(492))
        assertEquals(216, smallestTag(493))
        assertEquals(216, smallestTag(868))
        assertNull(smallestTag(869))
        // The project notes: the Signal link does not fit an NTAG213.
        assertEquals(215, smallestTag(Ndef.message(signal, "en").size))
    }
}
