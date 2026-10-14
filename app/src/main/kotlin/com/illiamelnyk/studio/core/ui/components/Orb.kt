package com.illiamelnyk.studio.core.ui.components

import android.animation.ValueAnimator
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.lerp
import com.illiamelnyk.studio.core.ui.theme.ArtPalette
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * The 64 dp blue sphere that means "the AI is working". While [active], the color inside flows: two
 * spots drift on slow paths that never quite repeat, under a light source that stays put, and the
 * glow breathes between the `glow.orb` limits. Inactive, or with animations turned off in the
 * system settings, it stands still.
 *
 * On Android 13 and newer the sphere is a shader, a soft fluid gradient; before, the same spots are
 * drawn as gradients. Everything moves in the draw phase: the orb never recomposes per frame.
 *
 * It is decorative: whatever shows it also says in words what is happening.
 *
 * @param level how intense the work is, from 0 to 1, such as the loudness of a recording; it
 *   brightens the glow and speeds the flow up. Read every frame while drawing.
 */
@Composable
fun Orb(modifier: Modifier = Modifier, active: Boolean = true, level: (() -> Float)? = null) {
    val clock = rememberOrbClock(active, level)
    val glow = StudioTheme.effects.glow.orb
    val breathPeriod = StudioTheme.motion.loop.glowBreath / MILLIS_PER_SECOND
    val breathEasing = StudioTheme.motion.loopEasing
    val shader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OrbShader() else null }
    Canvas(
        modifier
            .size(StudioTheme.dimens.size.avatar.l)
            .dropShadow(StudioTheme.shapes.full) {
                val fraction = if (clock.running) {
                    val breath = breathEasing.transform(clock.breathAt(breathPeriod))
                    val boost = level?.invoke()?.coerceIn(0f, 1f) ?: 0f
                    breath + (1f - breath) * boost
                } else {
                    0f
                }
                radius = lerp(glow.blurMin, glow.blurMax, fraction).toPx()
                alpha = glow.alphaMin + (glow.alphaMax - glow.alphaMin) * fraction
                color = glow.color
            },
    ) {
        val spots = OrbSpots.at(clock.flow)
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawCircle(shader.brush(size.width, size.height, clock.flow, spots))
        } else {
            drawSpots(spots)
        }
    }
}

/**
 * The orb's time: [flow] drives the spots and runs faster with the level, [elapsed] drives breathing.
 * It stands still unless [running].
 */
@Stable
internal class OrbClock {
    var running by mutableStateOf(false)
    var flow by mutableFloatStateOf(0f)
    var elapsed by mutableFloatStateOf(0f)

    /** Where the glow is in its breath, from 0 (out) to 1 (in) and back once per [period] seconds. */
    fun breathAt(period: Float): Float {
        val phase = (elapsed % period) / period
        return if (phase < 0.5f) phase * 2 else (1 - phase) * 2
    }
}

/**
 * Advances an [OrbClock] on every frame while [active], unless animations are turned off in the
 * system settings. The frames come from the infinite animation clock, which tests can pause, and
 * nothing reads the clock outside the draw phase.
 */
@Composable
internal fun rememberOrbClock(active: Boolean, level: (() -> Float)?): OrbClock {
    val clock = remember { OrbClock() }
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val currentLevel by rememberUpdatedState(level)
    LaunchedEffect(active, animationsEnabled) {
        clock.running = active && animationsEnabled
        if (!clock.running) return@LaunchedEffect
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val seconds = (now - last) / NANOS_PER_SECOND
                last = now
                val boost = currentLevel?.invoke()?.coerceIn(0f, 1f) ?: 0f
                clock.elapsed += seconds
                clock.flow += seconds * (1f + boost * LEVEL_SPEEDUP)
            }
        }
    }
    return clock
}

/** Centers of the two color spots, as fractions of the orb's size. */
private class OrbSpots(val light: Offset, val deep: Offset) {
    companion object {
        // Each coordinate swings with its own period, 7, 11 or 13 s, so the paths never quite repeat.
        fun at(seconds: Float) = OrbSpots(
            light = Offset(0.4f + 0.18f * wave(seconds, 7f), 0.68f + 0.12f * wave(seconds, 11f, 1.1f)),
            deep = Offset(0.66f + 0.16f * wave(seconds, 11f, 2f), 0.62f + 0.16f * wave(seconds, 13f)),
        )

        private fun wave(seconds: Float, period: Float, phase: Float = 0f) =
            sin(2 * PI.toFloat() * seconds / period + phase)
    }
}

/** Android 12 and older: the kit's radial gradients, with the spots moving and the highlight fixed. */
private fun DrawScope.drawSpots(spots: OrbSpots) {
    drawCircle(ArtPalette.orbBase)
    drawSpot(ArtPalette.orbLight, spots.light, reach = 0.5f)
    drawSpot(ArtPalette.orbDeep, spots.deep, reach = 0.55f)
    drawSpot(ArtPalette.orbHighlight, HIGHLIGHT, reach = 0.24f)
}

/**
 * CSS `radial-gradient(circle at x y, color 0, transparent reach)`: the gradient ray runs to the
 * farthest corner, and the color fades out over [reach] of it.
 */
private fun DrawScope.drawSpot(color: Color, at: Offset, reach: Float) {
    val center = Offset(size.width * at.x, size.height * at.y)
    val farthestCorner = hypot(max(center.x, size.width - center.x), max(center.y, size.height - center.y))
    drawCircle(
        Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = farthestCorner * reach,
        ),
    )
}

/** Android 13 and newer: the sphere as an AGSL shader whose uniforms change every frame. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class OrbShader {
    private val shader = RuntimeShader(SOURCE).apply {
        setColorUniform("baseColor", ArtPalette.orbBase.toArgb())
        setColorUniform("lightColor", ArtPalette.orbLight.toArgb())
        setColorUniform("deepColor", ArtPalette.orbDeep.toArgb())
        setColorUniform("highlightColor", ArtPalette.orbHighlight.toArgb())
        setFloatUniform("highlight", HIGHLIGHT.x, HIGHLIGHT.y)
    }
    private val brush = ShaderBrush(shader)

    fun brush(width: Float, height: Float, seconds: Float, spots: OrbSpots): Brush {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("time", seconds)
        shader.setFloatUniform("lightCenter", spots.light.x, spots.light.y)
        shader.setFloatUniform("deepCenter", spots.deep.x, spots.deep.y)
        return brush
    }

    private companion object {
        // Smooth value noise bends the coordinates, so the spots melt into each other like a fluid
        // instead of sliding as discs; the highlight uses the unbent coordinates and stays put.
        const val SOURCE = """
            uniform float2 size;
            uniform float time;
            uniform float2 lightCenter;
            uniform float2 deepCenter;
            uniform float2 highlight;
            layout(color) uniform half4 baseColor;
            layout(color) uniform half4 lightColor;
            layout(color) uniform half4 deepColor;
            layout(color) uniform half4 highlightColor;

            float hash(float2 p) {
                return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
            }

            float noise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                float2 u = f * f * (3.0 - 2.0 * f);
                float bottom = mix(hash(i), hash(i + float2(1.0, 0.0)), u.x);
                float top = mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x);
                return mix(bottom, top, u.y);
            }

            half spot(float2 uv, float2 center, float reach) {
                return half(1.0 - smoothstep(0.0, reach, distance(uv, center)));
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / size;
                float2 q = uv * 2.5;
                float2 flow = float2(
                    noise(q + float2(0.0, time * 0.3)),
                    noise(q + float2(5.2, 1.3 - time * 0.25))
                ) - 0.5;
                float2 p = uv + flow * 0.2;
                half3 color = baseColor.rgb;
                color = mix(color, lightColor.rgb, spot(p, lightCenter, 0.55));
                color = mix(color, deepColor.rgb, spot(p, deepCenter, 0.6));
                color = mix(color, highlightColor.rgb, spot(uv, highlight, 0.3));
                return half4(color, 1.0);
            }
        """
    }
}

/** The light source, top left, where the kit's highlight is. */
private val HIGHLIGHT = Offset(0.32f, 0.28f)
private const val LEVEL_SPEEDUP = 2f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MILLIS_PER_SECOND = 1_000f

@PreviewLightDark
@Composable
private fun OrbPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xxl)) {
            Orb()
            Orb(active = false)
        }
    }
}
