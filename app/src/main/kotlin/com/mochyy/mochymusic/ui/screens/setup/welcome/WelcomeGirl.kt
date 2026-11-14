// the welcome girl on screen: her layers drawn from the idle loop each frame, the phone between her and her fingers

package com.mochyy.mochymusic.ui.screens.setup.welcome

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

// the layers decoded at the size she's drawn, so the gpu never shrinks a big picture every frame
private class GirlBitmaps(
    val base: Bitmap,
    val hair: Bitmap,
    val eyeLeft: Bitmap,
    val eyeRight: Bitmap,
    val eyeLeftShut: Bitmap,
    val eyeRightShut: Bitmap,
    val mouth: Bitmap,
    val hand: Bitmap,
    val motionLines: Bitmap,
)

private suspend fun loadGirl(resources: Resources, scale: Float): GirlBitmaps = withContext(Dispatchers.IO) {
    fun load(layer: GirlLayer): Bitmap {
        var sample = 1
        while (scale * sample * 2 <= 1f) sample *= 2
        val raw = BitmapFactory.decodeResource(resources, layer.res, BitmapFactory.Options().apply { inSampleSize = sample })
        val width = (layer.w * scale).roundToInt().coerceAtLeast(1)
        val height = (layer.h * scale).roundToInt().coerceAtLeast(1)
        val sized = if (raw.width == width && raw.height == height) raw else Bitmap.createScaledBitmap(raw, width, height, true)
        if (sized !== raw) raw.recycle()
        // hands the upload to the render thread now, so the first frame she shows up in doesn't stall
        sized.prepareToDraw()
        return sized
    }
    GirlBitmaps(
        base = load(GirlArt.Base),
        hair = load(GirlArt.Hair),
        eyeLeft = load(GirlArt.EyeLeft),
        eyeRight = load(GirlArt.EyeRight),
        eyeLeftShut = load(GirlArt.EyeLeftShut),
        eyeRightShut = load(GirlArt.EyeRightShut),
        mouth = load(GirlArt.Mouth),
        hand = load(GirlArt.Hand),
        motionLines = load(GirlArt.MotionLines),
    )
}

// the girl, sized by the caller to the art frame's shape; screen fills her phone and can be a video later
@Composable
internal fun WelcomeGirl(
    motion: GirlMotion,
    seconds: FloatState,
    modifier: Modifier = Modifier,
    screen: @Composable BoxScope.() -> Unit = { MochyPhoneScreen() },
) {
    BoxWithConstraints(modifier) {
        val scale = constraints.maxWidth / GirlArt.FrameWidth
        // decoded in 5% steps and never above the art's own size, so a tiny resize doesn't decode again
        val bitmapScale = (ceil(scale * 20f) / 20f).coerceIn(0.05f, 1f)
        val resources = LocalContext.current.resources
        val bits by produceState<GirlBitmaps?>(null, bitmapScale) { value = loadGirl(resources, bitmapScale) }
        val appear = remember { Animatable(0f) }
        LaunchedEffect(bits != null) {
            if (bits != null) appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 90f))
        }
        val painter = remember { GirlPainter() }
        val lift = with(LocalDensity.current) { 40.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    seconds.floatValue
                    val shown = appear.value
                    alpha = shown.coerceIn(0f, 1f)
                    translationY = (1f - shown) * lift
                    // breathing lifts her shoulders a touch from the bottom of the frame
                    val breath = motion.pose.breath
                    scaleX = 1f + 0.0012f * breath
                    scaleY = 1f + 0.0045f * breath
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        seconds.floatValue
                        val loaded = bits ?: return@drawBehind
                        drawIntoCanvas { painter.drawBody(it.nativeCanvas, scale, motion.pose, loaded) }
                    },
            )
            PhoneBody(motion, seconds, scale, screen)
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        seconds.floatValue
                        val loaded = bits ?: return@drawBehind
                        drawIntoCanvas { painter.drawHand(it.nativeCanvas, scale, motion.pose, loaded) }
                    },
            )
        }
    }
}

// what her phone shows for now: plain gray with the mochy mark
@Composable
internal fun BoxScope.MochyPhoneScreen() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF5C5C5C), Color(0xFF3B3B3B)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mochy_mark),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(128f / 139f),
        )
    }
}

// the phone's white edge, black bezel and screen, drifting with her hand by layer props only
@Composable
private fun PhoneBody(motion: GirlMotion, seconds: FloatState, scale: Float, screen: @Composable BoxScope.() -> Unit) {
    val width = GirlArt.PhoneWidth * scale
    val height = GirlArt.PhoneHeight * scale
    val left = (GirlArt.PhoneCenterX - GirlArt.FrameLeft) * scale - width / 2f
    val top = (GirlArt.PhoneCenterY - GirlArt.FrameTop) * scale - height / 2f
    val placed = IntOffset(left.roundToInt(), top.roundToInt())
    val outer = GirlArt.PhoneRadius * scale
    val border = GirlArt.PhoneBorder * scale
    val bezel = GirlArt.PhoneBezel * scale
    with(LocalDensity.current) {
        Box(
            modifier = Modifier
                .offset { placed }
                .size(width.toDp(), height.toDp())
                .graphicsLayer {
                    seconds.floatValue
                    val pose = motion.pose
                    // where the phone's centre lands after her hand's drift, plus the pixel fraction the offset dropped
                    val movedX = pose.phoneMoveX(GirlArt.PhoneCenterX, GirlArt.PhoneCenterY)
                    val movedY = pose.phoneMoveY(GirlArt.PhoneCenterX, GirlArt.PhoneCenterY)
                    translationX = (movedX - GirlArt.PhoneCenterX) * scale + (left - placed.x)
                    translationY = (movedY - GirlArt.PhoneCenterY) * scale + (top - placed.y)
                    rotationZ = GirlArt.PhoneTilt + pose.phoneDeg
                }
                .background(Color.White, RoundedCornerShape(outer.toDp()))
                .padding(border.toDp())
                .background(Color.Black, RoundedCornerShape((outer - border).toDp()))
                .padding(bezel.toDp())
                .clip(RoundedCornerShape((outer - border - bezel).coerceAtLeast(0f).toDp())),
            content = screen,
        )
    }
}

// draws her straight onto the native canvas: two bent meshes, small patches for the eyes and mouth
private class GirlPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    fun drawBody(canvas: Canvas, scale: Float, pose: GirlPose, bits: GirlBitmaps) {
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-GirlArt.FrameLeft, -GirlArt.FrameTop)
        canvas.drawBitmapMesh(bits.base, GirlPose.BaseCols, GirlPose.BaseRows, pose.baseMesh, 0, null, 0, paint)
        // the face in the base mesh already turns this much, so everything on it turns along
        canvas.save()
        canvas.rotate(pose.headDeg, GirlArt.NeckX, GirlArt.NeckY)
        drawEye(canvas, bits.eyeLeft, GirlArt.EyeLeft, bits.eyeLeftShut, GirlArt.EyeLeftShut, GirlArt.EyeLeftLid, pose.eyesOpen)
        drawEye(canvas, bits.eyeRight, GirlArt.EyeRight, bits.eyeRightShut, GirlArt.EyeRightShut, GirlArt.EyeRightLid, pose.eyesOpen)
        drawMouth(canvas, bits.mouth, pose.mouthOpen)
        canvas.drawBitmapMesh(bits.hair, GirlPose.HairCols, GirlPose.HairRows, pose.hairMesh, 0, null, 0, paint)
        canvas.restore()
        canvas.restore()
    }

    fun drawHand(canvas: Canvas, scale: Float, pose: GirlPose, bits: GirlBitmaps) {
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-GirlArt.FrameLeft, -GirlArt.FrameTop)
        // the motion strokes sit on the sleeve, so they follow the hand only halfway
        canvas.save()
        canvas.translate(pose.phoneX * 0.5f, pose.phoneY * 0.5f)
        drawLayer(canvas, bits.motionLines, GirlArt.MotionLines, 1f)
        canvas.restore()
        canvas.translate(pose.phoneX, pose.phoneY)
        canvas.rotate(pose.phoneDeg, GirlArt.WristX, GirlArt.WristY)
        drawLayer(canvas, bits.hand, GirlArt.Hand, 1f)
        canvas.restore()
    }

    // the open eye squashes toward its lid as it fades into the shut lash curve, so a blink never pops
    private fun drawEye(canvas: Canvas, open: Bitmap, openLayer: GirlLayer, shut: Bitmap, shutLayer: GirlLayer, lidY: Float, amount: Float) {
        val shown = ((amount - 0.12f) / 0.33f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        if (shown > 0f) {
            canvas.save()
            canvas.scale(1f, 0.15f + 0.85f * amount, openLayer.x + openLayer.w / 2f, lidY)
            drawLayer(canvas, open, openLayer, shown)
            canvas.restore()
        }
        if (shown < 1f) drawLayer(canvas, shut, shutLayer, 1f - shown)
    }

    // the mouth eases from its top lip and widens a hair as it closes, like a soft breath
    private fun drawMouth(canvas: Canvas, mouth: Bitmap, open: Float) {
        val layer = GirlArt.Mouth
        canvas.save()
        canvas.scale(1f + 0.05f * (1f - open), open, layer.x + layer.w / 2f, GirlArt.MouthTop)
        drawLayer(canvas, mouth, layer, 1f)
        canvas.restore()
    }

    private fun drawLayer(canvas: Canvas, bitmap: Bitmap, layer: GirlLayer, alpha: Float) {
        if (alpha <= 0f) return
        paint.alpha = (alpha.coerceAtMost(1f) * 255f).roundToInt()
        rect.set(layer.x, layer.y, layer.right, layer.bottom)
        canvas.drawBitmap(bitmap, null, rect, paint)
        paint.alpha = 255
    }
}
