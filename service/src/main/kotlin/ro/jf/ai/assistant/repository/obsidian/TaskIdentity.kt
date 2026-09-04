package ro.jf.ai.assistant.repository.obsidian

import java.security.MessageDigest

object TaskIdentity {
    private const val BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
    private const val TOKEN_LENGTH = 8

    fun token(canonicalInput: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalInput.encodeToByteArray())
        return base32Token(digest)
    }

    private fun base32Token(bytes: ByteArray): String {
        val builder = StringBuilder(TOKEN_LENGTH)
        var buffer = 0L
        var bits = 0
        var index = 0
        while (builder.length < TOKEN_LENGTH) {
            buffer = (buffer shl 8) or (bytes[index].toLong() and 0xFF)
            bits += 8
            index++
            while (bits >= 5 && builder.length < TOKEN_LENGTH) {
                bits -= 5
                builder.append(BASE32_ALPHABET[((buffer shr bits) and 0x1F).toInt()])
            }
        }
        return builder.toString()
    }
}
