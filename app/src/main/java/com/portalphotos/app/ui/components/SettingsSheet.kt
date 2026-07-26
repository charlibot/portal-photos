package com.portalphotos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Local Web Server Information Banner
            if (!localWebUrl.isNullOrEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Manage Albums from any Phone / PC:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = localWebUrl ?: "",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Add Shared Album URL
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Add Google Photos Shared Album",
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
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (urlInput.isNotBlank()) {
                                        viewModel.addAlbumUrl(urlInput.trim())
                                        urlInput = ""
                                    }
                                },
                                enabled = urlInput.isNotBlank()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                            }
                        }
                    }
                }
            }

            // Section 2: Manage Added Albums
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

            // Section 3: Sleep Schedule & Night Hours (Default 23:00 to 08:00)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Night Sleep Schedule",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                            Switch(
                                checked = userSettings.sleepScheduleEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateSleepScheduleEnabled(isChecked)
                                }
                            )
                        }

                        if (userSettings.sleepScheduleEnabled) {
                            Text(
                                text = "Screen turns pitch black during sleeping hours to emit zero glare. Touch screen anytime to wake.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Sleep Starts", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        text = String.format("%02d:00 (%s)", userSettings.sleepStartHour, if (userSettings.sleepStartHour >= 12) "${if (userSettings.sleepStartHour > 12) userSettings.sleepStartHour - 12 else 12} PM" else "${if (userSettings.sleepStartHour == 0) 12 else userSettings.sleepStartHour} AM"),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = userSettings.sleepStartHour == 22,
                                        onClick = { viewModel.updateSleepStartHour(22) },
                                        label = { Text("10 PM") }
                                    )
                                    FilterChip(
                                        selected = userSettings.sleepStartHour == 23,
                                        onClick = { viewModel.updateSleepStartHour(23) },
                                        label = { Text("11 PM") }
                                    )
                                    FilterChip(
                                        selected = userSettings.sleepStartHour == 0,
                                        onClick = { viewModel.updateSleepStartHour(0) },
                                        label = { Text("12 AM") }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Sleep Ends", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        text = String.format("%02d:00 (%s)", userSettings.sleepEndHour, if (userSettings.sleepEndHour >= 12) "${if (userSettings.sleepEndHour > 12) userSettings.sleepEndHour - 12 else 12} PM" else "${if (userSettings.sleepEndHour == 0) 12 else userSettings.sleepEndHour} AM"),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = userSettings.sleepEndHour == 6,
                                        onClick = { viewModel.updateSleepEndHour(6) },
                                        label = { Text("6 AM") }
                                    )
                                    FilterChip(
                                        selected = userSettings.sleepEndHour == 7,
                                        onClick = { viewModel.updateSleepEndHour(7) },
                                        label = { Text("7 AM") }
                                    )
                                    FilterChip(
                                        selected = userSettings.sleepEndHour == 8,
                                        onClick = { viewModel.updateSleepEndHour(8) },
                                        label = { Text("8 AM") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Clock & Date Overlay Settings
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
                                        viewModel.appPreferences.updateClockOverlay(ClockOverlayMode.HIDDEN)
                                    }
                                },
                                label = { Text("Disabled") }
                            )
                            FilterChip(
                                selected = userSettings.clockOverlayMode == ClockOverlayMode.TOP_RIGHT,
                                onClick = {
                                    scope.launch {
                                        viewModel.appPreferences.updateClockOverlay(ClockOverlayMode.TOP_RIGHT)
                                    }
                                },
                                label = { Text("Top Right") }
                            )
                            FilterChip(
                                selected = userSettings.clockOverlayMode == ClockOverlayMode.BOTTOM_LEFT,
                                onClick = {
                                    scope.launch {
                                        viewModel.appPreferences.updateClockOverlay(ClockOverlayMode.BOTTOM_LEFT)
                                    }
                                },
                                label = { Text("Bottom Left") }
                            )
                        }
                    }
                }
            }

            // Section 5: Image Scaling & Smart Crop Mode
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Photo Alignment & Smart Crop",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = userSettings.scalingMode == ScalingMode.FILL_SMART_CROP,
                                onClick = {
                                    scope.launch {
                                        viewModel.appPreferences.updateScalingMode(ScalingMode.FILL_SMART_CROP)
                                    }
                                },
                                label = { Text("Smart Crop (ML Face & Object Centered)") }
                            )
                            FilterChip(
                                selected = userSettings.scalingMode == ScalingMode.FIT,
                                onClick = {
                                    scope.launch {
                                        viewModel.appPreferences.updateScalingMode(ScalingMode.FIT)
                                    }
                                },
                                label = { Text("Fit (Full Photo + Blurred Bars)") }
                            )
                            FilterChip(
                                selected = userSettings.scalingMode == ScalingMode.FILL_CENTER,
                                onClick = {
                                    scope.launch {
                                        viewModel.appPreferences.updateScalingMode(ScalingMode.FILL_CENTER)
                                    }
                                },
                                label = { Text("Fill Screen (Center Crop)") }
                            )
                        }
                    }
                }
            }

            // Section 6: Live Photos & Video Controls
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Playback & Motion Options",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Live Photo Mode:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE,
                                    onClick = {
                                        scope.launch {
                                            viewModel.appPreferences.updateLivePhotoBehavior(LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE)
                                        }
                                    },
                                    label = { Text("Still Photo + [LIVE] Toggle") }
                                )
                                FilterChip(
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.STILL_PHOTO_ONLY,
                                    onClick = {
                                        scope.launch {
                                            viewModel.appPreferences.updateLivePhotoBehavior(LivePhotoBehavior.STILL_PHOTO_ONLY)
                                        }
                                    },
                                    label = { Text("Still Only") }
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.PLAY_MOTION_ONCE,
                                    onClick = {
                                        scope.launch {
                                            viewModel.appPreferences.updateLivePhotoBehavior(LivePhotoBehavior.PLAY_MOTION_ONCE)
                                        }
                                    },
                                    label = { Text("Play Motion then Hold") }
                                )
                                FilterChip(
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.PLAY_AS_VIDEO,
                                    onClick = {
                                        scope.launch {
                                            viewModel.appPreferences.updateLivePhotoBehavior(LivePhotoBehavior.PLAY_AS_VIDEO)
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
                                        viewModel.appPreferences.updateShowProgressBar(isChecked)
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
                                        viewModel.appPreferences.updateShuffleMode(isChecked)
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
                                        viewModel.appPreferences.updateAutoplayVideos(isChecked)
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
                                        viewModel.appPreferences.updateKeepScreenAwake(isChecked)
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
