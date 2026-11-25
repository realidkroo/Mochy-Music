package com.mochyy.mochymusic.ui.component

import com.mochyy.mochymusic.ui.player.boxBlurPixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgslTranspilerTest {

    // real liquid glass shader from docs/wiki/index.html
    private val liquidGlassAgsl = """
        uniform shader content;
        uniform float2 size;
        uniform float margin;
        uniform float radius;
        uniform float density;
        uniform float quality;
        uniform float4 pill;
        uniform float pillShown;
        layout(color) uniform half4 chrome;

        uniform float tintAlpha;
        uniform float bevel;
        uniform float strength;
        uniform float spread;
        uniform float saturation;
        uniform float rimAlpha;
        uniform float sheen;

        float roundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 p) {
            float2 c = size * 0.5;
            float2 box = c - margin;
            float r = min(radius, min(box.x, box.y));
            float2 local = p - c;
            float d = roundedBox(local, box, r);
            // past the bar's edge: mochy clips it away
            if (d > 0.0) { return content.eval(p); }

            float bev = max(bevel * density, 1.0);
            float t = clamp(1.0 + d / bev, 0.0, 1.0);
            float bend = 1.0 - sqrt(1.0 - t * t);
            float2 n = float2(
                roundedBox(local + float2(1.0, 0.0), box, r) - roundedBox(local - float2(1.0, 0.0), box, r),
                roundedBox(local + float2(0.0, 1.0), box, r) - roundedBox(local - float2(0.0, 1.0), box, r));
            n = n / max(length(n), 0.0001);
            float2 offset = n * bend * strength * density;

            float4 color;
            if (quality > 0.5 && spread > 0.0 && bend > 0.001) {
                float4 red = float4(content.eval(p + offset * (1.0 + spread)));
                float4 mid = float4(content.eval(p + offset));
                float4 blue = float4(content.eval(p + offset * (1.0 - spread)));
                color = float4(red.r, mid.g, blue.b, mid.a);
            } else {
                color = float4(content.eval(p + offset));
            }

            float luma = dot(color.rgb, float3(0.2126, 0.7152, 0.0722));
            color.rgb = mix(float3(luma), color.rgb, saturation);
            float4 tint = float4(chrome);
            float deeper = (tint.r + tint.g + tint.b) < 0.01 ? 0.15 : 0.0;
            color.rgb = mix(color.rgb, tint.rgb * color.a, clamp(tintAlpha + deeper, 0.0, 0.95));
            float edge = 1.0 - smoothstep(0.0, 1.4 * density, -d);
            float facing = clamp(dot(n, normalize(float2(-1.0, -1.2))) * 0.5 + 0.5, 0.0, 1.0);
            float lit = clamp(edge * rimAlpha * mix(0.3, 1.0, facing), 0.0, 1.0);
            float down = (local.y + box.y) / max(box.y * 2.0, 1.0);
            float glow = sheen * (1.0 - smoothstep(0.0, 0.45, down));
            color.rgb = mix(color.rgb, float3(color.a), lit) + glow * color.a;
            return half4(color);
        }
    """.trimIndent()

    @Test
    fun `transpiles full liquid glass agsl to valid glsl es 300`() {
        val result = AgslTranspiler.transpile(liquidGlassAgsl)
        assertNotNull(result)
        result!!
        val glsl = result.glsl
        assertTrue(glsl.startsWith("#version 300 es"))
        assertEquals(listOf("content"), result.shaderInputs)
        assertTrue("pill" in result.declaredUniforms)
        assertTrue("chrome" in result.declaredUniforms)
        assertTrue("tintAlpha" in result.declaredUniforms)
        assertTrue("size" in result.declaredUniforms)

        // no agsl-only syntax left in the transpiled glsl
        assertFalse("leftover half4", Regex("""\bhalf[234]?\b""").containsMatchIn(glsl))
        assertFalse("leftover float2..4", Regex("""\bfloat[234]\b""").containsMatchIn(glsl))
        assertFalse("leftover layout(color)", glsl.contains("layout(color)"))
        assertFalse("leftover uniform shader", glsl.contains("uniform shader"))
        assertFalse("leftover .eval(", glsl.contains(".eval("))

        // nested parens inside content.eval(...) rewritten cleanly
        assertTrue(glsl.contains("uniform sampler2D content;"))
        assertTrue(glsl.contains("_agsl_eval(content, p + offset * (1.0 + spread), _u_content_size)"))
        assertTrue(glsl.contains("_agsl_eval(content, p + offset * (1.0 - spread), _u_content_size)"))
        assertTrue(glsl.contains("vec4 _agsl_main(vec2 p)"))
        assertTrue(glsl.contains("void main()"))
        assertTrue(glsl.contains("_agsl_out = _agsl_main(_p);"))
    }

    @Test
    fun `transpiles standalone bar and player shaders without content sampler`() {
        val barAgsl = """
            // uniform float fakeFromComment;
            /* uniform shader fakeBlockComment; */
            uniform float2 size;
            uniform float time;
            uniform float4 pill;
            uniform float pillShown;
            layout(color) uniform half4 accent;

            half4 main(float2 p) {
                float2 c = float2((pill.x + pill.z) * 0.5 + sin(time) * 6.0, size.y * 0.5);
                float glow = saturate(1.0 - smoothstep(0.0, size.y * 1.4, distance(p, c))) * pillShown;
                return half4(half3(accent.rgb) * glow * 0.35, glow * 0.35);
            }
        """.trimIndent()
        val result = AgslTranspiler.transpile(barAgsl)!!
        assertTrue(result.shaderInputs.isEmpty())
        assertFalse("commented uniform leaked", "fakeFromComment" in result.declaredUniforms)
        assertFalse("block-commented sampler leaked", "fakeBlockComment" in result.declaredUniforms)
        assertTrue("time" in result.declaredUniforms)
        assertTrue("accent" in result.declaredUniforms)
        assertFalse("standalone shader shouldn't emit _agsl_eval", result.glsl.contains("_agsl_eval"))
        assertTrue(result.glsl.contains("vec4 _agsl_main(vec2 p)"))
    }

    @Test
    fun `transpiles no-arg main using sk_FragCoord and rejects shaders without main`() {
        val noArg = """
            uniform float2 size;
            half4 main() {
                float2 uv = sk_FragCoord.xy / size;
                return half4(uv.x, uv.y, 0.5, 1.0);
            }
        """.trimIndent()
        val result = AgslTranspiler.transpile(noArg)!!
        assertTrue(result.glsl.contains("_agsl_frag_coord.xy / size"))
        assertTrue(result.glsl.contains("vec4 _agsl_main()"))
        assertTrue(result.glsl.contains("_agsl_out = _agsl_main();"))

        assertNull(AgslTranspiler.transpile("uniform float2 size; float helper(float x) { return x * 2.0; }"))
    }

    @Test
    fun `boxBlurPixels preserves solid color, spreads impulse symmetrically, and handles edge sizes`() {
        // 1. solid colour stays bit-identical
        val solid = IntArray(16 * 16) { 0xFF3A7BD5.toInt() }
        boxBlurPixels(solid, 16, 16, radius = 5)
        for (px in solid) assertEquals(0xFF3A7BD5.toInt(), px)

        // 2. center impulse blurs symmetrically without throwing
        val w = 15
        val h = 15
        val impulse = IntArray(w * h) { 0xFF000000.toInt() }
        impulse[7 * w + 7] = 0xFFFFFFFF.toInt()
        boxBlurPixels(impulse, w, h, radius = 3)
        val centerR = (impulse[7 * w + 7] ushr 16) and 0xFF
        val neighborR = (impulse[7 * w + 8] ushr 16) and 0xFF
        val symR = (impulse[7 * w + 6] ushr 16) and 0xFF
        val cornerR = (impulse[0] ushr 16) and 0xFF
        assertTrue("center ($centerR) > neighbor ($neighborR)", centerR > neighborR)
        assertTrue("neighbor ($neighborR) > corner ($cornerR)", neighborR > cornerR)
        assertEquals("horizontal symmetry", neighborR, symR)

        // 3. degenerate sizes and huge radius never crash
        val tiny = intArrayOf(0xFF112233.toInt())
        boxBlurPixels(tiny, 1, 1, radius = 20)
        assertEquals(0xFF112233.toInt(), tiny[0])

        val strip = IntArray(4) { if (it == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        boxBlurPixels(strip, 4, 1, radius = 50)
        assertTrue(((strip[1] ushr 16) and 0xFF) > 0)
    }
}
