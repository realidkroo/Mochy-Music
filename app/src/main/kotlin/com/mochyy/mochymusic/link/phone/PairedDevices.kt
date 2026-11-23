// PairedDevices.kt

package com.mochyy.mochymusic.link.phone

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.mochyy.mochymusic.link.DeviceKind
import com.mochyy.mochymusic.modules.security.SecretGuard
import com.mochyy.mochymusic.utils.SecretVault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

// a device this phone is paired with; the key is what both sides show on every call
@Immutable
data class PairedDevice(
    val id: String,
    val name: String,
    val key: String,
    val kind: DeviceKind,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val lastSyncAt: Long = 0L,
    // where a phone or tablet's own link server was last found; a tv says where it is on every hello instead
    val host: String? = null,
    val port: Int = 0,
    // this phone may play, skip and change settings there, and for another phone the other way round too
    val remoteControl: Boolean = true,
    // steering starts by itself as soon as the device turns up
    val autoControl: Boolean = false,
    // a tv may stream this phone's downloads and song files
    val streamLocal: Boolean = true,
    // a tv's library is replaced by this phone's and kept in step
    val mirrorLibrary: Boolean = true,
) {
    // only a paired tv syncing or streaming from this phone can use the link while mochy is closed;
    // phones, tablets and computers steering this phone need mochy's activity alive anyway
    val needsBackgroundLink: Boolean
        get() = kind.isTv && (mirrorLibrary || streamLocal)
}

// a paired device that's around right now, with where it listens for this phone
@Immutable
data class LiveDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val host: String,
    val port: Int,
    val key: String,
    val seenAt: Long,
)

// paired devices as json in shared preferences (the keys sealed), so the boot receiver can read them without hilt
internal object PairedDeviceStore {
    // the names are from when only tvs could pair, kept so nobody has to pair again
    const val Prefs = "mochy_link_tvs"
    private const val ListKey = "tvs"

    fun read(prefs: SharedPreferences): List<PairedDevice> {
        val array = runCatching { JSONArray(prefs.getString(ListKey, null) ?: return emptyList()) }.getOrNull() ?: return emptyList()
        var needsDomainUpgrade = false
        val devices = (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val storedKey = json.optString("key")
            val openedLink = SecretVault.open(storedKey, SecretVault.LinkDomain)
            val key = if (openedLink != null) {
                if (SecretVault.needsUpgrade(storedKey)) needsDomainUpgrade = true
                openedLink
            } else {
                needsDomainUpgrade = true
                SecretVault.open(storedKey, SecretVault.DefaultDomain)
            }?.ifBlank { null } ?: return@mapNotNull null
            PairedDevice(
                id = json.optString("id"),
                name = json.optString("name"),
                // sealed by this phone's vault under LinkDomain; one from another device can't be opened
                key = key,
                // saved before kinds existed, so it can only be a tv
                kind = DeviceKind.fromWire(json.optString("kind").ifBlank { null }, DeviceKind.Tv),
                pairedAt = json.optLong("pairedAt"),
                lastSeenAt = json.optLong("lastSeenAt"),
                lastSyncAt = json.optLong("lastSyncAt"),
                host = json.optString("host").ifBlank { null },
                port = json.optInt("port"),
                remoteControl = json.optBoolean("remoteControl", true),
                autoControl = json.optBoolean("autoControl", false),
                streamLocal = json.optBoolean("streamLocal", true),
                mirrorLibrary = json.optBoolean("mirrorLibrary", true),
            )
        }
        SecretGuard.register("link_devices", *devices.map { it.key }.toTypedArray())
        if (needsDomainUpgrade && devices.isNotEmpty()) write(prefs, devices)
        return devices
    }

    fun write(prefs: SharedPreferences, list: List<PairedDevice>) {
        SecretGuard.register("link_devices", *list.map { it.key }.toTypedArray())
        val array = JSONArray(
            list.map {
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("key", SecretVault.seal(it.key, SecretVault.LinkDomain))
                    .put("kind", it.kind.wire)
                    .put("pairedAt", it.pairedAt)
                    .put("lastSeenAt", it.lastSeenAt)
                    .put("lastSyncAt", it.lastSyncAt)
                    .put("host", it.host.orEmpty())
                    .put("port", it.port)
                    .put("remoteControl", it.remoteControl)
                    .put("autoControl", it.autoControl)
                    .put("streamLocal", it.streamLocal)
                    .put("mirrorLibrary", it.mirrorLibrary)
            },
        )
        prefs.edit { putString(ListKey, array.toString()) }
    }

    fun hasAny(prefs: SharedPreferences): Boolean {
        val saved = prefs.getString(ListKey, null)
        return !saved.isNullOrBlank() && saved != "[]"
    }

    // checks without unsealing keys whether any paired device can actually use the link in the background
    fun hasBackgroundCapable(prefs: SharedPreferences): Boolean = hasBackgroundCapableJson(prefs.getString(ListKey, null))

    internal fun hasBackgroundCapableJson(raw: String?): Boolean {
        if (raw.isNullOrBlank() || raw == "[]") return false
        val array = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return false
        for (element in array) {
            val json = runCatching { element.jsonObject }.getOrNull() ?: continue
            val kindWire = json["kind"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            val kind = DeviceKind.fromWire(kindWire, DeviceKind.Tv)
            val mirrorLibrary = json["mirrorLibrary"]?.jsonPrimitive?.booleanOrNull ?: true
            val streamLocal = json["streamLocal"]?.jsonPrimitive?.booleanOrNull ?: true
            if (kind.isTv && (mirrorLibrary || streamLocal)) {
                return true
            }
        }
        return false
    }
}
