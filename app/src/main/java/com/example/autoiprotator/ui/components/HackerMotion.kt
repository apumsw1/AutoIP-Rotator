package com.example.autoiprotator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.ui.theme.JetBrainsMonoFontFamily
import com.example.autoiprotator.ui.theme.VpnPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Smoothly cycles through Hacker Neon colors over time, modulated by tunnel status.
 */
@Composable
fun rememberHackerSpectrumColor(
    tunnelStatus: TunnelStatus = TunnelStatus.DISCONNECTED,
    phaseOffset: Float = 0f
): Color {
    val transition = rememberInfiniteTransition(label = "hackerSpectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spectrumPhase"
    )

    val shifted = (phase + phaseOffset) % 1f
    val paletteCycle = when (tunnelStatus) {
        TunnelStatus.CONNECTED -> listOf(
            VpnPalette.EmeraldVerified,
            VpnPalette.CyanPrimary,
            VpnPalette.MatrixLime,
            VpnPalette.EmeraldVerified
        )
        TunnelStatus.CONNECTING,
        TunnelStatus.AUTO_SELECTING,
        TunnelStatus.DISCONNECTING -> listOf(
            VpnPalette.AmberWarn,
            VpnPalette.CyanPrimary,
            VpnPalette.EmeraldVerified,
            VpnPalette.AmberWarn
        )
        TunnelStatus.ERROR -> listOf(
            VpnPalette.CoralError,
            VpnPalette.NeonMagenta,
            VpnPalette.AmberWarn,
            VpnPalette.CoralError
        )
        TunnelStatus.DISCONNECTED -> listOf(
            VpnPalette.CyanPrimary,
            VpnPalette.EmeraldVerified,
            VpnPalette.NeonViolet,
            VpnPalette.CyanPrimary
        )
    }

    val segments = paletteCycle.size - 1
    val scaled = shifted * segments
    val index = scaled.toInt().coerceIn(0, segments - 1)
    val localT = scaled - index
    val targetColor = lerp(paletteCycle[index], paletteCycle[index + 1], localT)

    val smoothed by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 350),
        label = "smoothedSpectrumColor"
    )
    return smoothed
}

/**
 * Full-screen animated Hacker background: subtle cyber grid, falling matrix data streams,
 * and low-contrast CRT scanline texture so foreground cards stay crisp.
 */
@Composable
fun HackerBackdrop(
    accentColor: Color,
    secondaryColor: Color,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "matrixRain")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainTick"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Deep cyber-void radial backdrop
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    VpnPalette.ObsidianBg,
                    VpnPalette.SlateSurface.copy(alpha = 0.92f),
                    VpnPalette.ObsidianBg
                )
            )
        )

        // Subtle atmospheric glow top-center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (isConnected) 0.14f else 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.18f),
                radius = size.maxDimension * 0.55f
            ),
            radius = size.maxDimension * 0.55f,
            center = Offset(size.width * 0.5f, size.height * 0.18f)
        )

        // Perspective cyber grid lines
        val gridSpacing = 44.dp.toPx()
        val gridAlpha = if (isConnected) 0.08f else 0.045f
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = accentColor.copy(alpha = gridAlpha),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += gridSpacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = secondaryColor.copy(alpha = gridAlpha * 0.85f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += gridSpacing
        }

        // Falling digital matrix packet streams
        val columns = 18
        val colStep = size.width / columns
        val streamLen = size.height * 0.24f
        for (i in 0 until columns) {
            val speed = 1f + (i % 4) * 0.35f
            val offsetPhase = ((tick * speed + (i * 0.173f)) % 1.35f) - 0.25f
            val headY = offsetPhase * size.height
            val tailY = headY - streamLen
            val streamX = i * colStep + colStep * 0.5f
            val streamColor = if (i % 3 == 0) secondaryColor else accentColor

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        streamColor.copy(alpha = if (isConnected) 0.22f else 0.12f),
                        streamColor.copy(alpha = if (isConnected) 0.52f else 0.25f)
                    ),
                    startY = tailY,
                    endY = headY
                ),
                start = Offset(streamX, tailY),
                end = Offset(streamX, headY),
                strokeWidth = if (i % 2 == 0) 2.2f else 1.4f,
                cap = StrokeCap.Round
            )

            // Bright packet node head
            drawCircle(
                color = streamColor.copy(alpha = if (isConnected) 0.55f else 0.28f),
                radius = 2.6f,
                center = Offset(streamX, headY)
            )
        }

        // Ultra-subtle CRT scanline texture
        val scanStep = 6.dp.toPx()
        var scanY = 0f
        while (scanY < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.14f),
                start = Offset(0f, scanY),
                end = Offset(size.width, scanY),
                strokeWidth = 1.2f
            )
            scanY += scanStep
        }
    }
}

/**
 * Animated Cipher-Decode Text ("writing animation"): decodes characters from hacker glyphs
 * whenever [text] changes or first appears.
 */
@Composable
fun ScrambleRevealText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = VpnPalette.TextPrimary,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    stepDelayMs: Long = 22L
) {
    val glyphs = remember { "01ABCDEF#@$%&*<>[]{}/\\" }
    var displayed by remember(text) { mutableStateOf(text) }

    LaunchedEffect(text) {
        if (text.isEmpty()) {
            displayed = ""
            return@LaunchedEffect
        }
        val length = text.length
        val steps = length.coerceIn(6, 20)
        for (step in 0..steps) {
            val revealCount = ((step.toFloat() / steps) * length).toInt()
            val sb = StringBuilder(length)
            for (i in 0 until length) {
                val ch = text[i]
                if (i < revealCount || ch == ' ' || ch == '\n') {
                    sb.append(ch)
                } else {
                    sb.append(glyphs[(i * 7 + step * 3) % glyphs.length])
                }
            }
            displayed = sb.toString()
            delay(stepDelayMs)
        }
        displayed = text
    }

    Text(
        text = displayed,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Typewriter terminal line with blinking cyber block cursor.
 */
@Composable
fun TypewriterTerminalText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = VpnPalette.EmeraldVerified,
    prefix: String = "> ",
    showCursor: Boolean = true,
    charDelayMs: Long = 18L,
    textAlign: TextAlign? = null
) {
    var visibleCount by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        visibleCount = 0
        for (i in 1..text.length) {
            visibleCount = i
            delay(charDelayMs)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    val cursorChar = if (showCursor && cursorAlpha > 0.45f) " _" else if (showCursor) "  " else ""
    Text(
        text = "$prefix${text.take(visibleCount)}$cursorChar",
        modifier = modifier,
        style = style.copy(fontFamily = JetBrainsMonoFontFamily),
        color = color,
        textAlign = textAlign
    )
}

/**
 * Chamfered Cyber HUD Card with animated gradient border and corner telemetry brackets.
 */
@Composable
fun CyberHudCard(
    modifier: Modifier = Modifier,
    borderColor: Color,
    secondaryBorderColor: Color = borderColor.copy(alpha = 0.35f),
    containerColor: Color = VpnPalette.SlateCard.copy(alpha = 0.90f),
    cornerCut: Dp = 16.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = CutCornerShape(
        topStart = cornerCut,
        bottomEnd = cornerCut,
        topEnd = 4.dp,
        bottomStart = 4.dp
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val bracketLen = 14.dp.toPx()
                val stroke = 2.dp.toPx()
                // Top-right corner tick
                drawLine(
                    color = borderColor.copy(alpha = 0.85f),
                    start = Offset(size.width - bracketLen, 2f),
                    end = Offset(size.width - 2f, 2f),
                    strokeWidth = stroke
                )
                drawLine(
                    color = borderColor.copy(alpha = 0.85f),
                    start = Offset(size.width - 2f, 2f),
                    end = Offset(size.width - 2f, bracketLen),
                    strokeWidth = stroke
                )
                // Bottom-left corner tick
                drawLine(
                    color = secondaryBorderColor.copy(alpha = 0.85f),
                    start = Offset(2f, size.height - bracketLen),
                    end = Offset(2f, size.height - 2f),
                    strokeWidth = stroke
                )
                drawLine(
                    color = secondaryBorderColor.copy(alpha = 0.85f),
                    start = Offset(2f, size.height - 2f),
                    end = Offset(bracketLen, size.height - 2f),
                    strokeWidth = stroke
                )
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor.copy(alpha = 0.78f),
                    secondaryBorderColor.copy(alpha = 0.45f),
                    borderColor.copy(alpha = 0.78f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Live Encrypted Packet Oscilloscope / Waveform Canvas shown inside the tunnel telemetry.
 */
@Composable
fun EncryptedPacketWaveform(
    accentColor: Color,
    secondaryColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1400 else 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            val amp = if (isActive) size.height * 0.34f else size.height * 0.10f

            // Baseline grid line
            drawLine(
                color = accentColor.copy(alpha = 0.18f),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1f
            )

            val pathPrimary = Path()
            val pathSecondary = Path()
            val samples = 64
            for (i in 0..samples) {
                val progress = i.toFloat() / samples
                val x = progress * size.width
                val envelope = sin(progress * PI).toFloat()
                val y1 = midY + sin(progress * 10f + phase) * amp * envelope *
                    (0.75f + 0.25f * sin(progress * 23f - phase * 1.6f))
                val y2 = midY + sin(progress * 7f - phase * 1.2f) * (amp * 0.65f) * envelope
                if (i == 0) {
                    pathPrimary.moveTo(x, y1)
                    pathSecondary.moveTo(x, y2)
                } else {
                    pathPrimary.lineTo(x, y1)
                    pathSecondary.lineTo(x, y2)
                }
            }

            drawPath(
                path = pathSecondary,
                color = secondaryColor.copy(alpha = if (isActive) 0.45f else 0.18f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = pathPrimary,
                brush = Brush.horizontalGradient(
                    colors = listOf(secondaryColor, accentColor, secondaryColor)
                ),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
