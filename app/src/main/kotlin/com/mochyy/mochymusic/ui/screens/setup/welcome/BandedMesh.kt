// a bitmap bent over a grid like drawBitmapMesh, drawn a few rows at a time so no single draw hands the gpu a big vertex buffer

package com.mochyy.mochymusic.ui.screens.setup.welcome

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import kotlin.math.min

// each band's points stay well under the size the allocator puts next to a guard page, see docs/notes/mali-mesh.md
private const val MaxBandBytes = 24 * 1024
// a point is its place plus where it reads the bitmap, two floats each
private const val BytesPerPoint = 16

internal class BandedMesh(private val cols: Int, private val rows: Int) {
    private val perRow = cols + 1
    private val bandRows = (MaxBandBytes / (BytesPerPoint * perRow) - 1).coerceIn(1, rows)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val texs = FloatArray(perRow * (rows + 1) * 2)
    private var texsFor: Bitmap? = null

    // each band is a strip, because skia glues back to back triangle draws into one big buffer again but never glues strips
    private val indices = ShortArray(stripLength(bandRows)).also { out ->
        var at = 0
        for (row in 0 until bandRows) {
            if (row > 0) {
                // the last point again plus this row's first: the hop between rows makes flat triangles that draw nothing
                out[at++] = (row * perRow - 1).toShort()
                out[at++] = ((row + 1) * perRow).toShort()
            }
            // bottom then top of each column gives drawBitmapMesh's own two triangles per cell, in its order
            for (col in 0..cols) {
                out[at++] = ((row + 1) * perRow + col).toShort()
                out[at++] = (row * perRow + col).toShort()
            }
        }
    }

    // verts holds every grid point row by row, like drawBitmapMesh takes them
    fun draw(canvas: Canvas, bitmap: Bitmap, verts: FloatArray) {
        // hardware canvases only draw vertices from android 10, older phones keep the one big mesh
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            canvas.drawBitmapMesh(bitmap, cols, rows, verts, 0, null, 0, paint)
            return
        }
        if (texsFor !== bitmap) useBitmap(bitmap)
        var row = 0
        while (row < rows) {
            val count = min(bandRows, rows - row)
            val offset = row * perRow * 2
            canvas.drawVertices(
                Canvas.VertexMode.TRIANGLE_STRIP, (count + 1) * perRow * 2, verts, offset, texs, offset,
                null, 0, indices, 0, stripLength(count), paint,
            )
            row += count
        }
    }

    // two points per column for every row, plus two to hop from one row to the next
    private fun stripLength(bandRows: Int) = bandRows * perRow * 2 + (bandRows - 1) * 2

    // where each grid point reads the bitmap, spread evenly over it like drawBitmapMesh does
    private fun useBitmap(bitmap: Bitmap) {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        var at = 0
        for (row in 0..rows) {
            val y = if (row == rows) height else height * row / rows
            for (col in 0..cols) {
                texs[at++] = if (col == cols) width else width * col / cols
                texs[at++] = y
            }
        }
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        texsFor = bitmap
    }
}
