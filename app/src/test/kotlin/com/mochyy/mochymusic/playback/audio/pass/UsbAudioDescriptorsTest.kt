package com.mochyy.mochymusic.playback.audio.pass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioDescriptorsTest {

    private fun bytes(vararg values: Int) = values.map { it.toByte() }

    private fun device() = bytes(18, 0x01, 0x00, 0x02, 0, 0, 0, 64, 0x6b, 0x1d, 0x01, 0x00, 0, 1, 1, 2, 3, 1)

    private fun config(value: Int) = bytes(9, 0x02, 0, 0, 3, value, 0, 0x80, 50)

    private fun iface(number: Int, alt: Int, endpoints: Int, sub: Int, protocol: Int) = bytes(9, 0x04, number, alt, endpoints, 1, sub, protocol, 0)

    private fun endpoint(address: Int, attributes: Int, maxPacket: Int, sync: Int = 0) =
        bytes(9, 0x05, address, attributes, maxPacket and 0xff, maxPacket shr 8, 1, 0, sync)

    // a uac2 interface with four outputs and its own feedback endpoint, one clock, two of them on the device
    private fun uac2(config: Int, clocks: List<Int> = listOf(0x10), terminalClock: Int = 0x10): List<Byte> = buildList {
        addAll(config(config))
        addAll(iface(0, 0, 0, 1, 0x20))
        addAll(bytes(9, 0x24, 0x01, 0x00, 0x02, 8, 64, 0, 0))
        for (id in clocks) addAll(bytes(8, 0x24, 0x0a, id, 3, 7, 0, 0))
        addAll(bytes(17, 0x24, 0x02, 1, 0x01, 0x01, 0, terminalClock, 4, 0, 0, 0, 0, 0, 0, 0, 0))
        addAll(bytes(12, 0x24, 0x03, 3, 0x01, 0x03, 0, 1, terminalClock, 0, 0, 0))
        addAll(iface(1, 0, 0, 2, 0x20))
        addAll(iface(1, 1, 2, 2, 0x20))
        addAll(bytes(16, 0x24, 0x01, 1, 0, 1, 1, 0, 0, 0, 4, 0, 0, 0, 0, 0))
        addAll(bytes(6, 0x24, 0x02, 0x01, 4, 24))
        addAll(endpoint(0x01, 0x05, 200))
        addAll(endpoint(0x81, 0x11, 4))
    }

    // a basic uac3 device like apple's usb-c adapter: no class descriptors, stereo 16-bit told by the packet size
    private fun basic(config: Int): List<Byte> = buildList {
        addAll(config(config))
        addAll(iface(0, 0, 0, 1, 0x30))
        addAll(iface(1, 0, 0, 2, 0x30))
        addAll(iface(1, 1, 1, 2, 0x30))
        addAll(endpoint(0x01, 0x09, 0xC0))
    }

    private fun parse(parts: List<Byte>, active: Int? = null) = UsbAudioDescriptors.parse((device() + parts).toByteArray(), active)

    @Test
    fun playsAStereoSongOnTheFirstTwoOfFourChannels() {
        val dac = parse(uac2(1))
        val plan = UsbAudioDescriptors.plans(dac, 48_000, 2, 16).first()
        assertEquals(4, plan.channels)
        assertEquals(2, plan.sourceChannels)
        assertEquals(0x81, plan.feedbackEndpoint)
        assertEquals(0x10, plan.clockId)
    }

    @Test
    fun triesEveryOtherClockAfterTheOneTheTopologyLeadsTo() {
        val dac = parse(uac2(1, clocks = listOf(0x10, 0x11, 0x12)))
        val clocks = UsbAudioDescriptors.plans(dac, 96_000, 4, 24).map { it.clockId }
        assertEquals(listOf(0x10, 0x11, 0x12), clocks)
    }

    @Test
    fun readsABasicDeviceFromItsPacketSize() {
        val dac = parse(basic(1))
        val plan = UsbAudioDescriptors.plans(dac, 48_000, 2, 16).single()
        assertEquals(2, plan.channels)
        assertEquals(2, plan.subslot)
        assertEquals(16, plan.bitResolution)
        assertTrue("a basic device only runs at 48 khz", UsbAudioDescriptors.plans(dac, 44_100, 2, 16).isEmpty())
        assertNull("it has no volume unit to find", dac.volume)
    }

    @Test
    fun onlyReadsTheConfigurationTheDeviceRuns() {
        val both = basic(1) + uac2(2)
        assertTrue(parse(both, active = 1).settings.all { it.basic })
        assertTrue(parse(both, active = 2).settings.none { it.basic })
        // without knowing, the first configuration is the one read, never a mix of both
        assertTrue(parse(both).settings.all { it.basic })
    }

    @Test
    fun pacesAnAsyncOutputWithNoFeedbackByItsCapture() {
        val parts = buildList {
            addAll(config(1))
            addAll(iface(0, 0, 0, 1, 0x00))
            addAll(bytes(10, 0x24, 0x01, 0x00, 0x01, 30, 0, 2, 1, 2))
            addAll(iface(1, 0, 0, 2, 0x00))
            addAll(iface(1, 1, 1, 2, 0x00))
            addAll(bytes(7, 0x24, 0x01, 1, 1, 0x01, 0x00))
            addAll(bytes(11, 0x24, 0x02, 0x01, 2, 3, 24, 1, 0x80, 0xbb, 0x00))
            addAll(endpoint(0x01, 0x05, 300))
            addAll(bytes(7, 0x25, 0x01, 0x01, 0, 0, 0))
            addAll(iface(2, 0, 0, 2, 0x00))
            addAll(iface(2, 1, 1, 2, 0x00))
            addAll(bytes(7, 0x24, 0x01, 2, 1, 0x01, 0x00))
            addAll(bytes(11, 0x24, 0x02, 0x01, 2, 3, 24, 1, 0x80, 0xbb, 0x00))
            addAll(endpoint(0x82, 0x25, 300))
        }
        val dac = parse(parts)
        val plan = UsbAudioDescriptors.plans(dac, 48_000, 2, 24).single()
        assertNotNull(plan.implicit)
        val implicit = plan.implicit!!
        assertEquals(2, implicit.streamingInterface)
        assertEquals(0x82, implicit.endpoint)
        assertEquals(6, implicit.frameBytes)
        assertEquals(-1, plan.feedbackEndpoint)
    }
}
