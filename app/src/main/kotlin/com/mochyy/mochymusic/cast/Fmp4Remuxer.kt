// Fmp4Remuxer.kt

package com.mochyy.mochymusic.cast

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns a YouTube DASH fragmented MP4 audio stream (ftyp=dash, moov+mvex, moof+mdat) into a plain,
 * faststart .m4a file (ftyp=M4A, moov with full sample tables at the front, single mdat) without
 * re-encoding a single sample. Samsung and other DLNA TVs often reject fMP4 audio with "format not
 * supported", while every TV and speaker plays a plain faststart M4A natively and seeks cleanly.
 */
object Fmp4Remuxer {

    private const val SamplesPerChunk = 43 // ~1 second at 44.1 kHz AAC-LC (1024 samples/frame)
    private const val MaxBoxScan = 64 * 1024 * 1024L

    /**
     * Remuxes [input] into [output] when [input] is a fragmented audio MP4.
     * Returns true if [output] was written as a plain faststart M4A; false if [input] is already
     * a standard non-fragmented MP4 (or not a fragmented audio MP4 we can safely remux).
     */
    fun remuxIfFragmented(input: File, output: File): Boolean {
        val fileLen = input.length()
        if (fileLen < 16L) return false

        RandomAccessFile(input, "r").use { raf ->
            var timescale = 0
            var stsdBox: ByteArray? = null
            var defaultTrexDuration = 0
            var defaultTrexSize = 0
            var hasMvex = false
            var audioTrackId = 1
            var stszSampleCount = 0

            data class SampleSlice(val fileOffset: Long, val size: Int, val duration: Int)
            val slices = ArrayList<SampleSlice>(4096)

            // First pass: walk top-level boxes for moov and every moof+mdat pair.
            var pos = 0L
            var pendingMoofStart = -1L
            var pendingBaseOffset: Long? = null
            var pendingRuns: List<TrunInfo>? = null

            while (pos + 8L <= fileLen) {
                val box = readBoxHeader(raf, pos, fileLen) ?: break
                when (box.type) {
                    "moov" -> {
                        if (box.payloadSize > MaxBoxScan) return false
                        val moovBytes = readBytes(raf, box.payloadOffset, box.payloadSize.toInt())
                        val parsed = parseMoov(moovBytes) ?: return false
                        timescale = parsed.timescale
                        stsdBox = parsed.stsdBox
                        defaultTrexDuration = parsed.defaultSampleDuration
                        defaultTrexSize = parsed.defaultSampleSize
                        hasMvex = parsed.hasMvex
                        audioTrackId = parsed.trackId
                        stszSampleCount = parsed.stszSampleCount
                        // Already a plain non-fragmented MP4: nothing to remux.
                        if (!hasMvex && stszSampleCount > 0) return false
                    }
                    "moof" -> {
                        if (box.payloadSize > MaxBoxScan) return false
                        val moofBytes = readBytes(raf, box.payloadOffset, box.payloadSize.toInt())
                        val parsed = parseMoof(
                            moofBytes = moofBytes,
                            targetTrackId = audioTrackId,
                            defaultTrexDuration = defaultTrexDuration,
                            defaultTrexSize = defaultTrexSize,
                        )
                        pendingMoofStart = box.start
                        pendingBaseOffset = parsed?.baseDataOffset
                        pendingRuns = parsed?.runs
                    }
                    "mdat" -> {
                        val runs = pendingRuns
                        if (runs != null && pendingMoofStart >= 0L) {
                            var cursor = box.payloadOffset
                            for (run in runs) {
                                val runStart = if (run.dataOffset != null) {
                                    (pendingBaseOffset ?: pendingMoofStart) + run.dataOffset
                                } else {
                                    cursor
                                }
                                var offset = runStart
                                for (i in 0 until run.sampleSizes.size) {
                                    val size = run.sampleSizes[i]
                                    val dur = run.sampleDurations[i]
                                    if (size <= 0 || offset < 0L || offset + size > fileLen) return false
                                    slices.add(SampleSlice(offset, size, dur))
                                    offset += size
                                }
                                cursor = offset
                            }
                            pendingRuns = null
                            pendingMoofStart = -1L
                            pendingBaseOffset = null
                        }
                    }
                }
                if (box.end <= pos) break
                pos = box.end
            }

            val stsd = stsdBox ?: return false
            if (timescale <= 0 || slices.isEmpty()) return false

            val totalSamples = slices.size
            val totalMdatBytes = slices.sumOf { it.size.toLong() }
            if (totalMdatBytes <= 0L || totalMdatBytes > Int.MAX_VALUE.toLong() - 1024L * 1024L) return false
            val totalDuration = slices.sumOf { it.duration.toLong() }

            // Build stts (run-length encoded sample durations)
            val sttsEntries = ArrayList<Pair<Int, Int>>()
            var runDur = slices[0].duration
            var runCount = 1
            for (i in 1 until totalSamples) {
                val d = slices[i].duration
                if (d == runDur) {
                    runCount++
                } else {
                    sttsEntries.add(runCount to runDur)
                    runDur = d
                    runCount = 1
                }
            }
            sttsEntries.add(runCount to runDur)

            // Group samples into ~1s chunks so hardware TV parsers never hit per-chunk buffer caps.
            val chunkCount = (totalSamples + SamplesPerChunk - 1) / SamplesPerChunk
            val chunkByteSizes = LongArray(chunkCount)
            for (i in 0 until totalSamples) {
                chunkByteSizes[i / SamplesPerChunk] += slices[i].size.toLong()
            }

            val fullChunks = totalSamples / SamplesPerChunk
            val remainder = totalSamples % SamplesPerChunk
            val stscEntries = ArrayList<Triple<Int, Int, Int>>(2)
            if (fullChunks > 0) stscEntries.add(Triple(1, SamplesPerChunk, 1))
            if (remainder > 0) stscEntries.add(Triple(fullChunks + 1, remainder, 1))

            val ftyp = buildFtyp()
            // Build moov twice: first with placeholder stco offsets to learn its exact byte length,
            // then with the real mdat offsets so moov sits right at the front (faststart).
            val dummyOffsets = IntArray(chunkCount)
            val moovSize = buildMoov(
                timescale = timescale,
                totalDuration = totalDuration,
                stsdBox = stsd,
                sttsEntries = sttsEntries,
                stscEntries = stscEntries,
                sampleSizes = IntArray(totalSamples) { slices[it].size },
                chunkOffsets = dummyOffsets,
            ).size

            val mdatDataStart = ftyp.size.toLong() + moovSize.toLong() + 8L
            if (mdatDataStart + totalMdatBytes > 0xFFFFFFFFL) return false

            val realOffsets = IntArray(chunkCount)
            var chunkPos = mdatDataStart
            for (c in 0 until chunkCount) {
                realOffsets[c] = chunkPos.toInt()
                chunkPos += chunkByteSizes[c]
            }

            val sampleSizes = IntArray(totalSamples) { slices[it].size }
            val moov = buildMoov(
                timescale = timescale,
                totalDuration = totalDuration,
                stsdBox = stsd,
                sttsEntries = sttsEntries,
                stscEntries = stscEntries,
                sampleSizes = sampleSizes,
                chunkOffsets = realOffsets,
            )
            if (moov.size != moovSize) return false

            BufferedOutputStream(FileOutputStream(output), 64 * 1024).use { out ->
                out.write(ftyp)
                out.write(moov)
                // mdat header
                val mdatHeader = ByteArray(8)
                ByteBuffer.wrap(mdatHeader).order(ByteOrder.BIG_ENDIAN).apply {
                    putInt((8L + totalMdatBytes).toInt())
                    put('m'.code.toByte()); put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte())
                }
                out.write(mdatHeader)

                // Copy contiguous runs of slices in large reads for speed
                val copyBuf = ByteArray(64 * 1024)
                var idx = 0
                while (idx < totalSamples) {
                    val runStart = slices[idx].fileOffset
                    var runLen = slices[idx].size.toLong()
                    var next = idx + 1
                    while (next < totalSamples && slices[next].fileOffset == runStart + runLen) {
                        runLen += slices[next].size.toLong()
                        next++
                    }
                    raf.seek(runStart)
                    var remaining = runLen
                    while (remaining > 0L) {
                        val toRead = minOf(copyBuf.size.toLong(), remaining).toInt()
                        raf.readFully(copyBuf, 0, toRead)
                        out.write(copyBuf, 0, toRead)
                        remaining -= toRead
                    }
                    idx = next
                }
                out.flush()
            }
            return true
        }
    }

    private data class BoxRef(val start: Long, val payloadOffset: Long, val end: Long, val type: String) {
        val payloadSize: Long get() = end - payloadOffset
    }

    private fun readBoxHeader(raf: RandomAccessFile, pos: Long, limit: Long): BoxRef? {
        if (pos + 8L > limit) return null
        raf.seek(pos)
        val header = ByteArray(8)
        raf.readFully(header)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val size32 = bb.int.toLong() and 0xFFFFFFFFL
        val type = String(header, 4, 4, Charsets.ISO_8859_1)
        val (end, payloadOffset) = when (size32) {
            0L -> limit to (pos + 8L)
            1L -> {
                if (pos + 16L > limit) return null
                val ext = ByteArray(8)
                raf.readFully(ext)
                val size64 = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).long
                if (size64 < 16L) return null
                (pos + size64) to (pos + 16L)
            }
            else -> {
                if (size32 < 8L) return null
                (pos + size32) to (pos + 8L)
            }
        }
        if (end > limit || payloadOffset > end) return null
        return BoxRef(pos, payloadOffset, end, type)
    }

    private fun readBytes(raf: RandomAccessFile, offset: Long, length: Int): ByteArray {
        val buf = ByteArray(length)
        raf.seek(offset)
        raf.readFully(buf)
        return buf
    }

    private data class MemBox(val start: Int, val payloadStart: Int, val end: Int, val type: String) {
        val size: Int get() = end - start
    }

    private fun childBoxes(data: ByteArray, from: Int, to: Int): List<MemBox> {
        val out = ArrayList<MemBox>()
        var p = from
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        while (p + 8 <= to) {
            val size32 = bb.getInt(p).toLong() and 0xFFFFFFFFL
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            val (end, payloadStart) = when (size32) {
                0L -> to to (p + 8)
                1L -> {
                    if (p + 16 > to) break
                    val size64 = bb.getLong(p + 8)
                    if (size64 < 16L || p + size64 > to) break
                    (p + size64.toInt()) to (p + 16)
                }
                else -> {
                    if (size32 < 8L || p + size32 > to) break
                    (p + size32.toInt()) to (p + 8)
                }
            }
            out.add(MemBox(p, payloadStart, end, type))
            if (end <= p) break
            p = end
        }
        return out
    }

    private data class MoovInfo(
        val trackId: Int,
        val timescale: Int,
        val stsdBox: ByteArray,
        val defaultSampleDuration: Int,
        val defaultSampleSize: Int,
        val hasMvex: Boolean,
        val stszSampleCount: Int,
    )

    private fun parseMoov(moov: ByteArray): MoovInfo? {
        val bb = ByteBuffer.wrap(moov).order(ByteOrder.BIG_ENDIAN)
        var hasMvex = false
        val trexByTrack = HashMap<Int, Pair<Int, Int>>()

        var audioTrackId = 1
        var audioTimescale = 0
        var audioStsd: ByteArray? = null
        var audioStszCount = 0

        for (child in childBoxes(moov, 0, moov.size)) {
            when (child.type) {
                "mvex" -> {
                    hasMvex = true
                    for (mc in childBoxes(moov, child.payloadStart, child.end)) {
                        if (mc.type == "trex" && mc.end - mc.payloadStart >= 24) {
                            val trackId = bb.getInt(mc.payloadStart + 4)
                            val defDur = bb.getInt(mc.payloadStart + 12)
                            val defSize = bb.getInt(mc.payloadStart + 16)
                            trexByTrack[trackId] = defDur to defSize
                        }
                    }
                }
                "trak" -> {
                    var tid = 1
                    var handler = ""
                    var timescale = 0
                    var stsd: ByteArray? = null
                    var stszCount = 0
                    for (tc in childBoxes(moov, child.payloadStart, child.end)) {
                        when (tc.type) {
                            "tkhd" -> {
                                if (tc.end - tc.payloadStart >= 24) {
                                    val version = moov[tc.payloadStart].toInt() and 0xFF
                                    tid = if (version == 1 && tc.end - tc.payloadStart >= 32) {
                                        bb.getInt(tc.payloadStart + 20)
                                    } else {
                                        bb.getInt(tc.payloadStart + 12)
                                    }
                                }
                            }
                            "mdia" -> {
                                for (mc in childBoxes(moov, tc.payloadStart, tc.end)) {
                                    when (mc.type) {
                                        "mdhd" -> {
                                            if (mc.end - mc.payloadStart >= 20) {
                                                val version = moov[mc.payloadStart].toInt() and 0xFF
                                                timescale = if (version == 1 && mc.end - mc.payloadStart >= 28) {
                                                    bb.getInt(mc.payloadStart + 20)
                                                } else {
                                                    bb.getInt(mc.payloadStart + 12)
                                                }
                                            }
                                        }
                                        "hdlr" -> {
                                            if (mc.end - mc.payloadStart >= 12) {
                                                handler = String(moov, mc.payloadStart + 8, 4, Charsets.ISO_8859_1)
                                            }
                                        }
                                        "minf" -> {
                                            for (mic in childBoxes(moov, mc.payloadStart, mc.end)) {
                                                if (mic.type == "stbl") {
                                                    for (sc in childBoxes(moov, mic.payloadStart, mic.end)) {
                                                        when (sc.type) {
                                                            "stsd" -> stsd = moov.copyOfRange(sc.start, sc.end)
                                                            "stsz" -> if (sc.end - sc.payloadStart >= 12) {
                                                                stszCount = bb.getInt(sc.payloadStart + 8)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (handler == "soun" && stsd != null) {
                        audioTrackId = tid
                        audioTimescale = timescale
                        audioStsd = stsd
                        audioStszCount = stszCount
                    }
                }
            }
        }
        val stsd = audioStsd ?: return null
        val (defDur, defSize) = trexByTrack[audioTrackId] ?: (0 to 0)
        return MoovInfo(
            trackId = audioTrackId,
            timescale = audioTimescale,
            stsdBox = stsd,
            defaultSampleDuration = defDur,
            defaultSampleSize = defSize,
            hasMvex = hasMvex,
            stszSampleCount = audioStszCount,
        )
    }

    private data class TrunInfo(
        val dataOffset: Int?,
        val sampleSizes: IntArray,
        val sampleDurations: IntArray,
    )

    private data class MoofInfo(
        val baseDataOffset: Long?,
        val runs: List<TrunInfo>,
    )

    private fun parseMoof(
        moofBytes: ByteArray,
        targetTrackId: Int,
        defaultTrexDuration: Int,
        defaultTrexSize: Int,
    ): MoofInfo? {
        val bb = ByteBuffer.wrap(moofBytes).order(ByteOrder.BIG_ENDIAN)
        for (child in childBoxes(moofBytes, 0, moofBytes.size)) {
            if (child.type != "traf") continue
            var trackId = targetTrackId
            var baseDataOffset: Long? = null
            var defaultDur = defaultTrexDuration
            var defaultSize = defaultTrexSize
            val runs = ArrayList<TrunInfo>()

            for (tc in childBoxes(moofBytes, child.payloadStart, child.end)) {
                when (tc.type) {
                    "tfhd" -> {
                        var p = tc.payloadStart
                        if (p + 8 > tc.end) return null
                        val flags = bb.getInt(p) and 0x00FFFFFF
                        p += 4
                        trackId = bb.getInt(p)
                        p += 4
                        if (flags and 0x000001 != 0) {
                            if (p + 8 > tc.end) return null
                            baseDataOffset = bb.getLong(p)
                            p += 8
                        }
                        if (flags and 0x000002 != 0) p += 4 // sample_description_index
                        if (flags and 0x000008 != 0) {
                            if (p + 4 > tc.end) return null
                            defaultDur = bb.getInt(p)
                            p += 4
                        }
                        if (flags and 0x000010 != 0) {
                            if (p + 4 > tc.end) return null
                            defaultSize = bb.getInt(p)
                            p += 4
                        }
                    }
                    "trun" -> {
                        var p = tc.payloadStart
                        if (p + 8 > tc.end) return null
                        val flags = bb.getInt(p) and 0x00FFFFFF
                        p += 4
                        val count = bb.getInt(p)
                        p += 4
                        if (count < 0 || count > 1_000_000) return null
                        val dataOffset = if (flags and 0x000001 != 0) {
                            if (p + 4 > tc.end) return null
                            bb.getInt(p).also { p += 4 }
                        } else {
                            null
                        }
                        if (flags and 0x000004 != 0) p += 4 // first_sample_flags
                        val hasDur = flags and 0x000100 != 0
                        val hasSize = flags and 0x000200 != 0
                        val hasFlags = flags and 0x000400 != 0
                        val hasCto = flags and 0x000800 != 0
                        val sizes = IntArray(count)
                        val durs = IntArray(count)
                        for (i in 0 until count) {
                            val d = if (hasDur) {
                                if (p + 4 > tc.end) return null
                                bb.getInt(p).also { p += 4 }
                            } else {
                                defaultDur.takeIf { it > 0 } ?: 1024
                            }
                            val s = if (hasSize) {
                                if (p + 4 > tc.end) return null
                                bb.getInt(p).also { p += 4 }
                            } else {
                                defaultSize
                            }
                            if (hasFlags) p += 4
                            if (hasCto) p += 4
                            sizes[i] = s
                            durs[i] = d
                        }
                        runs.add(TrunInfo(dataOffset, sizes, durs))
                    }
                }
            }
            if (trackId == targetTrackId && runs.isNotEmpty()) {
                return MoofInfo(baseDataOffset, runs)
            }
        }
        return null
    }

    private fun buildFtyp(): ByteArray = box("ftyp") { out ->
        out.writeBytes("M4A ")
        out.writeInt(0)
        out.writeBytes("M4A ")
        out.writeBytes("mp42")
        out.writeBytes("isom")
    }

    private fun buildMoov(
        timescale: Int,
        totalDuration: Long,
        stsdBox: ByteArray,
        sttsEntries: List<Pair<Int, Int>>,
        stscEntries: List<Triple<Int, Int, Int>>,
        sampleSizes: IntArray,
        chunkOffsets: IntArray,
    ): ByteArray {
        val mvhd = buildMvhd(timescale, totalDuration)
        val tkhd = buildTkhd(totalDuration)
        val mdhd = buildMdhd(timescale, totalDuration)
        val hdlr = buildHdlr()
        val smhd = buildSmhd()
        val dinf = buildDinf()
        val stts = buildStts(sttsEntries)
        val stsc = buildStsc(stscEntries)
        val stsz = buildStsz(sampleSizes)
        val stco = buildStco(chunkOffsets)
        val stbl = box("stbl") { out ->
            out.write(stsdBox)
            out.write(stts)
            out.write(stsc)
            out.write(stsz)
            out.write(stco)
        }
        val minf = box("minf") { out ->
            out.write(smhd)
            out.write(dinf)
            out.write(stbl)
        }
        val mdia = box("mdia") { out ->
            out.write(mdhd)
            out.write(hdlr)
            out.write(minf)
        }
        val trak = box("trak") { out ->
            out.write(tkhd)
            out.write(mdia)
        }
        return box("moov") { out ->
            out.write(mvhd)
            out.write(trak)
        }
    }

    private fun buildMvhd(timescale: Int, duration: Long): ByteArray = box("mvhd") { out ->
        val use64 = duration > 0xFFFFFFFFL
        out.writeInt(if (use64) 0x01000000 else 0) // version + flags
        if (use64) {
            out.writeLong(0L); out.writeLong(0L)
            out.writeInt(timescale)
            out.writeLong(duration)
        } else {
            out.writeInt(0); out.writeInt(0)
            out.writeInt(timescale)
            out.writeInt(duration.toInt())
        }
        out.writeInt(0x00010000) // rate 1.0
        out.writeShort(0x0100) // volume 1.0
        out.write(ByteArray(10)) // reserved
        // identity matrix
        out.writeInt(0x00010000); out.writeInt(0); out.writeInt(0)
        out.writeInt(0); out.writeInt(0x00010000); out.writeInt(0)
        out.writeInt(0); out.writeInt(0); out.writeInt(0x40000000)
        out.write(ByteArray(24)) // pre_defined
        out.writeInt(2) // next_track_ID
    }

    private fun buildTkhd(duration: Long): ByteArray = box("tkhd") { out ->
        val use64 = duration > 0xFFFFFFFFL
        // flags = 3 (track enabled + in movie)
        out.writeInt((if (use64) 0x01000000 else 0) or 0x000003)
        if (use64) {
            out.writeLong(0L); out.writeLong(0L)
            out.writeInt(1) // track_ID
            out.writeInt(0) // reserved
            out.writeLong(duration)
        } else {
            out.writeInt(0); out.writeInt(0)
            out.writeInt(1) // track_ID
            out.writeInt(0) // reserved
            out.writeInt(duration.toInt())
        }
        out.write(ByteArray(8)) // reserved
        out.writeShort(0) // layer
        out.writeShort(0) // alternate_group
        out.writeShort(0x0100) // audio volume 1.0
        out.writeShort(0) // reserved
        // identity matrix
        out.writeInt(0x00010000); out.writeInt(0); out.writeInt(0)
        out.writeInt(0); out.writeInt(0x00010000); out.writeInt(0)
        out.writeInt(0); out.writeInt(0); out.writeInt(0x40000000)
        out.writeInt(0); out.writeInt(0) // width, height
    }

    private fun buildMdhd(timescale: Int, duration: Long): ByteArray = box("mdhd") { out ->
        val use64 = duration > 0xFFFFFFFFL
        out.writeInt(if (use64) 0x01000000 else 0)
        if (use64) {
            out.writeLong(0L); out.writeLong(0L)
            out.writeInt(timescale)
            out.writeLong(duration)
        } else {
            out.writeInt(0); out.writeInt(0)
            out.writeInt(timescale)
            out.writeInt(duration.toInt())
        }
        out.writeShort(0x55C4) // language "und"
        out.writeShort(0)
    }

    private fun buildHdlr(): ByteArray = box("hdlr") { out ->
        out.writeInt(0) // version + flags
        out.writeInt(0) // pre_defined
        out.writeBytes("soun")
        out.write(ByteArray(12)) // reserved
        out.writeBytes("SoundHandler\u0000")
    }

    private fun buildSmhd(): ByteArray = box("smhd") { out ->
        out.writeInt(0) // version + flags
        out.writeShort(0) // balance
        out.writeShort(0) // reserved
    }

    private fun buildDinf(): ByteArray = box("dinf") { out ->
        val urlBox = box("url ") { u -> u.writeInt(0x000001) } // self-contained
        val dref = box("dref") { d ->
            d.writeInt(0) // version + flags
            d.writeInt(1) // entry_count
            d.write(urlBox)
        }
        out.write(dref)
    }

    private fun buildStts(entries: List<Pair<Int, Int>>): ByteArray = box("stts") { out ->
        out.writeInt(0)
        out.writeInt(entries.size)
        for ((count, delta) in entries) {
            out.writeInt(count)
            out.writeInt(delta)
        }
    }

    private fun buildStsc(entries: List<Triple<Int, Int, Int>>): ByteArray = box("stsc") { out ->
        out.writeInt(0)
        out.writeInt(entries.size)
        for ((firstChunk, samplesPerChunk, descIdx) in entries) {
            out.writeInt(firstChunk)
            out.writeInt(samplesPerChunk)
            out.writeInt(descIdx)
        }
    }

    private fun buildStsz(sampleSizes: IntArray): ByteArray = box("stsz") { out ->
        out.writeInt(0)
        out.writeInt(0) // variable sample size
        out.writeInt(sampleSizes.size)
        for (size in sampleSizes) out.writeInt(size)
    }

    private fun buildStco(chunkOffsets: IntArray): ByteArray = box("stco") { out ->
        out.writeInt(0)
        out.writeInt(chunkOffsets.size)
        for (offset in chunkOffsets) out.writeInt(offset)
    }

    private fun box(type: String, body: (DataOutputStream) -> Unit): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use(body)
        val bytes = payload.toByteArray()
        val full = ByteArrayOutputStream(8 + bytes.size)
        DataOutputStream(full).use { out ->
            out.writeInt(8 + bytes.size)
            out.writeBytes(type)
            out.write(bytes)
        }
        return full.toByteArray()
    }
}
