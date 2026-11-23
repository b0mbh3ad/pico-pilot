package com.picopilot.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey

/**
 * Криптография ADB: генерация ключа, формат публичного ключа Android и подпись токена.
 *
 * Публичный ключ передаётся в "сыром" формате adb:
 *   uint32 len | modulus (len байт, big-endian) | uint32 n0inv | rr (len байт) | uint32 exponent
 * Всё в little-endian, кроме самих чисел modulus/rr.
 */
object AdbCrypto {

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(privateKey)
        signature.update(token)
        return signature.sign()
    }

    fun encodePublicKey(publicKey: RSAPublicKey): ByteArray {
        val modulus = publicKey.modulus
        val len = (modulus.bitLength() + 7) / 8
        require(len in 1..1024) { "Неподдерживаемый размер ключа" }

        val modulusBytes = toFixed(modulus, len)

        // Младшее 32-битное слово модуля (little-endian) и его обратное по модулю 2^32.
        var n0 = 0
        for (i in 0 until 4) {
            n0 = n0 or ((modulusBytes[len - 1 - i].toInt() and 0xff) shl (8 * i))
        }
        var inverse = n0
        repeat(5) { inverse *= 2 - n0 * inverse }
        val n0inv = -inverse

        // rr = 2^(2 * len * 8) mod n
        val rr = BigInteger.ONE.shiftLeft(len * 16).mod(modulus)
        val rrBytes = toFixed(rr, len)

        val buffer = ByteBuffer.allocate(4 + len + 4 + len + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(len)
        buffer.put(modulusBytes)
        buffer.putInt(n0inv)
        buffer.put(rrBytes)
        buffer.putInt(publicKey.publicExponent.toInt())
        return buffer.array()
    }

    fun toFixed(value: BigInteger, length: Int): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(length)
        if (raw.size > length) {
            System.arraycopy(raw, raw.size - length, out, 0, length)
        } else {
            System.arraycopy(raw, 0, out, length - raw.size, raw.size)
        }
        return out
    }
}
