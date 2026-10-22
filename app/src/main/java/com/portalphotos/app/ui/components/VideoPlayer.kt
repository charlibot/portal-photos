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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import coil.compose.rememberAsyncImagePainter
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
    itemWidth: Int = 0,
    itemHeight: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currentIsActivePage by rememberUpdatedState(isActivePage)
    val currentOnVideoCompleted by rememberUpdatedState(onVideoCompleted)
    val userSettings by viewModel.userSettings.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val initialIsFinished = remember(streamUrl, isLivePhotoItem, livePhotoBehavior) {
        (isLivePhotoItem && livePhotoBehavior != LivePhotoBehavior.PLAY_MOTION_ONCE)
    }
    var isLivePhotoFinished by remember(streamUrl) { mutableStateOf(initialIsFinished) }
    var isManualMotionActive by remember(streamUrl) { mutableStateOf(false) }
    var isManualVideoPlaying by remember(streamUrl) { mutableStateOf(false) }
    var livePhotoLoopCount by remember(streamUrl) { mutableStateOf(0) }
    var activeVideoWidth by remember(streamUrl) { mutableStateOf(0) }
    var activeVideoHeight by remember(streamUrl) { mutableStateOf(0) }

    // Reset manual playback states when leaving the active page
    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            if (isManualMotionActive) {
                isManualMotionActive = false
                viewModel.setLiveMotionPlaying(false)
            }
            if (isManualVideoPlaying) {
                isManualVideoPlaying = false
                viewModel.setLiveMotionPlaying(false)
            }
        } else if (isLivePhotoItem && livePhotoBehavior == LivePhotoBehavior.PLAY_MOTION_ONCE) {
            isLivePhotoFinished = false
            livePhotoLoopCount = 0
        }
    }

    // Ensure liveMotionPlaying flag is cleaned up if composable is disposed while active
    DisposableEffect(Unit) {
        onDispose {
            if (isManualMotionActive || isManualVideoPlaying) {
                viewModel.setLiveMotionPlaying(false)
            }
        }
    }

    // Auto-detect Portrait orientation from known item dimensions and active video track
    val isPortraitMedia = remember(itemWidth, itemHeight, activeVideoWidth, activeVideoHeight) {
        (itemWidth > 0 && itemHeight > itemWidth) || (activeVideoWidth > 0 && activeVideoHeight > activeVideoWidth)
    }

    val effectiveScalingMode = remember(userSettings.scalingMode, isPortraitMedia) {
        if (isPortraitMedia && userSettings.scalingMode == ScalingMode.FILL_SMART_CROP) {
            ScalingMode.FIT
        } else {
            userSettings.scalingMode
        }
    }

    val animatedFocalPoint by animateOffsetAsState(
        targetValue = remember(imageUrl) {
            viewModel.cacheManager.focalPointCache[imageUrl] ?: Offset(0.5f, 0.5f)
        },
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "FocalPointPanVideo"
    )

    // Handle Still Photo Only mode EXCLUSIVELY for Live Photos
    if (isLivePhotoItem && livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_ONLY) {
        ImageViewer(
            imageUrl = imageUrl,
            scalingMode = userSettings.scalingMode,
            transitionEffect = userSettings.transitionEffect,
            viewModel = viewModel,
            isActivePage = isActivePage,
            modifier = modifier
        )
        return
    }

    // Determine whether video playback is actually desired right now.
    // Do NOT instantiate ExoPlayer or PlayerView (TextureView) unless active and playing!
    val wantsVideoPlayback = if (isLivePhotoItem) {
        when (livePhotoBehavior) {
            LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE -> isManualMotionActive
            LivePhotoBehavior.PLAY_MOTION_ONCE -> (autoplay && isPlaying && !isLivePhotoFinished) || isManualMotionActive
            LivePhotoBehavior.STILL_PHOTO_ONLY -> false
        }
    } else {
        (autoplay && isPlaying) || isManualVideoPlaying
    }

    val shouldMountVideoPlayer = isActivePage && wantsVideoPlayback

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Layer 1 (Background): High-Res Photo ALWAYS rendered underneath
        ImageViewer(
            imageUrl = imageUrl,
            scalingMode = effectiveScalingMode,
            transitionEffect = userSettings.transitionEffect,
            viewModel = viewModel,
            isActivePage = isActivePage,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2 (Foreground): Lazily mount ExoPlayer + TextureView ONLY when actively playing video/motion
        if (shouldMountVideoPlayer) {
            var hasRenderedFirstFrame by remember(streamUrl) { mutableStateOf(false) }
            var hasAppliedTrackOverride by remember(streamUrl) { mutableStateOf(false) }

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
                        repeatMode = if (isManualMotionActive) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                        volume = if (isMuted) 0f else 1.0f
                        playWhenReady = true
                        prepare()
                    }
            }

            LaunchedEffect(isManualMotionActive) {
                exoPlayer.repeatMode = if (isManualMotionActive) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }

            LaunchedEffect(isMuted) {
                exoPlayer.volume = if (isMuted) 0f else 1.0f
            }

            DisposableEffect(exoPlayer) {
                val listener = object : Player.Listener {
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        if (hasAppliedTrackOverride) return
                        for (group in tracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                val trackGroup = group.mediaTrackGroup
                                for (i in 0 until trackGroup.length) {
                                    val format = trackGroup.getFormat(i)
                                    // Select motion track (width <= 1920 & height <= 1920) instead of static 2048x1536 cover track
                                    if ((format.width in 1..1920 || format.height in 1..1920) && (format.width != 2048 && format.height != 2048)) {
                                        hasAppliedTrackOverride = true
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
                                if (isManualVideoPlaying) {
                                    isManualVideoPlaying = false
                                    viewModel.setLiveMotionPlaying(false)
                                }
                                currentOnVideoCompleted()
                            }
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        hasRenderedFirstFrame = true
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            activeVideoWidth = videoSize.width
                            activeVideoHeight = videoSize.height
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("PortalPhotos", "[EXO] Error: ${error.localizedMessage}", error)
                        if (currentIsActivePage && !isManualMotionActive) {
                            if (isManualVideoPlaying) {
                                isManualVideoPlaying = false
                                viewModel.setLiveMotionPlaying(false)
                            }
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
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = if (hasRenderedFirstFrame) 1f else 0f)
                )
            }
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
                        val nextState = !isManualMotionActive
                        isManualMotionActive = nextState
                        viewModel.setLiveMotionPlaying(nextState)
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

        // Standalone Video Manual Play Toggle Pill (Top-Left) when Autoplay is OFF
        if (!isLivePhotoItem && !userSettings.autoplayVideos) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .zIndex(10f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isManualVideoPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else Color.Black.copy(alpha = 0.6f)
                    )
                    .clickable {
                        isManualVideoPlaying = !isManualVideoPlaying
                        viewModel.setLiveMotionPlaying(isManualVideoPlaying)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isManualVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isManualVideoPlaying) "PLAYING" else "PLAY VIDEO",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
