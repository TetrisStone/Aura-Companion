package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.entity.CompanionBackgroundEntity
import com.example.data.local.entity.CompanionSpriteEntity
import com.example.data.model.EmotionType

@Composable
fun CompanionVisualView(
    background: CompanionBackgroundEntity?,
    emotion: EmotionType,
    isIdle: Boolean,
    currentSpriteEntity: CompanionSpriteEntity?,
    companionName: String,
    onCompanionTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")

    // Very gentle subtle breathing animation
    val bobbingOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobbing"
    )

    // Resolve background drawable ID
    val backgroundDrawableId = remember(background?.drawableResName) {
        val name = background?.drawableResName
        if (name != null) {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) id else R.drawable.img_bg_kitchen
        } else {
            R.drawable.img_bg_kitchen
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("companion_visual_stage")
    ) {
        // --- 1. Dynamic Background Scene ---
        if (!background?.customUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(background!!.customUri)
                    .crossfade(true)
                    .build(),
                contentDescription = background.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = backgroundDrawableId),
                contentDescription = background?.name ?: "Szene",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Cinematic Vignette and Top Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x880B0A1A),
                            Color(0x110B0A1A),
                            Color(0x220B0A1A),
                            Color(0xCC090814)
                        )
                    )
                )
        )

        // --- 2. Companion Character Sprite (Standing from ground, partially covered by chatbox) ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val spriteCustomUri = currentSpriteEntity?.customImageUri

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, bobbingOffset.toInt()) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCompanionTap
                    )
                    .testTag("companion_character_sprite")
            ) {
                if (spriteCustomUri != null) {
                    // Custom sprite image: Starts near bottom edge, no frame/border
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(spriteCustomUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Companion $companionName",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .heightIn(min = 450.dp, max = 560.dp)
                            .padding(bottom = 12.dp)
                    )
                } else {
                    // Default Companion Character Vector Art:
                    // Positioned right from the bottom edge so torso is partially covered by the text box.
                    // Absolutely NO green or colored border stroke around the shape!
                    CompanionFullBodyAvatar(
                        emotion = emotion,
                        isIdle = isIdle,
                        companionName = companionName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(490.dp)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Natural Anime Companion Silhouette & Character Sprite
 * Clean, modern visual novel character rendering with NO colored border/contour strokes.
 * The torso extends to the bottom of the screen, naturally covered by the glass chatbox.
 */
@Composable
fun CompanionFullBodyAvatar(
    emotion: EmotionType,
    isIdle: Boolean,
    companionName: String,
    modifier: Modifier = Modifier
) {
    val emotionColor = getEmotionColor(emotion)

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val centerX = w * 0.5f

            // 1. TORSO / OUTFIT (Extends to the very bottom, partially covered by chatbox)
            val outfitPath = Path().apply {
                // Shoulders
                moveTo(centerX - w * 0.32f, h)
                lineTo(centerX - w * 0.28f, h * 0.58f)
                // Left collar bone / neck joint
                quadraticTo(
                    centerX - w * 0.16f, h * 0.52f,
                    centerX - w * 0.08f, h * 0.50f
                )
                // Collar dip
                quadraticTo(
                    centerX, h * 0.55f,
                    centerX + w * 0.08f, h * 0.50f
                )
                // Right shoulder
                quadraticTo(
                    centerX + w * 0.16f, h * 0.52f,
                    centerX + w * 0.28f, h * 0.58f
                )
                lineTo(centerX + w * 0.32f, h)
                close()
            }

            // Outfit Gradient (Cozy aesthetic cardigan / jacket, NO border stroke)
            val outfitBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2E2659),
                    Color(0xFF1E183F),
                    Color(0xFF140F2D)
                ),
                startY = h * 0.50f,
                endY = h
            )
            drawPath(path = outfitPath, brush = outfitBrush)

            // Inner shirt / collar accent
            val collarPath = Path().apply {
                moveTo(centerX - w * 0.08f, h * 0.50f)
                quadraticTo(centerX, h * 0.58f, centerX + w * 0.08f, h * 0.50f)
                lineTo(centerX + w * 0.05f, h * 0.65f)
                lineTo(centerX - w * 0.05f, h * 0.65f)
                close()
            }
            drawPath(
                path = collarPath,
                color = Color(0x33FFFFFF)
            )

            // 2. NECK
            val neckPath = Path().apply {
                moveTo(centerX - w * 0.055f, h * 0.44f)
                lineTo(centerX - w * 0.055f, h * 0.52f)
                lineTo(centerX + w * 0.055f, h * 0.52f)
                lineTo(centerX + w * 0.055f, h * 0.44f)
                close()
            }
            drawPath(
                path = neckPath,
                color = Color(0xFFF6C8AF)
            )
            // Neck shadow under chin
            drawOval(
                color = Color(0x332A1515),
                topLeft = Offset(centerX - w * 0.055f, h * 0.44f),
                size = Size(w * 0.11f, h * 0.04f)
            )

            // 3. BACK HAIR (Silhouette Behind Head)
            val backHairPath = Path().apply {
                moveTo(centerX - w * 0.22f, h * 0.46f)
                quadraticTo(centerX - w * 0.26f, h * 0.20f, centerX, h * 0.12f)
                quadraticTo(centerX + w * 0.26f, h * 0.20f, centerX + w * 0.22f, h * 0.46f)
                quadraticTo(centerX + w * 0.16f, h * 0.50f, centerX, h * 0.48f)
                quadraticTo(centerX - w * 0.16f, h * 0.50f, centerX - w * 0.22f, h * 0.46f)
                close()
            }
            drawPath(
                path = backHairPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF382E64), Color(0xFF221A45)),
                    startY = h * 0.12f,
                    endY = h * 0.50f
                )
            )

            // 4. FACE SHAPE (Warm gentle skin tone, smooth anime jawline, NO border stroke)
            val facePath = Path().apply {
                moveTo(centerX - w * 0.13f, h * 0.26f)
                // Left cheek to chin
                quadraticTo(
                    centerX - w * 0.12f, h * 0.38f,
                    centerX - w * 0.04f, h * 0.44f
                )
                // Rounded delicate chin
                quadraticTo(
                    centerX, h * 0.455f,
                    centerX + w * 0.04f, h * 0.44f
                )
                // Right cheek
                quadraticTo(
                    centerX + w * 0.12f, h * 0.38f,
                    centerX + w * 0.13f, h * 0.26f
                )
                close()
            }
            drawPath(
                path = facePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFDFCE), Color(0xFFF9CFBA)),
                    startY = h * 0.24f,
                    endY = h * 0.46f
                )
            )

            // Soft Blushing Cheeks
            val blushColor = Color(0x55FF6584)
            drawOval(
                color = blushColor,
                topLeft = Offset(centerX - w * 0.11f, h * 0.36f),
                size = Size(w * 0.05f, h * 0.025f)
            )
            drawOval(
                color = blushColor,
                topLeft = Offset(centerX + w * 0.06f, h * 0.36f),
                size = Size(w * 0.05f, h * 0.025f)
            )

            // 5. EYES & EXPRESSION
            val leftEyeCenter = Offset(centerX - w * 0.065f, h * 0.325f)
            val rightEyeCenter = Offset(centerX + w * 0.065f, h * 0.325f)

            when (emotion) {
                EmotionType.HAPPY, EmotionType.PLAYFUL -> {
                    // Gentle happy curved eyelashes
                    drawArc(
                        color = Color(0xFF2C1B38),
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(leftEyeCenter.x - w * 0.035f, leftEyeCenter.y - h * 0.015f),
                        size = Size(w * 0.07f, h * 0.03f),
                        style = Stroke(width = 3.5.dp.toPx())
                    )
                    drawArc(
                        color = Color(0xFF2C1B38),
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(rightEyeCenter.x - w * 0.035f, rightEyeCenter.y - h * 0.015f),
                        size = Size(w * 0.07f, h * 0.03f),
                        style = Stroke(width = 3.5.dp.toPx())
                    )
                }
                EmotionType.THOUGHTFUL -> {
                    // Thoughtful glancing eyes with soft pupils
                    drawCircle(color = Color(0xFF1E1430), radius = w * 0.032f, center = leftEyeCenter)
                    drawCircle(color = Color(0xFF64B5F6), radius = w * 0.022f, center = Offset(leftEyeCenter.x + 2f, leftEyeCenter.y - 2f))
                    drawCircle(color = Color.White, radius = w * 0.012f, center = Offset(leftEyeCenter.x + 4f, leftEyeCenter.y - 4f))

                    drawCircle(color = Color(0xFF1E1430), radius = w * 0.032f, center = rightEyeCenter)
                    drawCircle(color = Color(0xFF64B5F6), radius = w * 0.022f, center = Offset(rightEyeCenter.x + 2f, rightEyeCenter.y - 2f))
                    drawCircle(color = Color.White, radius = w * 0.012f, center = Offset(rightEyeCenter.x + 4f, rightEyeCenter.y - 4f))
                }
                EmotionType.EXCITED, EmotionType.SURPRISED -> {
                    // Bright sparkling eyes with dual highlights
                    drawCircle(color = Color(0xFF1E1430), radius = w * 0.036f, center = leftEyeCenter)
                    drawCircle(color = Color(0xFFFF4081), radius = w * 0.026f, center = leftEyeCenter)
                    drawCircle(color = Color.White, radius = w * 0.014f, center = Offset(leftEyeCenter.x - 3f, leftEyeCenter.y - 3f))
                    drawCircle(color = Color.White, radius = w * 0.007f, center = Offset(leftEyeCenter.x + 4f, leftEyeCenter.y + 3f))

                    drawCircle(color = Color(0xFF1E1430), radius = w * 0.036f, center = rightEyeCenter)
                    drawCircle(color = Color(0xFFFF4081), radius = w * 0.026f, center = rightEyeCenter)
                    drawCircle(color = Color.White, radius = w * 0.014f, center = Offset(rightEyeCenter.x - 3f, rightEyeCenter.y - 3f))
                    drawCircle(color = Color.White, radius = w * 0.007f, center = Offset(rightEyeCenter.x + 4f, rightEyeCenter.y + 3f))
                }
                EmotionType.IDLE_PHONE, EmotionType.IDLE_BOOK -> {
                    // Relaxed downward gazing eyes (concentrating on phone/book)
                    drawLine(
                        color = Color(0xFF2C1B38),
                        start = Offset(leftEyeCenter.x - w * 0.035f, leftEyeCenter.y + 1f),
                        end = Offset(leftEyeCenter.x + w * 0.035f, leftEyeCenter.y + 4f),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF2C1B38),
                        start = Offset(rightEyeCenter.x - w * 0.035f, rightEyeCenter.y + 4f),
                        end = Offset(rightEyeCenter.x + w * 0.035f, rightEyeCenter.y + 1f),
                        strokeWidth = 3.dp.toPx()
                    )
                }
                else -> {
                    // Warm gentle open eyes
                    drawCircle(color = Color(0xFF1E1430), radius = w * 0.032f, center = leftEyeCenter)
                    drawCircle(color = Color(0xFF9575CD), radius = w * 0.022f, center = leftEyeCenter)
                    drawCircle(color = Color.White, radius = w * 0.012f, center = Offset(leftEyeCenter.x - 2f, leftEyeCenter.y - 2f))

                    drawCircle(color = Color(0xFF1E1430), radius = w * 0.032f, center = rightEyeCenter)
                    drawCircle(color = Color(0xFF9575CD), radius = w * 0.022f, center = rightEyeCenter)
                    drawCircle(color = Color.White, radius = w * 0.012f, center = Offset(rightEyeCenter.x - 2f, rightEyeCenter.y - 2f))
                }
            }

            // Eyebrows
            drawArc(
                color = Color(0xFF382E64),
                startAngle = 190f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - w * 0.035f, leftEyeCenter.y - h * 0.035f),
                size = Size(w * 0.07f, h * 0.02f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = Color(0xFF382E64),
                startAngle = 210f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - w * 0.035f, rightEyeCenter.y - h * 0.035f),
                size = Size(w * 0.07f, h * 0.02f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Delicate Nose
            drawCircle(
                color = Color(0x66B07A65),
                radius = 2.dp.toPx(),
                center = Offset(centerX, h * 0.37f)
            )

            // Mouth
            when (emotion) {
                EmotionType.HAPPY, EmotionType.EXCITED -> {
                    // Cheerful open smile
                    val mouthPath = Path().apply {
                        moveTo(centerX - w * 0.035f, h * 0.405f)
                        quadraticTo(centerX, h * 0.435f, centerX + w * 0.035f, h * 0.405f)
                        close()
                    }
                    drawPath(path = mouthPath, color = Color(0xFFE57373))
                }
                EmotionType.SURPRISED -> {
                    drawOval(
                        color = Color(0xFFE57373),
                        topLeft = Offset(centerX - w * 0.015f, h * 0.405f),
                        size = Size(w * 0.03f, h * 0.025f)
                    )
                }
                else -> {
                    // Small relaxed friendly smile
                    drawArc(
                        color = Color(0xFF9E4B4B),
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(centerX - w * 0.025f, h * 0.405f),
                        size = Size(w * 0.05f, h * 0.015f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // 6. FRONT HAIR & BANGS (Stylish anime fringe, NO border stroke)
            val frontHairPath = Path().apply {
                moveTo(centerX - w * 0.17f, h * 0.22f)
                // Left fringe lock
                lineTo(centerX - w * 0.12f, h * 0.32f)
                lineTo(centerX - w * 0.08f, h * 0.24f)
                // Middle fringe lock
                lineTo(centerX - w * 0.02f, h * 0.31f)
                lineTo(centerX + w * 0.04f, h * 0.24f)
                // Right fringe lock
                lineTo(centerX + w * 0.10f, h * 0.30f)
                lineTo(centerX + w * 0.17f, h * 0.22f)
                // Crown arch
                quadraticTo(centerX, h * 0.11f, centerX - w * 0.17f, h * 0.22f)
                close()
            }
            drawPath(
                path = frontHairPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF4A3E7D), Color(0xFF2C2454)),
                    startY = h * 0.11f,
                    endY = h * 0.32f
                )
            )

            // Subtle hair highlight shimmer
            val highlightPath = Path().apply {
                moveTo(centerX - w * 0.10f, h * 0.20f)
                quadraticTo(centerX, h * 0.18f, centerX + w * 0.10f, h * 0.20f)
            }
            drawPath(
                path = highlightPath,
                color = Color(0x33B39DDB),
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // --- In Idle Mode: Minimal subtle item indicator (Phone or Book) ---
        if (isIdle) {
            Surface(
                color = Color(0xDD15132B),
                shape = CircleShape,
                modifier = Modifier
                    .size(46.dp)
                    .align(Alignment.Center)
                    .offset(y = 40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (emotion == EmotionType.IDLE_PHONE) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Handy daddeln",
                            tint = Color(0xFF64FFDA),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Buch lesen",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getEmotionColor(emotion: EmotionType): Color {
    return when (emotion) {
        EmotionType.HAPPY -> Color(0xFFFFD54F) // Radiant warm gold
        EmotionType.THOUGHTFUL -> Color(0xFF64B5F6) // Clear celestial sky blue
        EmotionType.EXCITED -> Color(0xFFFF4081) // Vibrant neon magenta
        EmotionType.EMPATHETIC -> Color(0xFF81C784) // Gentle emerald mint
        EmotionType.SURPRISED -> Color(0xFFFFB74D) // Vivid warm amber
        EmotionType.PLAYFUL -> Color(0xFFBA68C8) // Playful electric violet
        EmotionType.IDLE_PHONE -> Color(0xFF4DD0E1) // Digital cyan glow
        EmotionType.IDLE_BOOK -> Color(0xFFFFCA28) // Cozy parchment amber
    }
}

fun getEmotionIcon(emotion: EmotionType) = when (emotion) {
    EmotionType.HAPPY -> Icons.Default.SentimentVerySatisfied
    EmotionType.THOUGHTFUL -> Icons.Default.Psychology
    EmotionType.EXCITED -> Icons.Default.AutoAwesome
    EmotionType.EMPATHETIC -> Icons.Default.Favorite
    EmotionType.SURPRISED -> Icons.Default.AutoAwesome
    EmotionType.PLAYFUL -> Icons.Default.SentimentSatisfied
    EmotionType.IDLE_PHONE -> Icons.Default.PhoneAndroid
    EmotionType.IDLE_BOOK -> Icons.AutoMirrored.Filled.MenuBook
}

@Composable
fun CompanionAvatarShape(
    emotion: EmotionType,
    isIdle: Boolean,
    pulseGlow: Float = 1f,
    companionName: String = "",
    modifier: Modifier = Modifier
) {
    CompanionFullBodyAvatar(
        emotion = emotion,
        isIdle = isIdle,
        companionName = companionName,
        modifier = modifier
    )
}
