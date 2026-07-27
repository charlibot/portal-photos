package com.portalphotos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portalphotos.app.data.metadata.FormattedMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSheet(
    metadata: FormattedMetadata,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Photo Info",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("Close", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 1. Date & Relative Age Card
            MetadataRowCard(
                icon = Icons.Default.CalendarToday,
                title = metadata.formattedDate,
                subtitle = metadata.relativeAge,
                highlight = true
            )

            // 2. Location (if present)
            if (!metadata.locationName.isNullOrBlank()) {
                MetadataRowCard(
                    icon = Icons.Default.LocationOn,
                    title = metadata.locationName,
                    subtitle = "Location"
                )
            }

            // 3. Camera Specs (if present)
            if (!metadata.cameraModel.isNullOrBlank()) {
                MetadataRowCard(
                    icon = Icons.Default.CameraAlt,
                    title = metadata.cameraModel,
                    subtitle = metadata.cameraSettings ?: "Camera Shot Details"
                )
            }

            // 4. Resolution & Megapixels
            if (!metadata.resolutionString.isNullOrBlank()) {
                MetadataRowCard(
                    icon = Icons.Default.PhotoSizeSelectActual,
                    title = metadata.resolutionString,
                    subtitle = "Image Resolution"
                )
            }

            // 5. Shared Album Origin
            if (metadata.albumTitle.isNotBlank()) {
                MetadataRowCard(
                    icon = Icons.Default.Folder,
                    title = metadata.albumTitle,
                    subtitle = "Shared Album"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetadataRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlight: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
