// translates module agsl/sksl shaders to glsl es 3.00 so android 12 and below can run them on gles without android 13's runtimeshader

package com.mochyy.mochymusic.ui.component

import androidx.compose.runtime.Immutable

/** a module shader transpiled to glsl es 3.00 for the gles fallback on android 12 and below */
@Immutable
internal data class TranspiledAgsl(
    val glsl: String,
    val declaredUniforms: Set<String>,
    val shaderInputs: List<String>,
)

internal object AgslTranspiler {

    private val LineComment = Regex("""//[^\n]*""")
    private val BlockComment = Regex("""/\*[\s\S]*?\*/""")
    private val LayoutColor = Regex("""\blayout\s*\(\s*color\s*\)\s*""")
    private val UniformShader = Regex("""\buniform\s+shader\s+(\w+)\s*;""")
    private val UniformDecl = Regex("""\buniform\s+\w+\s+(\w+)\s*[;\[]""")
    private val MainWithArg = Regex("""\b(half4|float4|vec4)\s+main\s*\(\s*(?:in\s+)?(?:float2|half2|vec2)\s+(\w+)\s*\)""")
    private val MainNoArg = Regex("""\b(half4|float4|vec4)\s+main\s*\(\s*(?:void)?\s*\)""")

    // agsl vector/matrix/half names mapped to standard glsl es 3.00 equivalents
    private val TypeMap = mapOf(
        "float2" to "vec2",
        "float3" to "vec3",
        "float4" to "vec4",
        "half" to "float",
        "half2" to "vec2",
        "half3" to "vec3",
        "half4" to "vec4",
        "int2" to "ivec2",
        "int3" to "ivec3",
        "int4" to "ivec4",
        "uint2" to "uvec2",
        "uint3" to "uvec3",
        "uint4" to "uvec4",
        "bool2" to "bvec2",
        "bool3" to "bvec3",
        "bool4" to "bvec4",
        "short" to "int",
        "ushort" to "uint",
        "float2x2" to "mat2",
        "half2x2" to "mat2",
        "float3x3" to "mat3",
        "half3x3" to "mat3",
        "float4x4" to "mat4",
        "half4x4" to "mat4",
        "float2x3" to "mat2x3",
        "half2x3" to "mat2x3",
        "float3x2" to "mat3x2",
        "half3x2" to "mat3x2",
        "float2x4" to "mat2x4",
        "half2x4" to "mat2x4",
        "float4x2" to "mat4x2",
        "half4x2" to "mat4x2",
        "float3x4" to "mat3x4",
        "half3x4" to "mat3x4",
        "float4x3" to "mat4x3",
        "half4x3" to "mat4x3",
        "sk_FragCoord" to "_agsl_frag_coord",
    )

    /** turns an agsl shader into a glsl es 3.00 fragment shader, or null when it has no valid main entry point */
    fun transpile(source: String): TranspiledAgsl? {
        val clean = stripCommentsPreservingLines(source)
        val shaderInputs = UniformShader.findAll(clean).map { it.groupValues[1] }.distinct().toList()
        val hasMainWithArg = MainWithArg.containsMatchIn(clean)
        val hasMainNoArg = !hasMainWithArg && MainNoArg.containsMatchIn(clean)
        if (!hasMainWithArg && !hasMainNoArg) return null

        var body = clean
        body = LayoutColor.replace(body, "")
        val declared = UniformDecl.findAll(body).map { it.groupValues[1] }.toSet()
        body = UniformShader.replace(body) { match -> "uniform sampler2D ${match.groupValues[1]};" }
        for (input in shaderInputs) {
            body = rewriteEvalCalls(body, input)
        }
        body = replaceAgslTokens(body)
        body = if (hasMainWithArg) {
            val m = MainWithArg.find(body) ?: return null
            body.replaceRange(m.range, "vec4 _agsl_main(vec2 ${m.groupValues[2]})")
        } else {
            val m = MainNoArg.find(body) ?: return null
            body.replaceRange(m.range, "vec4 _agsl_main()")
        }

        val glsl = buildString(body.length + 768) {
            appendLine("#version 300 es")
            appendLine("precision highp float;")
            appendLine("uniform vec2 _u_viewport;")
            appendLine("uniform vec2 _u_coord_offset;")
            appendLine("uniform float _u_flip_y;")
            if (shaderInputs.isNotEmpty()) {
                appendLine("uniform vec2 _u_content_size;")
                appendLine("uniform float _u_content_decal;")
            }
            appendLine("vec2 _agsl_frag_coord;")
            appendLine("out vec4 _agsl_out;")
            appendLine("float saturate(float x) { return clamp(x, 0.0, 1.0); }")
            appendLine("vec2 saturate(vec2 x) { return clamp(x, 0.0, 1.0); }")
            appendLine("vec3 saturate(vec3 x) { return clamp(x, 0.0, 1.0); }")
            appendLine("vec4 saturate(vec4 x) { return clamp(x, 0.0, 1.0); }")
            appendLine("vec4 unpremul(vec4 c) { return vec4(c.rgb / max(c.a, 0.0001), c.a); }")
            if (shaderInputs.isNotEmpty()) {
                // agsl pixel coordinates grow downward while gl texture v grows upward, so flip v once here
                appendLine("vec4 _agsl_eval(sampler2D s, vec2 coord, vec2 texSize) {")
                appendLine("    vec2 sz = max(texSize, vec2(1.0));")
                appendLine("    vec2 uv = vec2(coord.x / sz.x, 1.0 - coord.y / sz.y);")
                appendLine("    if (_u_content_decal > 0.5 && (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)) { return vec4(0.0); }")
                appendLine("    return texture(s, clamp(uv, vec2(0.0), vec2(1.0)));")
                appendLine("}")
            }
            appendLine(body)
            appendLine("void main() {")
            appendLine("    vec2 _p = vec2(gl_FragCoord.x, _u_flip_y > 0.5 ? (_u_viewport.y - gl_FragCoord.y) : gl_FragCoord.y) + _u_coord_offset;")
            appendLine("    _agsl_frag_coord = _p;")
            if (hasMainWithArg) {
                appendLine("    _agsl_out = _agsl_main(_p);")
            } else {
                appendLine("    _agsl_out = _agsl_main();")
            }
            appendLine("}")
        }
        return TranspiledAgsl(glsl = glsl, declaredUniforms = declared, shaderInputs = shaderInputs)
    }

    // keeps line breaks intact when stripping comments so shader compiler diagnostics still point near the right line
    private fun stripCommentsPreservingLines(source: String): String {
        val noBlocks = BlockComment.replace(source) { match ->
            buildString(match.value.length) {
                for (c in match.value) append(if (c == '\n') '\n' else ' ')
            }
        }
        return LineComment.replace(noBlocks, "")
    }

    // replaces whole-word agsl identifiers while leaving member swizzles like .half or compound names untouched
    private fun replaceAgslTokens(code: String): String {
        val out = StringBuilder(code.length + 64)
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            if (c.isLetter() || c == '_') {
                val start = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val token = code.substring(start, i)
                val afterDot = start > 0 && code[start - 1] == '.'
                val mapped = if (afterDot) null else TypeMap[token]
                out.append(mapped ?: token)
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    // rewrites `<name>.eval(arg)` with balanced parentheses so nested expressions like `content.eval(p + offset * (1.0 + s))` stay intact
    private fun rewriteEvalCalls(code: String, sampler: String): String {
        val pattern = Regex("""\b${Regex.escape(sampler)}\s*\.\s*eval\s*\(""")
        val out = StringBuilder(code.length + 64)
        var cursor = 0
        while (cursor < code.length) {
            val match = pattern.find(code, cursor)
            if (match == null) {
                out.append(code, cursor, code.length)
                break
            }
            out.append(code, cursor, match.range.first)
            val openParen = match.range.last
            var depth = 1
            var pos = openParen + 1
            var topComma = -1
            while (pos < code.length && depth > 0) {
                when (code[pos]) {
                    '(' -> depth++
                    ')' -> depth--
                    ',' -> if (depth == 1 && topComma < 0) topComma = pos
                }
                if (depth > 0) pos++
            }
            if (depth != 0) {
                // unbalanced parens: leave untouched so the glsl compiler reports the real syntax error
                out.append(code, match.range.first, code.length)
                break
            }
            val rawArgs = code.substring(openParen + 1, pos)
            val rewrittenArgs = rewriteEvalCalls(rawArgs, sampler)
            val coordExpr = if (topComma >= 0) {
                val relComma = topComma - (openParen + 1)
                val first = rewriteEvalCalls(rawArgs.substring(0, relComma), sampler)
                val second = rewriteEvalCalls(rawArgs.substring(relComma + 1), sampler)
                "vec2($first, $second)"
            } else {
                rewrittenArgs
            }
            out.append("_agsl_eval(").append(sampler).append(", ").append(coordExpr).append(", _u_content_size)")
            cursor = pos + 1
        }
        return out.toString()
    }
}
