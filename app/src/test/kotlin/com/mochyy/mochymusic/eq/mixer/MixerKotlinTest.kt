package com.mochyy.mochymusic.eq.mixer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerKotlinTest {

    @Test
    fun readsJamesDspKnobs() {
        val script = LiveprogScript.parse(
            """
            desc: Gain control
            //tags: gain utility

            dB:-8<-30,15,1>Volume gain (dB)

            @init
            dB = -8;

            @sample
            spl0 = spl0 * 2;
            """.trimIndent(),
        )
        assertEquals("Gain control", script.description)
        assertEquals(listOf("gain", "utility"), script.tags)
        assertEquals(1, script.knobs.size)
        val knob = script.knobs[0]
        assertEquals("dB", knob.variable)
        assertEquals("Volume gain (dB)", knob.name)
        assertEquals(-8f, knob.default)
        assertEquals(-30f, knob.min)
        assertEquals(15f, knob.max)
        assertEquals(1f, knob.step)
        assertTrue(script.hasSample)
        assertFalse(script.hasSlider)
        assertFalse(script.kind("Gain").liveKnobs)
    }

    @Test
    fun readsJsfxKnobsWithVariablesChoicesAndHiddenOnes() {
        val script = LiveprogScript.parse(
            """
            slider1:gain_db=0<-24,24,0.1>Gain
            slider2:0<0,2,1{Soft,Hard,Fold}>Mode
            slider3:0.5<0,1>-Hidden mix
            @slider
            g = 1;
            @sample
            spl0 = spl0;
            // a line after a section that looks like a knob is code, not a knob
            notaknob:1<0,2,1>Nope
            """.trimIndent(),
        )
        assertEquals(listOf("gain_db", "slider2", "slider3"), script.knobs.map { it.variable })
        assertEquals(listOf("Soft", "Hard", "Fold"), script.knobs[1].choices)
        assertEquals("Hidden mix", script.knobs[2].name)
        assertNull(script.knobs[2].step)
        assertTrue(script.hasSlider)
        val kind = script.kind("Test")
        assertTrue(kind.liveKnobs)
        assertEquals(KnobScale.Choice, kind.knobs[1].scale)
    }

    @Test
    fun dropsKnobsWithNoRange() {
        val script = LiveprogScript.parse("broken:1<2,2,1>Nothing to turn\n@sample\nspl0=spl0;")
        assertTrue(script.knobs.isEmpty())
    }

    @Test
    fun refusesRoutesThatLoop() {
        val a = MixBus(id = 0, songIn = true, sends = mapOf(1 to 1f, MixerNative.Master to 1f))
        val b = MixBus(id = 1, sends = mapOf(2 to 1f))
        val c = MixBus(id = 2, sends = mapOf(MixerNative.Master to 1f))
        val state = MixerState(buses = listOf(a, b, c))
        assertFalse("c already hears a through b", state.canSend(2, 0))
        assertFalse("a bus can't send to itself", state.canSend(1, 1))
        assertTrue(state.canSend(0, 2))
        assertTrue(state.canSend(2, MixerNative.Master))
        assertEquals((1 shl 1) or (1 shl MixerNative.Master), a.sendMask)
    }

    @Test
    fun logKnobsSpreadEvenlyByOctave() {
        val knob = FxKnob("cutoff", "Cutoff", "Hz", 20f, 20000f, 1000f, KnobScale.Log)
        assertEquals(0.5f, knob.toFraction(632.4555f), 0.001f)
        assertEquals(20000f, knob.fromFraction(1f), 0.5f)
        assertEquals(1000f, knob.fromFraction(knob.toFraction(1000f)), 0.5f)
        val steps = FxKnob("n", "N", "", 0f, 10f, 0f, KnobScale.Int)
        assertEquals(3f, steps.fromFraction(0.31f))
    }

    @Test
    fun fitsSavedValuesToAKindsKnobs() {
        val kind = LiveprogScript.parse("a:1<0,2,1>A\nb:5<0,10,1>B\n@sample\nspl0=spl0;").kind("T")
        assertEquals(listOf(2f, 5f), kind.fit(listOf(9f)))
        assertEquals(listOf(0f, 10f), kind.fit(listOf(-1f, 99f, 7f)))
    }
}
