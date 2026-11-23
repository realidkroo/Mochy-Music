// SecretVault.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// seals creds with a per-install random key wrapped in keystore + domain-separated subkeys
@OptIn(ExperimentalEncodingApi::class)
object SecretVault {
    const val DefaultDomain = "default"
    const val SettingsDomain = "settings"
    const val CloudBackupDomain = "cloud_backup"
    const val ModuleFeedDomain = "module_feed"
    const val LinkDomain = "link"

    private const val LegacyAlias = "mochy_secrets"
    private const val AliasPrefix = "mochy_k_"
    private const val KeyFileName = ".vault_k"
    private const val PrefixV1 = "enc1:"
    private const val PrefixV2 = "enc2:"
    private const val IvBytes = 12
    private const val TagBits = 128
    private const val DekBytes = 32
    private const val Transformation = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    @Volatile private var keyDir: File? = null
    @Volatile private var dek: ByteArray? = null
    @Volatile private var legacyKey: SecretKey? = null
    @Volatile private var testDek: ByteArray? = null

    fun init(context: Context) {
        keyDir = context.applicationContext.noBackupFilesDir
    }

    // jvm tests don't have android keystore, so they plug in a temp key here
    internal fun useTestKey(raw: ByteArray?) {
        testDek = raw?.copyOf()
        dek = null
    }

    fun isSealed(text: String): Boolean = text.startsWith(PrefixV2) || text.startsWith(PrefixV1)

    fun needsUpgrade(text: String): Boolean = !text.startsWith(PrefixV2)

    fun seal(plain: String, domain: String = DefaultDomain): String {
        if (plain.startsWith(PrefixV2)) return plain
        val master = dekOrNull()
        if (master != null) {
            return runCatching { sealV2(plain, master, domain) }.getOrElse { sealLegacyFallback(plain) }
        }
        return sealLegacyFallback(plain)
    }

    fun open(stored: String, domain: String = DefaultDomain): String? {
        if (stored.startsWith(PrefixV2)) {
            val master = dekOrNull() ?: return null
            return runCatching { openV2(stored, master, domain) }.getOrNull()
        }
        if (stored.startsWith(PrefixV1)) {
            val key = legacyKeyOrNull() ?: return null
            return runCatching {
                val bytes = Base64.decode(stored.removePrefix(PrefixV1))
                if (bytes.size <= IvBytes) return null
                val cipher = Cipher.getInstance(Transformation)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBits, bytes, 0, IvBytes))
                String(cipher.doFinal(bytes, IvBytes, bytes.size - IvBytes), Charsets.UTF_8)
            }.getOrNull()
        }
        return stored
    }

    fun openWithFallbackDomain(stored: String, primary: String, fallback: String): String? =
        open(stored, primary) ?: if (primary != fallback) open(stored, fallback) else null

    private fun sealV2(plain: String, master: ByteArray, domain: String): String {
        val iv = ByteArray(IvBytes).also(random::nextBytes)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, domainKey(master, domain), GCMParameterSpec(TagBits, iv))
        cipher.updateAAD("$PrefixV2$domain".toByteArray(Charsets.UTF_8))
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PrefixV2 + Base64.encode(iv + out)
    }

    private fun openV2(stored: String, master: ByteArray, domain: String): String? {
        val bytes = Base64.decode(stored.removePrefix(PrefixV2))
        if (bytes.size <= IvBytes) return null
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, domainKey(master, domain), GCMParameterSpec(TagBits, bytes, 0, IvBytes))
        cipher.updateAAD("$PrefixV2$domain".toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes, IvBytes, bytes.size - IvBytes), Charsets.UTF_8)
    }

    // per-domain subkey so ciphertext stolen from one store won't even open in another
    private fun domainKey(master: ByteArray, domain: String): SecretKey {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(master, "HmacSHA256"))
        val derived = mac.doFinal("mochy-vault-v2:$domain".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(derived, "AES")
    }

    private fun sealLegacyFallback(plain: String): String {
        if (plain.startsWith(PrefixV1)) return plain
        val key = legacyKeyOrNull() ?: return plain
        return runCatching {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            PrefixV1 + Base64.encode(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
        }.getOrElse { plain }
    }

    private fun dekOrNull(): ByteArray? {
        testDek?.let { return it }
        dek?.let { return it }
        val dir = keyDir ?: return null
        synchronized(this) {
            dek?.let { return it }
            return runCatching { loadOrCreateDek(dir) }
                .onFailure {
                    Timber.tag("Vault").w(it, "KeyStore temporarily unavailable while loading vault DEK")
                }
                .getOrNull()
                ?.also { dek = it }
        }
    }

    private fun loadOrCreateDek(dir: File): ByteArray {
        val file = File(dir, KeyFileName)
        if (file.isFile) {
            val parts = file.readText().trim().split('|')
            if (parts.size == 3 && parts[0] == "v2") {
                val wrap = loadKeyStoreKey(parts[1], create = false)
                if (wrap != null) {
                    val blob = Base64.decode(parts[2])
                    if (blob.size <= IvBytes) error("Corrupted vault key blob")
                    val cipher = Cipher.getInstance(Transformation)
                    cipher.init(Cipher.DECRYPT_MODE, wrap, GCMParameterSpec(TagBits, blob, 0, IvBytes))
                    val raw = cipher.doFinal(blob, IvBytes, blob.size - IvBytes)
                    if (raw.size != DekBytes) error("Unexpected vault DEK length")
                    return raw
                }
                // KeyStore alias is genuinely gone (e.g., keystore wiped); rotate cleanly below.
            }
        }
        val suffix = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val alias = AliasPrefix + suffix
        val wrap = requireNotNull(loadKeyStoreKey(alias, create = true))
        val fresh = ByteArray(DekBytes).also(random::nextBytes)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, wrap)
        val wrapped = Base64.encode(cipher.iv + cipher.doFinal(fresh))
        dir.mkdirs()
        val temp = File(dir, "$KeyFileName.tmp")
        temp.writeText("v2|$alias|$wrapped")
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        return fresh
    }

    private fun legacyKeyOrNull(): SecretKey? {
        legacyKey?.let { return it }
        synchronized(this) {
            legacyKey?.let { return it }
            return runCatching { loadKeyStoreKey(LegacyAlias, create = true) }
                .onFailure {
                    Timber.tag("Vault").w(it, "Legacy KeyStore unavailable")
                }
                .getOrNull()
                ?.also { legacyKey = it }
        }
    }

    private fun loadKeyStoreKey(alias: String, create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
