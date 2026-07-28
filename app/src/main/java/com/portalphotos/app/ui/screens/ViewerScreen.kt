package com.portalphotos.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portalphotos.app.data.metadata.MediaMetadataExtractor
import com.portalphotos.app.data.model.MediaItem
import com.portalphotos.app.data.prefs.LivePhotoBehavior
import com.portalphotos.app.ui.components.ClockOverlay
import com.portalphotos.app.ui.components.ImageViewer
import com.portalphotos.app.ui.components.MetadataSheet
import com.portalphotos.app.ui.components.SettingsSheet
import com.portalphotos.app.ui.components.VideoPlayer
import com.portalphotos.app.ui.viewmodel.ViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mediaItems by viewModel.displayMediaItems.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val localWebUrl by viewModel.localWebUrl.collectAsState()
    val isAudioMuted by viewModel.isAudioMuted.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var showControlsOverlay by remember { mutableStateOf(true) }

    // Sleep Mode State Management
    var currentHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var isTemporarilyWoken by remember { mutableStateOf(false) }

    // Update current hour every 60 seconds
    LaunchedEffect(Unit) {
        while (true) {
            currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(60_000L)
        }
    }

    val isSleepHourActive = remember(currentHour, userSettings.sleepStartHour, userSettings.sleepEndHour) {
        val start = userSettings.sleepStartHour
        val end = userSettings.sleepEndHour
        if (start > end) {
            currentHour >= start || currentHour < end
        } else if (start < end) {
            currentHour in start until end
        } else {
            false
        }
    }

    val isSleepingNow = userSettings.sleepScheduleEnabled && isSleepHourActive && !isTemporarilyWoken

    // Auto-re-enter sleep mode after 30 seconds of temp wake
    LaunchedEffect(isTemporarilyWoken) {
        if (isTemporarilyWoken) {
            delay(30_000L)
            isTemporarilyWoken = false
        }
    }

    // Safe infinite looping pager setup within Float precision limits
    val virtualPageCount = remember(mediaItems) {
        if (mediaItems.size > 1) mediaItems.size * 1000 else 1
    }
    val initialPage = remember(mediaItems) {
        if (mediaItems.size > 1) mediaItems.size * 500 else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { virtualPageCount }
    )

    // Compute Metadata for currently active media item safely
    val activeItem = remember(pagerState.settledPage, mediaItems) {
        if (mediaItems.isNotEmpty()) {
            val actualIndex = pagerState.settledPage % mediaItems.size
            mediaItems.getOrNull(actualIndex)
        } else null
    }

    val activeMetadata = remember(activeItem) {
        val item = activeItem
        if (item != null) {
            val cachedFile: File? = try {
                context.cacheDir.resolve("image_cache").listFiles()?.firstOrNull()
            } catch (e: Exception) {
                null
            }

            val (w, h) = when (item) {
                is MediaItem.Photo -> Pair(item.width, item.height)
                is MediaItem.Video -> Pair(item.width, item.height)
            }

            MediaMetadataExtractor.extractMetadata(
                context = context,
                imageFile = cachedFile,
                timestampMs = item.timestamp,
                albumTitle = item.albumTitle,
                width = w,
                height = h
            )
        } else null
    }

    // Show Toast messages from ViewModel
    LaunchedEffect(uiMessage) {
        val msg = uiMessage
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUiMessage()
        }
    }

    // Trigger lookahead pre-buffering whenever settled page changes
    LaunchedEffect(pagerState.settledPage, mediaItems) {
        if (mediaItems.isNotEmpty()) {
            val actualIndex = pagerState.settledPage % mediaItems.size
            viewModel.preloadWindow(actualIndex)
        }
    }

    val isLiveMotionPlaying by viewModel.isLiveMotionPlaying.collectAsState()

    // Auto-advance slideshow timer loop for photos and still Live Photos when not sleeping and motion is not playing
    LaunchedEffect(isPlaying, isSleepingNow, isLiveMotionPlaying, pagerState.settledPage, mediaItems, userSettings.slideshowTimerSeconds, userSettings.livePhotoBehavior) {
        if (isPlaying && !isSleepingNow && !isLiveMotionPlaying && mediaItems.isNotEmpty() && userSettings.slideshowTimerSeconds > 0) {
            val actualIndex = pagerState.settledPage % mediaItems.size
            val currentItem = mediaItems.getOrNull(actualIndex)
            val isStillOrLivePhotoToggle = currentItem is MediaItem.Photo ||
                (currentItem is MediaItem.Video && currentItem.isLivePhoto && userSettings.livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE)

            if (isStillOrLivePhotoToggle) {
                delay(userSettings.slideshowTimerSeconds * 1000L)
                if (isPlaying && !isSleepingNow && !isLiveMotionPlaying && mediaItems.isNotEmpty()) {
                    val nextPage = pagerState.settledPage + 1
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    // Auto-hide controls overlay after 5 seconds of inactivity
    LaunchedEffect(showControlsOverlay, isPlaying) {
        if (showControlsOverlay && isPlaying) {
            delay(5000L)
            showControlsOverlay = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isSleepingNow) {
                    isTemporarilyWoken = true
                } else {
                    showControlsOverlay = !showControlsOverlay
                }
            }
    ) {
        if (mediaItems.isEmpty()) {
            // Empty state view when no albums or items added yet
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Welcome to Portal Photos",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    if (!localWebUrl.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        text = "Add albums from any phone or PC:",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Go to $localWebUrl",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Or tap Settings below to paste a Google Photos shared album URL directly",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )

                    Button(
                        onClick = { showSettingsSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Settings")
                    }
                }
            }
        } else {
            // Infinite Looping Horizontal Pager Media Viewer
            HorizontalPager(
                state = pagerState,
                key = { page ->
                    if (mediaItems.isNotEmpty()) {
                        val actualIndex = page % mediaItems.size
                        "${mediaItems[actualIndex].id}_$page"
                    } else {
                        "empty_$page"
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val actualIndex = if (mediaItems.isNotEmpty()) page % mediaItems.size else 0
                val item = mediaItems.getOrNull(actualIndex)
                if (item != null) {
                    val isActivePage = (page == pagerState.settledPage)

                    when (item) {
                        is MediaItem.Photo -> {
                            ImageViewer(
                                imageUrl = item.displayUrl,
                                scalingMode = userSettings.scalingMode,
                                transitionEffect = userSettings.transitionEffect,
                                viewModel = viewModel
                            )
                        }
                        is MediaItem.Video -> {
                            VideoPlayer(
                                imageUrl = item.displayUrl,
                                streamUrl = item.streamUrl,
                                isLivePhotoItem = item.isLivePhoto,
                                isActivePage = isActivePage && !isSleepingNow,
                                autoplay = userSettings.autoplayVideos,
                                isMuted = isAudioMuted,
                                livePhotoBehavior = userSettings.livePhotoBehavior,
                                onToggleMute = { viewModel.toggleAudioMute() },
                                onVideoCompleted = {
                                    scope.launch {
                                        if (mediaItems.isNotEmpty() && !isSleepingNow) {
                                            val nextPage = pagerState.settledPage + 1
                                            pagerState.animateScrollToPage(
                                                page = nextPage,
                                                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    }
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }

            // Ambient Clock Overlay
            if (!isSleepingNow) {
                ClockOverlay(
                    mode = userSettings.clockOverlayMode,
                    pixelShiftProtection = userSettings.pixelShiftProtection
                )
            }

            // Ambient 1-Line HUD Caption (Bottom Left)
            if (userSettings.showAmbientCaption && activeMetadata != null && !isSleepingNow) {
                val cleanAlbumTitle = remember(activeMetadata?.albumTitle) {
                    activeMetadata?.albumTitle
                        ?.replace(Regex("""\s*\([A-Za-z0-9\s,-]+\)"""), "")
                        ?.trim() ?: ""
                }
                val ambientText = remember(activeMetadata?.formattedDate, activeMetadata?.locationName, cleanAlbumTitle) {
                    buildString {
                        append(activeMetadata?.formattedDate ?: "")
                        if (!activeMetadata?.locationName.isNullOrBlank()) {
                            append(" • ${activeMetadata?.locationName}")
                        } else if (cleanAlbumTitle.isNotBlank()) {
                            append(" • $cleanAlbumTitle")
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 28.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = ambientText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Top Progress Bar for Active Slideshow Playback Countdown
            val currentItem = mediaItems.getOrNull(pagerState.settledPage % mediaItems.size)
            val isStillSlide = currentItem is MediaItem.Photo || (currentItem is MediaItem.Video && currentItem.isLivePhoto)

            if (mediaItems.size > 1 && userSettings.showProgressBar && isStillSlide && !isSleepingNow) {
                val progressAnimation = remember { Animatable(0f) }

                LaunchedEffect(pagerState.settledPage) {
                    progressAnimation.snapTo(0f)
                }

                LaunchedEffect(pagerState.settledPage, isPlaying, userSettings.slideshowTimerSeconds) {
                    if (isPlaying && userSettings.slideshowTimerSeconds > 0) {
                        val remainingRatio = (1f - progressAnimation.value).coerceIn(0f, 1f)
                        val remainingMillis = (userSettings.slideshowTimerSeconds * 1000 * remainingRatio).toInt()
                        if (remainingMillis > 0) {
                            progressAnimation.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = remainingMillis,
                                    easing = LinearEasing
                                )
                            )
                        }
                    } else {
                        progressAnimation.stop()
                    }
                }

                LinearProgressIndicator(
                    progress = { progressAnimation.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }

        // Night Sleep Mode Full Black Overlay
        if (isSleepingNow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { isTemporarilyWoken = true },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep Mode Active",
                        tint = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Night Sleep Schedule",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Touch screen anywhere to wake",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Overlay Controls (Fades in on screen tap when not sleeping)
        AnimatedVisibility(
            visible = (showControlsOverlay || mediaItems.isEmpty()) && !isSleepingNow,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Center Action Bar: Local Web Server Prompt ONLY
                if (!localWebUrl.isNullOrEmpty() && mediaItems.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Add albums at $localWebUrl",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Consolidated Glassmorphic Bottom Control Bar: Prev / Play-Pause / Audio / Info / Settings / Next
                if (mediaItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Previous Photo Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val prevPage = pagerState.settledPage - 1
                                    pagerState.animateScrollToPage(
                                        page = prevPage,
                                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Photo",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 2. Play / Pause Button
                        IconButton(
                            onClick = { viewModel.togglePlayPause() }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // 3. Audio Mute / Unmute Button
                        IconButton(
                            onClick = { viewModel.toggleAudioMute() }
                        ) {
                            Icon(
                                imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isAudioMuted) "Unmute" else "Mute",
                                tint = if (isAudioMuted) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 4. Metadata Info Button (ℹ️)
                        IconButton(
                            onClick = { showMetadataSheet = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Photo Information",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 5. Settings Gear Button
                        IconButton(
                            onClick = { showSettingsSheet = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 6. Next Photo Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val nextPage = pagerState.settledPage + 1
                                    pagerState.animateScrollToPage(
                                        page = nextPage,
                                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Photo",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        // Photo Metadata Information Sheet Modal (ℹ️)
        if (showMetadataSheet && activeMetadata != null) {
            MetadataSheet(
                metadata = activeMetadata,
                onDismiss = { showMetadataSheet = false }
            )
        }

        // Settings Modal Sheet
        if (showSettingsSheet) {
            SettingsSheet(
                viewModel = viewModel,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}
