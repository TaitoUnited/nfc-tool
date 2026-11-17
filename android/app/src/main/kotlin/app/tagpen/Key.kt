package app.tagpen

import java.security.MessageDigest

// The tag stores a 4-byte password and a 2-byte acknowledge. Mixing in the UID gives each tag its
// own password from the same passphrase, as NXP's data sheet advises. The iOS app derives them the
// same way, so a tag protected on one platform unlocks on the other.
class Key(passphrase: String, uid: ByteArray) {
    val pwd: ByteArray
    val pack: ByteArray

    init {
        val hash = MessageDigest.getInstance("SHA-256").digest(uid + passphrase.toByteArray(Charsets.UTF_8))
        pwd = hash.copyOfRange(0, 4)
        pack = hash.copyOfRange(4, 6)
    }
}
