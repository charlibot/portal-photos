package com.portalphotos.app.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosAuto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.Coil
import coil.request.ImageRequest
import com.portalphotos.app.data.crop.SmartCropDetector
import com.portalphotos.app.data.prefs.LivePhotoBehavior
import com.portalphotos.app.data.prefs.ScalingMode
import com.portalphotos.app.ui.viewmodel.ViewerViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    imageUrl: String,
    streamUrl: String,
    isLivePhotoItem: Boolean,
    isActivePage: Boolean,
    autoplay: Boolean,
    isMuted: Boolean,
    livePhotoBehavior: LivePhotoBehavior,
    onToggleMute: () -> Unit,
    onVideoCompleted: () -> Unit,
    viewModel: ViewerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currentIsActivePage by rememberUpdatedState(isActivePage)
    val currentOnVideoCompleted by rememberUpdatedState(onVideoCompleted)
    val userSettings by viewModel.userSettings.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val initialIsFinished = remember(streamUrl) {
        (isLivePhotoItem && livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE)
    }
    var isLivePhotoFinished by remember(streamUrl) { mutableStateOf(initialIsFinished) }
    var isManualMotionActive by remember(streamUrl) { mutableStateOf(false) }
    var livePhotoLoopCount by remember(streamUrl) { mutableStateOf(0) }
    var loadedBitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }

    // Auto-detect Portrait orientation: If portrait & Smart Crop enabled, automatically use FIT mode!
    val effectiveScalingMode = remember(userSettings.scalingMode, loadedBitmap) {
        val bmp = loadedBitmap
        if (bmp != null && bmp.height > bmp.width && userSettings.scalingMode == ScalingMode.FILL_SMART_CROP) {
            ScalingMode.FIT
        } else {
            userSettings.scalingMode
        }
    }

    // Synchronize initial smart crop focal point for 1:1 alignment matching
    val initialFocal = remember(imageUrl) {
        viewModel.cacheManager.focalPointCache[imageUrl] ?: Offset(0.5f, 0.5f)
    }
    var focalPoint by remember(imageUrl) { mutableStateOf(initialFocal) }

    val animatedFocalPoint by animateOffsetAsState(
        targetValue = focalPoint,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "VideoFocalPointPan"
    )

    // Pre-download high-res photo in background
    DisposableEffect(imageUrl) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .target { drawable ->
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    loadedBitmap = drawable.bitmap
                }
            }
            .build()
        Coil.imageLoader(context).enqueue(request)
        onDispose {}
    }

    LaunchedEffect(loadedBitmap, effectiveScalingMode) {
        val bmp = loadedBitmap
        if (bmp != null && effectiveScalingMode == ScalingMode.FILL_SMART_CROP) {
            focalPoint = viewModel.getFocalPoint(imageUrl, bmp)
        }
    }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaSource = ProgressiveMediaSource.Factory(viewModel.cacheManager.cacheDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamUrl))
            setMediaSource(mediaSource)
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Configure loop mode if user tapped Motion Pill on Live Photo & synchronize ViewModel play state
    LaunchedEffect(isManualMotionActive) {
        if (isManualMotionActive) {
            viewModel.pausePlayback()
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Handle Still Photo Only mode EXCLUSIVELY for Live Photos
    if (isLivePhotoItem && livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_ONLY) {
        ImageViewer(
            imageUrl = imageUrl,
            scalingMode = userSettings.scalingMode,
            transitionEffect = userSettings.transitionEffect,
            viewModel = viewModel,
            modifier = modifier
        )
        LaunchedEffect(isActivePage) {
            if (isActivePage) {
                delay(userSettings.slideshowTimerSeconds * 1000L)
                if (currentIsActivePage) {
                    currentOnVideoCompleted()
                }
            }
        }
        return
    }

    // Determine whether motion video surface should be visible
    val showVideoSurface = if (isLivePhotoItem) {
        when (livePhotoBehavior) {
            LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE -> isManualMotionActive // Video appears ONLY when LIVE button is pressed!
            LivePhotoBehavior.PLAY_MOTION_ONCE -> !isLivePhotoFinished
            LivePhotoBehavior.PLAY_AS_VIDEO -> true
            LivePhotoBehavior.STILL_PHOTO_ONLY -> false
        }
    } else {
        true // Real standalone video -> Always show video surface
    }

    // Handle Active Page focus & Global Play/Pause state for videos
    LaunchedEffect(isActivePage, isPlaying, autoplay, showVideoSurface) {
        Log.d("PortalPhotos", "VideoPlayer LaunchedEffect isActivePage=$isActivePage | isPlaying=$isPlaying | isLivePhoto=$isLivePhotoItem | showVideoSurface=$showVideoSurface")
        if (isActivePage) {
            if (!isLivePhotoItem) {
                // Real standalone video: Pause / Resume ExoPlayer based on global isPlaying state!
                if (isPlaying) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                } else {
                    exoPlayer.playWhenReady = false
                    exoPlayer.pause()
                }
            } else if (livePhotoBehavior == LivePhotoBehavior.PLAY_MOTION_ONCE) {
                isLivePhotoFinished = false
                livePhotoLoopCount = 0
                exoPlayer.seekTo(0)
                if (isPlaying && autoplay) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                } else {
                    exoPlayer.playWhenReady = false
                    exoPlayer.pause()
                }
            } else if (livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE && !isManualMotionActive) {
                isLivePhotoFinished = true
            }
        } else {
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
            if (isManualMotionActive) {
                isManualMotionActive = false
                viewModel.resumePlayback()
            }
        }
    }

    // Handle Mute/Unmute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1.0f
    }

    // Handle Live Photo photo hold timer (Guaranteed countdown for active page when not in manual motion)
    LaunchedEffect(isLivePhotoFinished, isActivePage, isManualMotionActive, userSettings.slideshowTimerSeconds) {
        Log.d("PortalPhotos", "HoldTimer LaunchedEffect isFinished=$isLivePhotoFinished | isActive=$isActivePage | manualMotion=$isManualMotionActive")
        if (isLivePhotoFinished && isActivePage && !isManualMotionActive) {
            val holdDurationMs = (userSettings.slideshowTimerSeconds * 1000L).coerceAtLeast(3000L)
            Log.d("PortalPhotos", "Holding high-res photo for ${holdDurationMs}ms...")
            delay(holdDurationMs)
            if (currentIsActivePage && !isManualMotionActive) {
                Log.d("PortalPhotos", "Hold timer finished -> calling onVideoCompleted()")
                currentOnVideoCompleted()
            }
        }
    }

    // Listen to video completion (STATE_ENDED)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("PortalPhotos", "onPlaybackStateChanged state=$playbackState | active=$currentIsActivePage | url=${streamUrl.take(30)}")
                if (playbackState == Player.STATE_ENDED && currentIsActivePage) {
                    if (isLivePhotoItem && livePhotoBehavior == LivePhotoBehavior.PLAY_MOTION_ONCE && !isManualMotionActive) {
                        livePhotoLoopCount++
                        if (livePhotoLoopCount < 2) {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        } else {
                            isLivePhotoFinished = true
                        }
                    } else if (!isManualMotionActive) {
                        currentOnVideoCompleted()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PortalPhotos", "ExoPlayer Error: ${error.localizedMessage}", error)
                if (currentIsActivePage && !isManualMotionActive) {
                    currentOnVideoCompleted()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Layer 1 (Background): High-Res Photo ALWAYS rendered underneath
        ImageViewer(
            imageUrl = imageUrl,
            scalingMode = effectiveScalingMode,
            transitionEffect = userSettings.transitionEffect,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2 (Foreground): Motion Video Surface (appears ONLY when showVideoSurface is true!)
        val targetBiasAlignment = remember(animatedFocalPoint) {
            SmartCropDetector.toBiasAlignment(animatedFocalPoint)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = when (effectiveScalingMode) {
                ScalingMode.FIT, ScalingMode.FILL_CENTER -> Alignment.Center
                ScalingMode.FILL_SMART_CROP -> targetBiasAlignment
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = when (effectiveScalingMode) {
                        ScalingMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        ScalingMode.FILL_CENTER, ScalingMode.FILL_SMART_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = if (showVideoSurface) 1f else 0f)
            )
        }

        // Google Photos Style Interactive Live Motion Toggle Pill (Top-Left)
        if (isLivePhotoItem) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isManualMotionActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else Color.Black.copy(alpha = 0.6f)
                    )
                    .clickable {
                        isManualMotionActive = !isManualMotionActive
                        if (isManualMotionActive) {
                            isLivePhotoFinished = false
                            exoPlayer.seekTo(0)
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        } else {
                            isLivePhotoFinished = true
                            exoPlayer.pause()
                            viewModel.resumePlayback()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MotionPhotosAuto,
                        contentDescription = "Motion Photo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isManualMotionActive) "MOTION ON" else "LIVE",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
