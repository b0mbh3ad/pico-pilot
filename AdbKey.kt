package com.picopilot.adb

import java.security.KeyPair
import java.security.interfaces.RSAPublicKey

class AdbKey(val keyPair: KeyPair) {

    val publicKeyBlob: ByteArray by lazy {
        AdbCrypto.encodePublicKey(keyPair.public as RSAPublicKey)
    }

    fun sign(token: ByteArray): ByteArray = AdbCrypto.signToken(keyPair.private, token)

    val fingerprint: String by lazy {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(publicKeyBlob)
        digest.take(8).joinToString(":") { "%02x".format(it) }
    }
}
