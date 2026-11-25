// UsbAudioDescriptors.kt

package com.mochyy.mochymusic.playback.audio.pass

/** reads a usb audio dac's raw descriptors (class 1, 2, or a class 3 basic device like apple's usb-c adapter): which setting, endpoint and clock carry a song, and where its volume knob is */
internal object UsbAudioDescriptors {

    /** one way the dac can be streamed to, everything the native driver needs to set it up */
    data class Plan(
        val controlInterface: Int,
        val streamingInterface: Int,
        val alt: Int,
        val uac2: Boolean,
        /** the clock to set the rate on (uac2), and the selector in front of it with the pin that leads there, or 0 */
        val clockId: Int,
        val selectorId: Int,
        val selectorPin: Int,
        val endpoint: Int,
        val endpointAttributes: Int,
        val maxPacket: Int,
        val interval: Int,
        /** uac1: the endpoint takes a sampling rate request */
        val rateControl: Boolean,
        /** the endpoint the dac reports its own pace on, or -1 */
        val feedbackEndpoint: Int,
        val feedbackMaxPacket: Int,
        val rate: Int,
        /** the dac's channels on the wire; a song with fewer goes in the first ones and the rest stay silent */
        val channels: Int,
        /** bytes per sample on the wire, and how many of those bits are real */
        val subslot: Int,
        val bitResolution: Int,
        /** the song's own channels, what the player writes */
        val sourceChannels: Int = channels,
        /** a dac with no feedback endpoint paces off its own capture stream: that stream, or null */
        val implicit: Implicit? = null,
    )

    /** the capture stream an output paces itself by (implicit feedback): mochy runs it and counts what arrives */
    data class Implicit(val streamingInterface: Int, val alt: Int, val endpoint: Int, val maxPacket: Int, val interval: Int, val frameBytes: Int, val rateControl: Boolean)

    /** the dac's own volume control on the path from the stream to its output */
    data class VolumeUnit(val controlInterface: Int, val unitId: Int, val uac2: Boolean, val channels: List<Int>)

    data class Dac(val bcdUsb: Int, val settings: List<Setting>, val volume: VolumeUnit?, val captures: List<Setting> = emptyList())

    /** one alternate setting of a streaming interface that carries pcm out to the dac (or, for [Dac.captures], in from it) */
    data class Setting(
        val controlInterface: Int,
        val streamingInterface: Int,
        val alt: Int,
        val uac2: Boolean,
        val terminalLink: Int,
        val channels: Int,
        val subslot: Int,
        val bitResolution: Int,
        /** uac1 lists its rates; each pair is a range, a single rate is low == high */
        val rates: List<Pair<Int, Int>>,
        val endpoint: Endpoint?,
        val feedback: Endpoint?,
        /** uac2: the clock that drives it, and the selector in front of that clock with the pin leading to it, or 0 */
        val clockId: Int = 0,
        val selectorId: Int = 0,
        val selectorPin: Int = 0,
        /** a uac3 basic device: no class descriptors, the format comes from the packet size and the rate is 48 khz */
        val basic: Boolean = false,
        /** every other clock source of its control interface, tried in turn when [clockId] won't take the rate */
        val otherClocks: List<Int> = emptyList(),
    )

    data class Endpoint(val address: Int, val attributes: Int, val maxPacket: Int, val interval: Int, val syncAddress: Int, val rateControl: Boolean) {
        val isIn: Boolean get() = address and 0x80 != 0
        val isAsync: Boolean get() = (attributes shr 2) and 3 == 1
        /** an in endpoint marked as the implicit feedback for an output */
        val isImplicitFeedback: Boolean get() = (attributes shr 4) and 3 == 2
    }

    private class Entity(val id: Int, val subtype: Int, val source: List<Int>, val clock: Int, val terminalType: Int, val volumeChannels: List<Int>)

    /** [activeConfig] is the configuration the device runs in; a device with a class 3 and a class 2 configuration would otherwise mix the two */
    fun parse(bytes: ByteArray, activeConfig: Int? = null): Dac {
        var bcdUsb = 0
        var config = -1
        var wantedConfig = activeConfig
        var iface = -1
        var alt = 0
        var ifaceClass = 0
        var ifaceSub = 0
        var protocol = 0
        var control = -1
        val controlProtocol = HashMap<Int, Int>()
        val headers = mutableSetOf<Int>()
        val entities = HashMap<Pair<Int, Int>, Entity>()
        val outs = mutableListOf<Setting>()
        val ins = mutableListOf<Setting>()
        var current: Setting? = null
        var endpoints = mutableListOf<Endpoint>()

        fun finish() {
            val setting = current ?: return
            current = null
            val found = endpoints
            endpoints = mutableListOf()
            val iso = found.filter { it.attributes and 3 == 1 }
            val out = iso.firstOrNull { !it.isIn && (it.attributes shr 4) and 3 == 0 }
            val input = iso.firstOrNull { it.isIn && (it.attributes shr 4) and 3 != 1 }
            val data = out ?: input ?: return
            val feedback = if (out == null) null else iso.firstOrNull { ep -> ep.isIn && ((ep.attributes shr 4) and 3 == 1 || ep.address == out.syncAddress) }
            // a basic device has no class descriptors at all: its format is fixed by its packet size
            val full = if (setting.basic) basicFormat(setting, data) ?: return else setting
            val done = full.copy(endpoint = data, feedback = feedback)
            if (out != null) outs += done else ins += done
        }

        var offset = 0
        while (offset + 2 <= bytes.size) {
            val length = bytes[offset].toInt() and 0xff
            if (length < 2 || offset + length > bytes.size) break
            val d = bytes.copyOfRange(offset, offset + length)
            offset += length
            fun u(i: Int) = if (i < d.size) d[i].toInt() and 0xff else 0
            fun u16(i: Int) = u(i) or (u(i + 1) shl 8)
            fun u24(i: Int) = u16(i) or (u(i + 2) shl 16)
            val type = u(1)
            if (type == 0x01) bcdUsb = u16(2)
            if (type == 0x02) {
                finish()
                config = u(5)
                if (wantedConfig == null) wantedConfig = config
                continue
            }
            // only the configuration the device is running in counts
            if (config != -1 && config != wantedConfig) continue
            when (type) {
                0x04 -> {
                    finish()
                    iface = u(2)
                    alt = u(3)
                    ifaceClass = u(5)
                    ifaceSub = u(6)
                    protocol = u(7)
                    if (ifaceClass == 1 && ifaceSub == 1) {
                        control = iface
                        controlProtocol[iface] = protocol
                    }
                    if (ifaceClass == 1 && ifaceSub == 2 && alt > 0 && control >= 0) {
                        val family = controlProtocol[control] ?: 0
                        // class 3 without its own descriptors is a basic device; a full class 3 one describes itself in ways this doesn't read
                        val basic = family == 0x30 && control !in headers
                        if (family != 0x30 || basic) {
                            current = Setting(control, iface, alt, family == 0x20, 0, 0, 0, 0, emptyList(), null, null, basic = basic)
                        }
                    }
                }
                0x05 -> if (current != null && length >= 7) {
                    val packet = u16(4)
                    endpoints += Endpoint(
                        address = u(2),
                        attributes = u(3),
                        // high-bandwidth endpoints carry up to three transactions a microframe
                        maxPacket = (packet and 0x7ff) * (1 + ((packet shr 11) and 3)),
                        interval = u(6),
                        syncAddress = if (length >= 9) u(8) else 0,
                        rateControl = false,
                    )
                }
                0x25 -> if (current != null && length >= 4 && u(2) == 1 && endpoints.isNotEmpty() && current?.uac2 == false) {
                    val last = endpoints.removeAt(endpoints.lastIndex)
                    endpoints += last.copy(rateControl = u(3) and 1 != 0)
                }
                0x24 -> when {
                    ifaceClass == 1 && ifaceSub == 1 -> {
                        if (u(2) == 0x01) headers += iface
                        controlEntity(d, protocol == 0x20)?.let { entities[iface to it.id] = it }
                    }
                    current != null && current?.basic == false -> current = streamingEntity(current!!, d, ::u, ::u24)
                }
            }
        }
        finish()

        fun usable(s: Setting) = s.endpoint != null && s.channels in 1..MaxChannels && s.subslot in 2..4 && s.bitResolution in 16..32
        val pcm = outs.filter(::usable).map { s -> if (s.uac2) withClock(s, entities) else s }
        val captures = ins.filter(::usable).map { s -> if (s.uac2) withClock(s, entities) else s }
        return Dac(bcdUsb, pcm, volumeUnit(pcm, entities), captures)
    }

    // uac3 basic devices: 48 khz, 16 or 24 bits, mono or stereo, told apart only by the packet size (a frame more when async)
    private fun basicFormat(s: Setting, endpoint: Endpoint): Setting? {
        val packet = endpoint.maxPacket
        val (channels, bytes) = BasicPackets[packet] ?: return null
        return s.copy(channels = channels, subslot = bytes, bitResolution = bytes * 8, rates = listOf(48_000 to 48_000), terminalLink = 0)
    }

    private val BasicPackets = mapOf(
        0x60 to (1 to 2), 0x90 to (1 to 3), 0xC0 to (2 to 2), 0x120 to (2 to 3),
        0x62 to (1 to 2), 0x93 to (1 to 3), 0xC4 to (2 to 2), 0x126 to (2 to 3),
    )

    private fun controlEntity(d: ByteArray, uac2: Boolean): Entity? {
        fun u(i: Int) = if (i < d.size) d[i].toInt() and 0xff else 0
        fun u16(i: Int) = u(i) or (u(i + 1) shl 8)
        val subtype = u(2)
        return when (subtype) {
            // input terminal: on uac2 it names the clock that drives it
            0x02 -> Entity(u(3), subtype, emptyList(), if (uac2) u(7) else 0, u16(4), emptyList())
            0x03 -> Entity(u(3), subtype, listOf(u(7)), if (uac2) u(8) else 0, u16(4), emptyList())
            0x04 -> Entity(u(3), subtype, (0 until u(4)).map { u(5 + it) }, 0, 0, emptyList())
            0x05 -> Entity(u(3), subtype, (0 until u(4)).map { u(5 + it) }, 0, 0, emptyList())
            0x06 -> Entity(u(3), subtype, listOf(u(4)), 0, 0, featureVolume(d, uac2))
            // clock source, clock selector and multiplier only exist on uac2
            0x0a -> if (uac2) Entity(u(3), subtype, emptyList(), 0, 0, emptyList()) else null
            0x0b -> if (uac2) Entity(u(3), subtype, (0 until u(4)).map { u(5 + it) }, 0, 0, emptyList()) else null
            0x0c -> if (uac2) Entity(u(3), subtype, listOf(u(4)), 0, 0, emptyList()) else null
            else -> null
        }
    }

    /** the channels whose volume the host can set: 0 is the master */
    private fun featureVolume(d: ByteArray, uac2: Boolean): List<Int> {
        fun u(i: Int) = if (i < d.size) d[i].toInt() and 0xff else 0
        val size = if (uac2) 4 else u(5)
        val start = if (uac2) 5 else 6
        if (size == 0) return emptyList()
        val count = (d.size - start - 1) / size
        return (0 until count).filter { ch ->
            val first = u(start + ch * size)
            if (uac2) (first shr 2) and 3 == 3 else first and 0x02 != 0
        }
    }

    private fun streamingEntity(s: Setting, d: ByteArray, u: (Int) -> Int, u24: (Int) -> Int): Setting = when {
        u(2) == 1 && s.uac2 -> if (u(5) == 1 && (u(6) and 1) != 0) s.copy(terminalLink = u(3), channels = u(10)) else s.copy(terminalLink = u(3), channels = 0)
        u(2) == 1 -> if ((u(5) or (u(6) shl 8)) == 1) s.copy(terminalLink = u(3)) else s.copy(terminalLink = u(3), subslot = -1)
        u(2) == 2 && u(3) == 1 && s.uac2 -> s.copy(subslot = u(4), bitResolution = u(5))
        u(2) == 2 && u(3) == 1 && s.subslot >= 0 -> {
            val count = u(7)
            val rates = if (count == 0) listOf(u24(8) to u24(11)) else (0 until count).map { val r = u24(8 + it * 3); r to r }
            s.copy(channels = u(4), subslot = u(5), bitResolution = u(6), rates = rates)
        }
        else -> s
    }

    /** follows a setting's terminal to its clock; a selector with several inputs is pointed at the first clock source behind it */
    private fun withClock(s: Setting, entities: Map<Pair<Int, Int>, Entity>): Setting {
        var id = entities[s.controlInterface to s.terminalLink]?.clock ?: 0
        var selector = 0
        var pin = 0
        for (step in 0 until 4) {
            val entity = entities[s.controlInterface to id] ?: break
            when (entity.subtype) {
                0x0b -> {
                    val index = entity.source.indexOfFirst { src -> entities[s.controlInterface to src]?.subtype == 0x0a }.coerceAtLeast(0)
                    selector = entity.id
                    pin = index + 1
                    id = entity.source.getOrElse(index) { 0 }
                }
                0x0c -> id = entity.source.firstOrNull() ?: 0
                else -> break
            }
        }
        val isSource = entities[s.controlInterface to id]?.subtype == 0x0a
        val others = entities.values.filter { it.subtype == 0x0a && entities[s.controlInterface to it.id] === it && it.id != id }.map { it.id }.sorted()
        return s.copy(clockId = if (isSource) id else 0, selectorId = selector, selectorPin = pin, otherClocks = others)
    }

    /** the one feature unit with a settable volume between the stream's terminal and an output; anything ambiguous means no volume */
    private fun volumeUnit(settings: List<Setting>, entities: Map<Pair<Int, Int>, Entity>): VolumeUnit? {
        val setting = settings.firstOrNull { !it.basic } ?: return null
        val control = setting.controlInterface
        val outputs = entities.values.filter { it.subtype == 0x03 && it.terminalType != 0x0101 && entities[control to it.id] === it }
        val found = mutableSetOf<VolumeUnit>()
        for (output in outputs) {
            var id = output.source.firstOrNull() ?: continue
            var unit: VolumeUnit? = null
            val seen = mutableSetOf<Int>()
            while (id != setting.terminalLink && seen.add(id)) {
                val entity = entities[control to id] ?: break
                if (unit == null && entity.subtype == 0x06 && entity.volumeChannels.isNotEmpty()) {
                    val channels = if (0 in entity.volumeChannels) listOf(0) else entity.volumeChannels.filter { it in 1..setting.channels }
                    unit = VolumeUnit(control, entity.id, setting.uac2, channels)
                }
                // a mixer or selector could route from somewhere else: guessing could turn the wrong thing down
                if (entity.source.size != 1) break
                id = entity.source.first()
            }
            if (id == setting.terminalLink && unit != null) found += unit
        }
        return found.singleOrNull()
    }

    /** every way this dac can carry a song of this rate, channels and depth, best first: its own channel count, then the narrowest container that holds it whole, then the other clocks */
    fun plans(dac: Dac, rate: Int, channels: Int, sourceBits: Int): List<Plan> =
        dac.settings
            .filter { it.channels >= channels && it.bitResolution >= sourceBits && it.subslot * 8 >= it.bitResolution }
            .filter { s -> s.uac2 || s.rates.any { (low, high) -> rate in low..high } }
            .sortedWith(compareBy({ it.channels != channels }, { it.channels }, { it.subslot }, { it.bitResolution }))
            .flatMap { s -> planFor(dac, s, rate, channels) }

    private fun planFor(dac: Dac, s: Setting, rate: Int, sourceChannels: Int): List<Plan> {
        val data = s.endpoint ?: return emptyList()
        if (data.interval !in 1..4 || data.maxPacket == 0) return emptyList()
        // an async output with no feedback endpoint paces itself off the dac's own capture stream, which mochy runs alongside
        val implicit = if (data.isAsync && s.feedback == null) implicitFor(dac, s, rate) ?: return emptyList() else null
        // uac1 with no rate request only plays the one rate it lists
        if (!s.uac2 && !data.rateControl && s.rates != listOf(rate to rate)) return emptyList()
        if (s.uac2 && s.clockId == 0) return emptyList()
        val base = Plan(
            controlInterface = s.controlInterface,
            streamingInterface = s.streamingInterface,
            alt = s.alt,
            uac2 = s.uac2,
            clockId = s.clockId,
            selectorId = s.selectorId,
            selectorPin = s.selectorPin,
            endpoint = data.address,
            endpointAttributes = data.attributes,
            maxPacket = data.maxPacket,
            interval = data.interval,
            rateControl = data.rateControl,
            feedbackEndpoint = s.feedback?.address ?: -1,
            feedbackMaxPacket = s.feedback?.maxPacket ?: 0,
            rate = rate,
            channels = s.channels,
            subslot = s.subslot,
            bitResolution = s.bitResolution,
            sourceChannels = sourceChannels,
            implicit = implicit,
        )
        // some dacs describe their clocks in ways that don't lead to the one that takes the rate, so every other clock gets a turn
        return listOf(base) + s.otherClocks.map { base.copy(clockId = it, selectorId = 0, selectorPin = 0) }
    }

    // the capture stream an output follows: one marked as its implicit feedback, or the dac's first capture that runs at this rate
    private fun implicitFor(dac: Dac, out: Setting, rate: Int): Implicit? {
        val fits = dac.captures.filter { c -> c.controlInterface == out.controlInterface && (c.uac2 || c.rates.any { (low, high) -> rate in low..high }) }
        val capture = fits.firstOrNull { it.endpoint?.isImplicitFeedback == true } ?: fits.firstOrNull() ?: return null
        val ep = capture.endpoint ?: return null
        if (ep.interval !in 1..4 || ep.maxPacket == 0) return null
        return Implicit(capture.streamingInterface, capture.alt, ep.address, ep.maxPacket, ep.interval, capture.channels * capture.subslot, ep.rateControl)
    }

    // past 7.1 a stereo song would need too much padding to be worth a bit-perfect path
    private const val MaxChannels = 8
}
