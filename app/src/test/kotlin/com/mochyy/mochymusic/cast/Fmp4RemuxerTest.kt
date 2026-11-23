package com.mochyy.mochymusic.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Fmp4RemuxerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun remuxesFragmentedAudioMp4IntoFaststartM4aWithIntactSamples() {
        // 100 fake AAC frames across 2 moof+mdat fragments (> 43 samples so multi-chunk stsc/stco is exercised)
        val frames = List(100) { idx ->
            val size = 64 + (idx % 37)
            ByteArray(size) { b -> ((idx * 31 + b) and 0xFF).toByte() }
        }
        val fakeStsdPayload = ByteArray(48) { i -> (0xA0 + (i % 16)).toByte() }
        val fmp4 = buildSyntheticFmp4(
            timescale = 44100,
            sampleDuration = 1024,
            stsdPayload = fakeStsdPayload,
            fragments = listOf(frames.subList(0, 50), frames.subList(50, 100)),
        )

        val inputFile = tempDir.newFile("itag140.m4a").apply { writeBytes(fmp4) }
        val outputFile = tempDir.newFile("remuxed.m4a")

        val didRemux = Fmp4Remuxer.remuxIfFragmented(inputFile, outputFile)
        assertTrue("Expected fragmented MP4 to be remuxed", didRemux)

        val outBytes = outputFile.readBytes()
        val topBoxes = scanBoxes(outBytes, 0, outBytes.size)
        assertEquals(listOf("ftyp", "moov", "mdat"), topBoxes.map { it.type })

        // Verify ftyp major brand is M4A
        val ftyp = topBoxes[0]
        assertEquals("M4A ", String(outBytes, ftyp.payloadStart, 4, Charsets.ISO_8859_1))

        // Verify moov tables and reconstruct every sample via stsc + stco + stsz
        val moov = topBoxes[1]
        val moovChildren = scanBoxes(outBytes, moov.payloadStart, moov.end)
        val trak = moovChildren.first { it.type == "trak" }
        val mdia = scanBoxes(outBytes, trak.payloadStart, trak.end).first { it.type == "mdia" }
        val mdiaChildren = scanBoxes(outBytes, mdia.payloadStart, mdia.end)
        val mdhd = mdiaChildren.first { it.type == "mdhd" }
        val bb = ByteBuffer.wrap(outBytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(44100, bb.getInt(mdhd.payloadStart + 12))
        assertEquals(100 * 1024, bb.getInt(mdhd.payloadStart + 16))

        val minf = mdiaChildren.first { it.type == "minf" }
        val stbl = scanBoxes(outBytes, minf.payloadStart, minf.end).first { it.type == "stbl" }
        val stblChildren = scanBoxes(outBytes, stbl.payloadStart, stbl.end).associateBy { it.type }

        // stsd preserved verbatim
        val stsd = assertNotNullAndGet(stblChildren["stsd"])
        assertArrayEquals(fakeStsdPayload, outBytes.copyOfRange(stsd.payloadStart, stsd.end))

        // stsz has all 100 sample sizes
        val stsz = assertNotNullAndGet(stblChildren["stsz"])
        assertEquals(100, bb.getInt(stsz.payloadStart + 8))
        val sampleSizes = IntArray(100) { i -> bb.getInt(stsz.payloadStart + 12 + i * 4) }

        // stco + stsc map every sample to its exact bytes in mdat
        val stco = assertNotNullAndGet(stblChildren["stco"])
        val chunkCount = bb.getInt(stco.payloadStart + 4)
        assertEquals(3, chunkCount) // 43 + 43 + 14 = 100
        val chunkOffsets = IntArray(chunkCount) { c -> bb.getInt(stco.payloadStart + 8 + c * 4) }

        val stsc = assertNotNullAndGet(stblChildren["stsc"])
        val stscCount = bb.getInt(stsc.payloadStart + 4)
        assertEquals(2, stscCount) // (chunk 1 -> 43 samples), (chunk 3 -> 14 samples)

        val chunksSamples = intArrayOf(43, 43, 14)
        var sampleIdx = 0
        for (c in 0 until chunkCount) {
            var offset = chunkOffsets[c]
            repeat(chunksSamples[c]) {
                val sz = sampleSizes[sampleIdx]
                val extracted = outBytes.copyOfRange(offset, offset + sz)
                assertArrayEquals("Sample $sampleIdx mismatch", frames[sampleIdx], extracted)
                offset += sz
                sampleIdx++
            }
        }
        assertEquals(100, sampleIdx)

        // Running Fmp4Remuxer again on the non-fragmented output must return false (passthrough)
        val secondOut = tempDir.newFile("second.m4a")
        assertFalse("Already non-fragmented M4A should not be remuxed again", Fmp4Remuxer.remuxIfFragmented(outputFile, secondOut))
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    private data class Box(val type: String, val start: Int, val payloadStart: Int, val end: Int)

    private fun scanBoxes(data: ByteArray, from: Int, to: Int): List<Box> {
        val out = ArrayList<Box>()
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        var p = from
        while (p + 8 <= to) {
            val size = bb.getInt(p)
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            out.add(Box(type, p, p + 8, p + size))
            p += size
        }
        return out
    }

    private fun buildSyntheticFmp4(
        timescale: Int,
        sampleDuration: Int,
        stsdPayload: ByteArray,
        fragments: List<List<ByteArray>>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // ftyp=dash
        out.write(box("ftyp") {
            it.writeBytes("dash")
            it.writeInt(0)
            it.writeBytes("iso6")
            it.writeBytes("mp41")
        })
        // moov with empty stsz and mvex/trex
        val stsd = box("stsd") { it.write(stsdPayload) }
        val stsz = box("stsz") { it.writeInt(0); it.writeInt(0); it.writeInt(0) }
        val stbl = box("stbl") { it.write(stsd); it.write(stsz) }
        val minf = box("minf") { it.write(stbl) }
        val mdhd = box("mdhd") {
            it.writeInt(0); it.writeInt(0); it.writeInt(0)
            it.writeInt(timescale); it.writeInt(0)
            it.writeShort(0x55C4); it.writeShort(0)
        }
        val hdlr = box("hdlr") {
            it.writeInt(0); it.writeInt(0); it.writeBytes("soun"); it.write(ByteArray(12)); it.writeBytes("Sound\u0000")
        }
        val mdia = box("mdia") { it.write(mdhd); it.write(hdlr); it.write(minf) }
        val tkhd = box("tkhd") {
            it.writeInt(3); it.writeInt(0); it.writeInt(0); it.writeInt(1); it.writeInt(0); it.writeInt(0)
            it.write(ByteArray(60))
        }
        val trak = box("trak") { it.write(tkhd); it.write(mdia) }
        val trex = box("trex") {
            it.writeInt(0) // v+flags
            it.writeInt(1) // trackId
            it.writeInt(1) // defaultSampleDescIndex
            it.writeInt(sampleDuration)
            it.writeInt(0) // defaultSampleSize
            it.writeInt(0) // defaultSampleFlags
        }
        val mvex = box("mvex") { it.write(trex) }
        out.write(box("moov") { it.write(trak); it.write(mvex) })

        for (frag in fragments) {
            val tfhd = box("tfhd") {
                it.writeInt(0x020000) // default-base-is-moof
                it.writeInt(1) // trackId
            }
            // Build trun with dataOffset (0x000001) + sampleSize (0x000200)
            fun makeTrun(dataOffset: Int) = box("trun") {
                it.writeInt(0x000201)
                it.writeInt(frag.size)
                it.writeInt(dataOffset)
                for (f in frag) it.writeInt(f.size)
            }
            val dummyMoof = box("moof") {
                it.write(box("traf") { t -> t.write(tfhd); t.write(makeTrun(0)) })
            }
            val realMoof = box("moof") {
                it.write(box("traf") { t -> t.write(tfhd); t.write(makeTrun(dummyMoof.size + 8)) })
            }
            val mdat = box("mdat") {
                for (f in frag) it.write(f)
            }
            out.write(realMoof)
            out.write(mdat)
        }
        return out.toByteArray()
    }

    private fun box(type: String, body: (DataOutputStream) -> Unit): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use(body)
        val bytes = payload.toByteArray()
        val full = ByteArrayOutputStream(8 + bytes.size)
        DataOutputStream(full).use {
            it.writeInt(8 + bytes.size)
            it.writeBytes(type)
            it.write(bytes)
        }
        return full.toByteArray()
    }
}
