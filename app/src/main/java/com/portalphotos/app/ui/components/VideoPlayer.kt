package com.portalphotos.app.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
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
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMinVideoFrameRate(10)
            )
        }
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build().apply {
                val mediaSource = ProgressiveMediaSource.Factory(viewModel.cacheManager.cacheDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(streamUrl))
                setMediaSource(mediaSource)
                prepare()
                repeatMode = Player.REPEAT_MODE_OFF
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
            LivePhotoBehavior.STILL_PHOTO_ONLY -> false
        }
    } else {
        true // Real standalone video -> Always show video surface
    }

    // Effect 1: Manual LIVE motion control — triggers ONLY when isManualMotionActive changes value
    LaunchedEffect(isManualMotionActive) {
        Log.d("PortalPhotos", "ManualMotion LaunchedEffect fired! isManualMotionActive=$isManualMotionActive | isActivePage=$isActivePage")
        if (isManualMotionActive) {
            viewModel.setLiveMotionPlaying(true)
            isLivePhotoFinished = false
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.setPlaybackSpeed(1.0f)
            exoPlayer.seekTo(0)
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            viewModel.setLiveMotionPlaying(false)
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            exoPlayer.setPlaybackSpeed(1.0f)
            if (isLivePhotoItem && livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE) {
                isLivePhotoFinished = true
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
            }
        }
    }

    // Effect 2: Normal page/play state control — SKIPS entirely when manual motion is active
    LaunchedEffect(isActivePage, isPlaying, autoplay) {
        Log.d("PortalPhotos", "MainEffect LaunchedEffect isActivePage=$isActivePage | isPlaying=$isPlaying | isManualMotionActive=$isManualMotionActive | isLivePhoto=$isLivePhotoItem")
        if (isManualMotionActive) {
            Log.d("PortalPhotos", "MainEffect SKIPPED because manual motion is active")
            return@LaunchedEffect
        }

        if (!isActivePage) {
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
            if (isManualMotionActive) {
                isManualMotionActive = false
                viewModel.setLiveMotionPlaying(false)
            }
            return@LaunchedEffect
        }

        if (!isLivePhotoItem) {
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
        } else if (livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE) {
            isLivePhotoFinished = true
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
        }
    }

    // Handle Mute/Unmute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1.0f
    }

    // Listen to video completion & state changes with deep diagnostic logging
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                Log.d("PortalPhotos", "[EXO] onTracksChanged called! Inspecting ${tracks.groups.size} track groups...")
                for (group in tracks.groups) {
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                        val trackGroup = group.mediaTrackGroup
                        Log.d("PortalPhotos", "[EXO] Found Video TrackGroup with ${trackGroup.length} formats:")
                        for (i in 0 until trackGroup.length) {
                            val format = trackGroup.getFormat(i)
                            Log.d("PortalPhotos", "  -> Track $i: ${format.width}x${format.height} | frameRate=${format.frameRate} | mimeType=${format.sampleMimeType}")
                            // Override to select motion track (width <= 1920 & height <= 1920) instead of static 2048x1536 cover track
                            if ((format.width in 1..1920 || format.height in 1..1920) && (format.width != 2048 && format.height != 2048)) {
                                Log.d("PortalPhotos", "[EXO] *** FORCING SELECTION OF MOTION TRACK $i (${format.width}x${format.height}) ***")
                                val override = androidx.media3.common.TrackSelectionOverride(trackGroup, i)
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(override)
                                    .build()
                                break
                            }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateStr = when(playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d("PortalPhotos", "[EXO] onPlaybackStateChanged state=$stateStr | playWhenReady=${exoPlayer.playWhenReady} | isPlaying=${exoPlayer.isPlaying} | pos=${exoPlayer.currentPosition}/${exoPlayer.duration}")
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

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("PortalPhotos", "[EXO] onIsPlayingChanged isPlaying=$isPlaying | pos=${exoPlayer.currentPosition}")
            }

            override fun onRenderedFirstFrame() {
                Log.d("PortalPhotos", "[EXO] *** FIRST FRAME RENDERED *** videoSize=${exoPlayer.videoSize.width}x${exoPlayer.videoSize.height} | pos=${exoPlayer.currentPosition}")
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                Log.d("PortalPhotos", "[EXO] onVideoSizeChanged ${videoSize.width}x${videoSize.height}")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PortalPhotos", "[EXO] Error: ${error.localizedMessage}", error)
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

        // Layer 2 (Foreground): Motion Video Surface ALWAYS composed with TextureView
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
                    val view = android.view.LayoutInflater.from(ctx).inflate(com.portalphotos.app.R.layout.player_view, null, false)
                    (view as PlayerView).apply {
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
                    if (isManualMotionActive) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
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
                    .zIndex(10f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isManualMotionActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else Color.Black.copy(alpha = 0.6f)
                    )
                    .clickable {
                        // Toggle manual motion - the LaunchedEffect handles all ExoPlayer control
                        isManualMotionActive = !isManualMotionActive
                        Log.d("PortalPhotos", "LIVE motion pill tapped! isManualMotionActive=$isManualMotionActive")
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
