// modules that bring their own code in a zip: only an apk signed with mochy's own key gets in, it's copied into the app's files, and its code runs behind the bridge (docs/notes/modules.md)

package com.mochyy.mochymusic.modules.code

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.mochyy.mochymusic.module.bridge.BridgeOnlyParent
import com.mochyy.mochymusic.module.bridge.BridgedModule
import com.mochyy.mochymusic.module.bridge.shared.ModuleBridge
import com.mochyy.mochymusic.modules.ModuleRegistry
import dalvik.system.DexClassLoader
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal object CodeModules {

    /** a module running from its zip's code, and the folder that code sits in */
    class Loaded(val module: BridgedModule, val folder: String)

    /** copies the apk at [path] in the zip out to the cache to be checked; null when it isn't there or is too big */
    fun extract(context: Context, uri: Uri, path: String): File? {
        val dir = File(context.cacheDir, "code_check").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(dir, "module.apk")
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.take(MaxZipEntries).firstOrNull { !it.isDirectory && it.name.trimStart('/') == path } ?: return null
                return target.takeIf { copyCapped(zip, it) }
            }
        }
        return null
    }

    /** true when the apk is signed with exactly the keys this copy of mochy is signed with */
    fun signedLikeMochy(context: Context, apk: File): Boolean =
        signedLikeMochy(context, context.packageManager.getPackageArchiveInfo(apk.path, SignatureFlags))

    /** puts a checked apk in place under [key] and starts it; null when its code can't run in this mochy */
    fun install(context: Context, key: String, apk: File): Loaded? {
        // each copy gets a folder of its own, since a native library stays open in the class loader that loaded it and one path can't be loaded twice
        val folder = "${safe(key)}/${System.currentTimeMillis()}"
        val target = File(root(context), "$folder/$ApkName")
        target.parentFile?.mkdirs()
        if (!apk.renameTo(target)) {
            apk.copyTo(target, overwrite = true)
            apk.delete()
        }
        val loaded = load(context, folder)
        if (loaded == null) File(root(context), folder).deleteRecursively()
        return loaded
    }

    /** starts the module in [folder], checking its signature again first since a restored backup can bring any file back; null when it can't run here */
    fun load(context: Context, folder: String): Loaded? = runCatching {
        val dir = File(root(context), folder)
        val apk = File(dir, ApkName)
        if (!apk.isFile) return null
        // android 14 refuses to load code from a file the app could still write to
        apk.setReadOnly()
        val info = context.packageManager.getPackageArchiveInfo(apk.path, SignatureFlags or PackageManager.GET_META_DATA)
        if (!signedLikeMochy(context, info)) {
            Timber.tag(Tag).w("Skipping the module in $folder: not signed like Mochy")
            return null
        }
        val entry = info?.applicationInfo?.metaData?.getString(ModuleRegistry.EntryMetaKey) ?: return null
        val optimized = File(context.codeCacheDir, "code_modules").apply { mkdirs() }
        val loader = DexClassLoader(apk.path, optimized.path, nativeLibs(apk, dir)?.path, BridgeOnlyParent(CodeModules::class.java.classLoader!!))
        val bridge = loader.loadClass(BridgedModule.ModuleSideClass).getDeclaredConstructor().newInstance() as ModuleBridge
        Loaded(BridgedModule.open(bridge, entry), folder)
    }.onFailure { Timber.tag(Tag).w(it, "Couldn't start the module in $folder") }.getOrNull()

    /** deletes every copy but the ones in [keep]: the ones updates replaced and modules that were removed */
    fun tidy(context: Context, keep: Set<String>) {
        root(context).listFiles()?.forEach { key ->
            key.listFiles()?.forEach { copy -> if ("${key.name}/${copy.name}" !in keep) copy.deleteRecursively() }
            if (key.list().isNullOrEmpty()) key.delete()
        }
    }

    /** takes a removed module's code away; a copy still running keeps what it already opened */
    fun delete(context: Context, folder: String) {
        File(root(context), folder.substringBefore('/')).deleteRecursively()
    }

    private fun root(context: Context) = File(context.filesDir, "code_modules")

    private fun safe(key: String) = key.lowercase().replace(Regex("[^a-z0-9._-]"), "-").ifBlank { "module" }

    private fun signedLikeMochy(context: Context, theirs: PackageInfo?): Boolean {
        val own = signers(context.packageManager.getPackageInfo(context.packageName, SignatureFlags))
        return own.isNotEmpty() && own == signers(theirs)
    }

    // the certificates it's signed with right now, as fingerprints
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo?): Set<String> {
        info ?: return emptySet()
        val signatures = (if (Build.VERSION.SDK_INT >= 28) currentSigners(info) else null) ?: info.signatures?.toList().orEmpty()
        return signatures.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    // a key that was rotated keeps its history, only the newest one counts
    @RequiresApi(28)
    private fun currentSigners(info: PackageInfo): List<Signature>? {
        val signing = info.signingInfo ?: return null
        return if (signing.hasMultipleSigners()) signing.apkContentsSigners?.toList() else signing.signingCertificateHistory?.takeLast(1)
    }

    // the apk's native libraries for this phone's abi, unpacked once next to it; null when it brings none
    private fun nativeLibs(apk: File, dir: File): File? = ZipFile(apk).use { zip ->
        val abis = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
        val names = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".so") }.map { it.name }.toList()
        val abi = abis.firstOrNull { abi -> names.any { it.startsWith("lib/$abi/") } } ?: return null
        val target = File(dir, "lib/$abi")
        if (target.isDirectory) return target
        // unpacked beside and moved in whole, so a crash halfway never leaves half a folder that looks done
        val staging = File(dir, "lib/.$abi").apply {
            deleteRecursively()
            mkdirs()
        }
        names.filter { it.startsWith("lib/$abi/") }.forEach { name ->
            zip.getInputStream(zip.getEntry(name)).use { input -> File(staging, name.substringAfterLast('/')).outputStream().use { input.copyTo(it) } }
        }
        target.takeIf { staging.renameTo(it) }
    }

    private fun copyCapped(input: InputStream, target: File): Boolean {
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        target.outputStream().use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return true
                total += read
                if (total > MaxApkBytes) break
                out.write(buffer, 0, read)
            }
        }
        target.delete()
        return false
    }

    @Suppress("DEPRECATION")
    private val SignatureFlags = PackageManager.GET_SIGNATURES or (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val Tag = "CodeModules"
    private const val ApkName = "module.apk"
    private const val MaxZipEntries = 200
    // a module's code with its native libraries is a few megabytes, far past this is something else
    private const val MaxApkBytes = 64L * 1024 * 1024
}
