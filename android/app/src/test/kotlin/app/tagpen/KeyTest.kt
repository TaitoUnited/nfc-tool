package app.tagpen

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyTest {
    // Computed on 2026-11-17 by the iOS app's own Key struct (Sources/TagWriter.swift, CryptoKit),
    // copied verbatim into a Swift script, so a tag protected on either platform unlocks on both.
    private val fromIos = listOf(
        Vector("04A24B1A2C5D80", "kumpula26", pwd = "9734B605", pack = "F086"),
        Vector("04A24B1A2C5D81", "kumpula26", pwd = "67BCBA2D", pack = "A2BA"),
        Vector("04112233445566", "Kumpulan Potentiaali", pwd = "026C80DF", pack = "4B08"),
        Vector("04112233445566", "tarrat äö €", pwd = "3C7F9FD9", pack = "1625"),
        Vector("04112233445566", "", pwd = "6F0C82D8", pack = "7A94"),
    )

    @Test
    fun matchesTheIosApp() {
        for (v in fromIos) {
            val key = Key(v.passphrase, hex(v.uid))
            assertEquals("PWD for ${v.uid} \"${v.passphrase}\"", v.pwd, hex(key.pwd))
            assertEquals("PACK for ${v.uid} \"${v.passphrase}\"", v.pack, hex(key.pack))
        }
    }

    private class Vector(val uid: String, val passphrase: String, val pwd: String, val pack: String)
}

fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it) }
