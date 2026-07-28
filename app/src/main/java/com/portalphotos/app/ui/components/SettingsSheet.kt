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
import androidx.compose.material.icons.filled.Refresh
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Album Selection (Check multiple to merge)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            if (allAlbums.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.repository.refreshAllAlbums()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-sync All", fontSize = 13.sp)
                                }
                            }
                        }
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
                                    val syncedDate = remember(album.lastSyncedAt) {
                                        SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(album.lastSyncedAt))
                                    }
                                    val subtitleText = remember(album.itemCount, album.albumDate, syncedDate) {
                                        buildString {
                                            append("${album.itemCount} items")
                                            if (!album.albumDate.isNullOrBlank()) {
                                                append(" • ${album.albumDate}")
                                            }
                                            append(" • Last sync: $syncedDate")
                                        }
                                    }

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
                                                text = subtitleText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    viewModel.repository.refreshAlbum(album.id)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Re-sync Album",
                                                tint = MaterialTheme.colorScheme.primary
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

            // Section 3: Sleep Schedule & Night Hours (Default ON: 23:00 to 08:00)
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

                            // 24-Hour Selector for Sleep Start Hour
                            HourPickerRow(
                                label = "Sleep Starts",
                                selectedHour = userSettings.sleepStartHour,
                                onHourSelected = { hour -> viewModel.updateSleepStartHour(hour) }
                            )

                            // 24-Hour Selector for Sleep End Hour
                            HourPickerRow(
                                label = "Sleep Ends",
                                selectedHour = userSettings.sleepEndHour,
                                onHourSelected = { hour -> viewModel.updateSleepEndHour(hour) }
                            )
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

            // Section 6: Live Photos & Playback Controls
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
                                    selected = userSettings.livePhotoBehavior == LivePhotoBehavior.PLAY_MOTION_ONCE,
                                    onClick = {
                                        scope.launch {
                                            viewModel.appPreferences.updateLivePhotoBehavior(LivePhotoBehavior.PLAY_MOTION_ONCE)
                                        }
                                    },
                                    label = { Text("Play Motion then Hold") }
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
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ken Burns Effect (Pan & Zoom)")
                            Switch(
                                checked = userSettings.transitionEffect == TransitionEffect.KEN_BURNS,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        val newEffect = if (isChecked) TransitionEffect.KEN_BURNS else TransitionEffect.CROSSFADE
                                        viewModel.appPreferences.updateTransitionEffect(newEffect)
                                    }
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Ambient Date Captions")
                            Switch(
                                checked = userSettings.showAmbientCaption,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        viewModel.appPreferences.updateShowAmbientCaption(isChecked)
                                    }
                                }
                            )
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

@Composable
private fun HourPickerRow(
    label: String,
    selectedHour: Int,
    onHourSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    fun formatHour(h: Int): String {
        val amPm = if (h >= 12) "PM" else "AM"
        val displayHour = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%02d:00 (%d %s)", h, displayHour, amPm)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(formatHour(selectedHour), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                (0..23).forEach { hour ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = formatHour(hour),
                                fontWeight = if (hour == selectedHour) FontWeight.Bold else FontWeight.Normal,
                                color = if (hour == selectedHour) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                        },
                        onClick = {
                            onHourSelected(hour)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
