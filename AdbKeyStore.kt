package com.picopilot.adb

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object AdbKeyStore {

    private const val PREFS = "picopilot_adb_keys"
    private const val KEY_PRIVATE = "private_key"
    private const val KEY_PUBLIC = "public_key"

    @Synchronized
    fun load(context: Context): AdbKey {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val privateKey = prefs.getString(KEY_PRIVATE, null)
        val publicKey = prefs.getString(KEY_PUBLIC, null)

        if (privateKey != null && publicKey != null) {
            try {
                val factory = KeyFactory.getInstance("RSA")
                val priv = factory.generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(privateKey, Base64.NO_WRAP))
                )
                val pub = factory.generatePublic(
                    X509EncodedKeySpec(Base64.decode(publicKey, Base64.NO_WRAP))
                )
                return AdbKey(KeyPair(pub, priv))
            } catch (_: Exception) {
                // Повреждённый ключ — создадим новый ниже.
            }
        }

        val generated = AdbCrypto.generateKeyPair()
        prefs.edit()
            .putString(KEY_PRIVATE, Base64.encodeToString(generated.private.encoded, Base64.NO_WRAP))
            .putString(KEY_PUBLIC, Base64.encodeToString(generated.public.encoded, Base64.NO_WRAP))
            .apply()
        return AdbKey(generated)
    }

    @Synchronized
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
