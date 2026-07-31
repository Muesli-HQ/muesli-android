package com.phequals7.muesli.ui.launch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.R
import com.phequals7.muesli.engine.SherpaRecognizerHolder
import com.phequals7.muesli.theme.MuesliTheme

/**
 * First-launch warmup screen, ported from muesli-ios LaunchWarmupView:
 * brand mark + gradient backdrop while the Parakeet model primes for
 * inference, status line with pulse, version footer. The caller dismisses
 * when the model is warm or after the 5-second display cap.
 */
@Composable
fun LaunchWarmupScreen(
    warmState: SherpaRecognizerHolder.WarmState,
    modelName: String,
    versionText: String,
) {
    val colors = MuesliTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF030711), Color(0xFF07111F), Color(0xFF02040A)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
    ) {
        // Subtle brand tint overlay (brandBlue top → syncGreen bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.brandBlue.copy(alpha = 0.16f),
                            Color.Transparent,
                            colors.syncGreen.copy(alpha = 0.10f),
                        )
                    )
                )
        )

        // Brand mark: official icon + wordmark (iOS: offset y −18)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-18).dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.muesli_app_icon),
                contentDescription = "Muesli",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(74.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = colors.brandBlue,
                        spotColor = colors.brandBlue,
                    )
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                "muesli",
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp
            )
        }

        // Warmup status block (iOS: pinned 78dp above bottom)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 78.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (warmState == SherpaRecognizerHolder.WarmState.FAILED) {
                    Icon(
                        Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.68f),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.syncGreen
                    )
                }
                Text(
                    text = when (warmState) {
                        SherpaRecognizerHolder.WarmState.READY -> "Transcription engine ready"
                        SherpaRecognizerHolder.WarmState.FAILED -> "Warmup paused"
                        else -> "Warming up the transcription engine..."
                    },
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            WarmupPulseLine(
                isAnimating = warmState == SherpaRecognizerHolder.WarmState.WARMING,
                modifier = Modifier
                    .width(176.dp)
                    .height(3.dp)
            )

            Text(
                modelName,
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 12.sp
            )
        }

        // Version footer (iOS: caption, white 0.24, 18dp above bottom)
        Text(
            versionText,
            color = Color.White.copy(alpha = 0.24f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        )
    }
}

/** Sliding gradient blob on a capsule track (iOS WarmupPulseLine). */
@Composable
private fun WarmupPulseLine(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MuesliTheme.colors
    val transition = rememberInfiniteTransition(label = "warmup-pulse")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150),
            repeatMode = RepeatMode.Restart,
        ),
        label = "warmup-pulse-fraction"
    )

    BoxWithConstraints(modifier = modifier.clip(CircleShape)) {
        val trackWidth = maxWidth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .width(trackWidth * 0.36f)
                .fillMaxHeight()
                .offset(x = if (isAnimating) trackWidth * 0.64f * fraction else 0.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colors.brandBlue.copy(alpha = 0.25f),
                            colors.syncGreen,
                            colors.brandBlue.copy(alpha = 0.25f),
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}
