package com.portalphotos.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.portalphotos.app.data.crop.SmartCropDetector
import com.portalphotos.app.data.prefs.ScalingMode
import com.portalphotos.app.data.prefs.TransitionEffect
import com.portalphotos.app.ui.viewmodel.ViewerViewModel

@Composable
fun ImageViewer(
    imageUrl: String,
    scalingMode: ScalingMode,
    transitionEffect: TransitionEffect,
    viewModel: ViewerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loadedBitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }

    // Initialize focalPoint directly from cache on Frame 0 for 0ms instant alignment
    val initialFocal = remember(imageUrl) {
        viewModel.cacheManager.focalPointCache[imageUrl] ?: Offset(0.5f, 0.5f)
    }
    var focalPoint by remember(imageUrl) { mutableStateOf(initialFocal) }

    // Auto-detect Portrait orientation: If portrait & Smart Crop enabled, automatically use FIT mode!
    val effectiveScalingMode = remember(scalingMode, loadedBitmap) {
        val bmp = loadedBitmap
        if (bmp != null && bmp.height > bmp.width && scalingMode == ScalingMode.FILL_SMART_CROP) {
            ScalingMode.FIT
        } else {
            scalingMode
        }
    }

    // Smoothly animate any focal point adjustments with a gentle cinematic camera pan
    val animatedFocalPoint by animateOffsetAsState(
        targetValue = focalPoint,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "FocalPointPan"
    )

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false) // Required to extract bitmap for ML Kit Face/Object detection & orientation check
            .crossfade(true)
            .build(),
        onSuccess = { state ->
            val drawable = state.result.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                loadedBitmap = drawable.bitmap
            }
        }
    )

    // Calculate smart crop focal point when bitmap loads if not already in cache
    LaunchedEffect(loadedBitmap, effectiveScalingMode) {
        val bitmap = loadedBitmap
        if (bitmap != null && effectiveScalingMode == ScalingMode.FILL_SMART_CROP) {
            focalPoint = viewModel.getFocalPoint(imageUrl, bitmap)
        }
    }

    // Ken Burns subtle zoom transition animation evaluated ONLY when enabled
    val kenBurnsScale = if (transitionEffect == TransitionEffect.KEN_BURNS) {
        val infiniteTransition = rememberInfiniteTransition(label = "KenBurns")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Scale"
        )
        scale
    } else {
        1.0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (effectiveScalingMode) {
            ScalingMode.FIT -> {
                // Background: Blurred full-bleed photo
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(30.dp)
                        .graphicsLayer(alpha = 0.6f)
                )
                // Foreground: 100% complete photo without cropping
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = kenBurnsScale,
                            scaleY = kenBurnsScale
                        )
                )
            }
            ScalingMode.FILL_CENTER -> {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = kenBurnsScale,
                            scaleY = kenBurnsScale
                        )
                )
            }
            ScalingMode.FILL_SMART_CROP -> {
                val biasAlignment = remember(animatedFocalPoint) {
                    SmartCropDetector.toBiasAlignment(animatedFocalPoint)
                }

                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = biasAlignment, // Smartly anchored on faces/artwork!
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = kenBurnsScale,
                            scaleY = kenBurnsScale
                        )
                )
            }
        }
    }
}
