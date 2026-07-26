package com.portalphotos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portalphotos.app.data.prefs.*
import com.portalphotos.app.ui.viewmodel.ViewerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    viewModel: ViewerViewModel,
    modifier: Modifier = Modifier
) {
    val userSettings by viewModel.userSettings.collectAsState()
    val allAlbums by viewModel.allAlbums.collectAsState()
    val localWebUrl by viewModel.localWebUrl.collectAsState()
    var urlInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "Portal Photos Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Local Web Server URL Banner Prompt
            if (!localWebUrl.isNullOrEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Add albums from your phone/PC",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Go to $localWebUrl on any browser on the same Wi-Fi network",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Add Google Photos Album URL directly on Portal
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Add Google Photos Album Link",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                placeholder = { Text("Paste https://photos.app.goo.gl/...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (urlInput.isNotBlank()) {
                                        viewModel.addAlbumUrl(urlInput)
                                        urlInput = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }
                }
            }

            // Section 2: Manage Saved Albums & Multi-Selection Pool
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Album Selection (Check multiple to merge)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (allAlbums.isEmpty()) {
                            Text(
                                text = "No albums added yet. Paste a Google Photos shared link above or visit $localWebUrl!",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                allAlbums.forEach { album ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = album.isSelected,
                                            onCheckedChange = { isChecked ->
                                                viewModel.toggleAlbumSelection(album.id, isChecked)
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.title,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "${album.itemCount} items",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteAlbum(album.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Album",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Clock & Date Overlay Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Clock & Date Overlay",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = userSettings.clockOverlayMode == ClockOverlayMode.HIDDEN,
                                onClick = {
                                    scope.launch {
                                        viewModel.preferences.updateClockOverlay(ClockOverlayMode.HIDDEN)
                                    }
                                },
                                label = { Text("Disabled") }
                            )
                            FilterChip(
                                selected = userSettings.clockOverlayMode == ClockOverlayMode.TOP_RIGHT,
                                onClick = {
                                    scope.launch {
                                        viewModel.preferences.updateClockOverlay(ClockOverlayMode.TOP_RIGHT)
                                    }
                                },
                                label = { Text("Top-Right") }
                            )
                            FilterChip(
                                selected = userSettings.clockOverlayMode == ClockOverlayMode.BOTTOM_LEFT,
                                onClick = {
                                    scope.launch {
                                        viewModel.preferences.updateClockOverlay(ClockOverlayMode.BOTTOM_LEFT)
                                    }
                                },
                                label = { Text("Bottom-Left") }
                            )
                            FilterChip(
                                selected = userSettings.clockOverlayMode == ClockOverlayMode.BAR,
                                onClick = {
                                    scope.launch {
                                        viewModel.preferences.updateClockOverlay(ClockOverlayMode.BAR)
                                    }
                                },
                                label = { Text("Glass Bar") }
                            )
                        }
                    }
                }
            }

            // Section 4: Display & Smart Cropping Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Display & Smart Cropping Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = userSettings.scalingMode == ScalingMode.FILL_SMART_CROP,
                                onClick = {
                                    scope.launch {
                                        viewModel.preferences.updateScalingMode(ScalingMode.FILL_SMART_CROP)
                                    }
                                },
                                label = { Text("Fill (Smart Crop)") }
                            )
                            FilterChip(
                                selected = userSettings.scalingMode == ScalingMode.FIT,
                                onClick = {
                                    scope.launch {
                                        viewModel.preferences.updateScalingMode(ScalingMode.FIT)
                                    }
                                },
                                label = { Text("Fit (Whole Photo)") }
                            )
                            FilterChip(
                                selected = userSettings.scalingMode == ScalingMode.FILL_CENTER,
                                onClick = {
                                    scope.launch {
                                        viewModel.preferences.updateScalingMode(ScalingMode.FILL_CENTER)
                                    }
                                },
                                label = { Text("Fill (Center)") }
                            )
                        }
                    }
                }
            }

            // Section 5: Playback & Live Photos Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Playback & Slideshow Settings",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Slideshow Timer")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5, 10, 30, 60).forEach { seconds ->
                                    FilterChip(
                                        selected = userSettings.slideshowTimerSeconds == seconds,
                                        onClick = {
                                            scope.launch {
                                                viewModel.preferences.updateSlideshowTimer(seconds)
                                            }
                                        },
                                        label = { Text("${seconds}s") }
                                    )
                                }
                            }
                        }

                        Column {
                            Text("Live Photos Behavior", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE,
                                    onClick = {
                                        scope.launch {
                                            viewModel.preferences.updateLivePhotoBehavior(LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE)
                                        }
                                    },
                                    label = { Text("Still Photo + Motion Pill") }
                                )
                                FilterChip(
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.PLAY_MOTION_ONCE,
                                    onClick = {
                                        scope.launch {
                                            viewModel.preferences.updateLivePhotoBehavior(LivePhotoBehavior.PLAY_MOTION_ONCE)
                                        }
                                    },
                                    label = { Text("Play Motion then Hold") }
                                )
                                FilterChip(
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.PLAY_AS_VIDEO,
                                    onClick = {
                                        scope.launch {
                                            viewModel.preferences.updateLivePhotoBehavior(LivePhotoBehavior.PLAY_AS_VIDEO)
                                        }
                                    },
                                    label = { Text("Play on Loop") }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Top Progress Bar")
                            Switch(
                                checked = userSettings.showProgressBar,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        viewModel.preferences.updateShowProgressBar(isChecked)
                                    }
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Shuffle Playback Order")
                            Switch(
                                checked = userSettings.shuffleMode,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        viewModel.preferences.updateShuffleMode(isChecked)
                                    }
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Autoplay Videos")
                            Switch(
                                checked = userSettings.autoplayVideos,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        viewModel.preferences.updateAutoplayVideos(isChecked)
                                    }
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Keep Display Awake 24/7")
                            Switch(
                                checked = userSettings.keepScreenAwake,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        viewModel.preferences.updateKeepScreenAwake(isChecked)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
