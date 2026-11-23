package com.mochyy.mochymusic.link.phone

import com.mochyy.mochymusic.link.DeviceKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** checks which paired devices actually justify keeping the background link service alive */
class PairedDeviceBackgroundPolicyTest {

    @Test
    fun `only tvs with library sync or local streaming need the background link service`() {
        val phone = PairedDevice("p1", "Pixel", "k", DeviceKind.Phone, 0L, 0L, remoteControl = true)
        val desktop = PairedDevice("d1", "Mac", "k", DeviceKind.Desktop, 0L, 0L, remoteControl = true)
        val tvSilent = PairedDevice("t1", "Living Room TV", "k", DeviceKind.Tv, 0L, 0L, streamLocal = false, mirrorLibrary = false)
        val tvStream = PairedDevice("t2", "Living Room TV", "k", DeviceKind.Tv, 0L, 0L, streamLocal = true, mirrorLibrary = false)
        val tvMirror = PairedDevice("t3", "Living Room TV", "k", DeviceKind.Tv, 0L, 0L, streamLocal = false, mirrorLibrary = true)

        assertFalse(phone.needsBackgroundLink)
        assertFalse(desktop.needsBackgroundLink)
        assertFalse(tvSilent.needsBackgroundLink)
        assertTrue(tvStream.needsBackgroundLink)
        assertTrue(tvMirror.needsBackgroundLink)
    }

    @Test
    fun `json precheck ignores phones and computers and detects background-capable tvs without unsealing keys`() {
        assertFalse(PairedDeviceStore.hasBackgroundCapableJson(null))
        assertFalse(PairedDeviceStore.hasBackgroundCapableJson("[]"))
        assertFalse(
            PairedDeviceStore.hasBackgroundCapableJson(
                """[{"id":"p1","name":"Phone","kind":"phone","streamLocal":true,"mirrorLibrary":true}]""",
            ),
        )
        assertFalse(
            PairedDeviceStore.hasBackgroundCapableJson(
                """[{"id":"d1","name":"PC","kind":"desktop","streamLocal":true,"mirrorLibrary":true}]""",
            ),
        )
        assertFalse(
            PairedDeviceStore.hasBackgroundCapableJson(
                """[{"id":"t1","name":"TV","kind":"tv","streamLocal":false,"mirrorLibrary":false}]""",
            ),
        )
        assertTrue(
            PairedDeviceStore.hasBackgroundCapableJson(
                """[{"id":"t1","name":"TV","kind":"tv","streamLocal":true,"mirrorLibrary":false}]""",
            ),
        )
        // legacy records written before kind existed represent TVs
        assertTrue(
            PairedDeviceStore.hasBackgroundCapableJson(
                """[{"id":"t0","name":"Old TV"}]""",
            ),
        )
    }
}
